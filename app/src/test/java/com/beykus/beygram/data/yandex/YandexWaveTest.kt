package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YandexWaveTest {

    private val api = YandexApi(YandexHttp())

    private fun track(id: String, album: String? = "a$id") = ProviderTrack(
        trackId = id, title = "t$id", artists = emptyList(), albumTitle = null, durationMs = 1_000L,
        coverUrl = null, isAvailable = true, albumId = album,
    )

    private fun batch(
        vararg ids: String,
        session: String = "s1",
        batchId: String = "b",
        unknown: Boolean = false,
        terminated: Boolean = false,
    ) = ProviderResult.Success(
        YandexWaveBatch(session, batchId, ids.map { track(it) }, unknownSession = unknown, terminated = terminated)
    )

    @Test
    fun sessionAnswerIsParsed() {
        val root = JSONObject(
            """{"result":{"radioSessionId":"abc","batchId":"b1","sequence":[
                {"type":"track","liked":true,"track":{"id":10,"title":"One","albums":[{"id":7,"title":"Al"}]}},
                {"type":"track","track":{"id":"11","title":"Two"}},
                {"type":"track","track":{"id":"12"}}
            ]}}"""
        )
        val parsed = api.parseWaveBatch(root, sessionId = null)!!
        assertEquals("abc", parsed.sessionId)
        assertEquals("b1", parsed.batchId)
        assertEquals(listOf("10", "11"), parsed.tracks.map { it.trackId })
        assertEquals(true, parsed.tracks[0].liked)
        assertNull(parsed.tracks[1].liked)
        assertEquals("7", parsed.tracks[0].albumId)
        assertFalse(parsed.unknownSession)
    }

    @Test
    fun nextBatchKeepsTheKnownSessionId() {
        val root = JSONObject("""{"result":{"batchId":"b2","sequence":[],"unknownSession":true}}""")
        val parsed = api.parseWaveBatch(root, sessionId = "abc")!!
        assertEquals("abc", parsed.sessionId)
        assertTrue(parsed.unknownSession)
    }

    @Test
    fun sessionWithoutIdIsRejected() {
        assertNull(api.parseWaveBatch(JSONObject("""{"result":{"sequence":[]}}"""), sessionId = null))
    }

    @Test
    fun queueIdCarriesTheAlbum() {
        assertEquals("5:9", YandexWaveSession.queueId(track("5", "9")))
        assertEquals("5", YandexWaveSession.queueId(track("5", null)))
    }

    @Test
    fun moreSendsGivenTracksAndDropsRepeats() = runTest {
        val sent = mutableListOf<List<String>>()
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> batch("1", "2") },
            next = { _, queue -> sent += queue; batch("2", "3") },
        )
        assertEquals(listOf("1", "2"), (session.first() as ProviderResult.Success).value.map { it.trackId })
        assertEquals(listOf("3"), session.more().map { it.trackId })
        assertEquals(listOf("1:a1", "2:a2"), sent.first())
    }

    @Test
    fun forgottenSessionIsReplacedWithTheHeardQueue() = runTest {
        val starts = mutableListOf<List<String>>()
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, queue ->
                starts += queue
                if (starts.size == 1) batch("1") else batch("4", session = "s2")
            },
            next = { _, _ -> batch(unknown = true) },
        )
        session.first()
        assertEquals(listOf("4"), session.more().map { it.trackId })
        assertEquals(listOf("1:a1"), starts[1])
    }

    @Test
    fun failedBatchThrowsSoThePlayerRetries() = runTest {
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> batch("1") },
            next = { _, _ -> ProviderResult.Failure(MusicProviderFailure.Network) },
        )
        session.first()
        try {
            session.more()
            fail("expected a failure")
        } catch (e: YandexWaveException) {
            assertEquals(MusicProviderFailure.Network, e.failure)
        }
    }

    @Test
    fun emptyBatchesEndWithAnEmptyPage() = runTest {
        var calls = 0
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> batch("1") },
            next = { _, _ -> calls++; batch("1") },
        )
        session.first()
        assertTrue(session.more().isEmpty())
        assertEquals(YandexWave.MAX_EMPTY_BATCHES, calls)
    }

    @Test
    fun moreCapsQueueHintToLatestWindowWhileKeepingFullDedup() = runTest {
        val sentQueues = mutableListOf<List<String>>()
        val initialTracks = (1..132).map { track(it.toString()) }
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ ->
                ProviderResult.Success(YandexWaveBatch("s1", "b1", initialTracks, unknownSession = false, terminated = false))
            },
            next = { _, queue ->
                sentQueues += queue
                // Returns an old track (#1) and a brand-new track (#133); #1 must still be deduplicated.
                batch("1", "133")
            },
        )
        assertEquals(132, (session.first() as ProviderResult.Success).value.size)
        val fresh = session.more()
        assertEquals(listOf("133"), fresh.map { it.trackId })
        assertEquals(1, sentQueues.size)
        assertEquals(YandexWave.QUEUE_HINT, sentQueues.first().size)
        assertEquals("83:a83", sentQueues.first().first())
        assertEquals("132:a132", sentQueues.first().last())
    }

    @Test
    fun exhaustedSessionResetsAndRecoversWithFreshStartOnNextMore() = runTest {
        var starts = 0
        var nextCalls = 0
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ ->
                starts++
                if (starts == 1) batch("1", session = "s1") else batch("2", session = "s2")
            },
            next = { _, _ ->
                nextCalls++
                batch("1")
            },
        )
        session.first()
        assertTrue(session.more().isEmpty())
        assertEquals(YandexWave.MAX_EMPTY_BATCHES, nextCalls)
        // Next recovery attempt must open a fresh session via start() instead of polling the exhausted s1.
        assertEquals(listOf("2"), session.more().map { it.trackId })
        assertEquals(2, starts)
        assertEquals("s2", session.currentSessionId)
    }

    @Test
    fun networkFailureKeepsTheLiveSessionForFeedback() = runTest {
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> batch("1", session = "s1", batchId = "b1") },
            next = { _, _ -> ProviderResult.Failure(MusicProviderFailure.Network) },
        )
        session.first()
        try {
            session.more()
            fail("expected a failure")
        } catch (e: YandexWaveException) {
            assertEquals(MusicProviderFailure.Network, e.failure)
        }
        // A failed request does not end the session: the queued feedback of its tracks stays addressable.
        assertEquals("s1", session.currentSessionId)
        assertEquals(WaveTrackOrigin("1:a1", "s1", "b1"), session.originOf("ym:1"))
    }

    @Test
    fun emptyBatchesKeepTheLiveSessionAsTheFeedbackTarget() = runTest {
        var nextCalls = 0
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> batch("1", session = "s1") },
            next = { _, _ -> nextCalls++; batch(session = "s1") },
        )
        session.first()
        assertTrue(session.more().isEmpty())
        assertEquals(YandexWave.MAX_EMPTY_BATCHES, nextCalls)
        // The session still exists, only the next request will open a fresh one; feedback may still go to it meanwhile.
        assertEquals("s1", session.currentSessionId)
        assertEquals("s1", session.originOf("ym:1")?.sessionId)
    }

    @Test
    fun forgottenSessionStopsTakingFeedbackButItsTracksStillPlay() = runTest {
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> batch("1", session = "s1") },
            next = { _, _ -> batch("4", session = "s1", batchId = "b2", unknown = true) },
        )
        session.first()
        assertEquals("s1", session.currentSessionId)
        assertEquals(listOf("4"), session.more().map { it.trackId })
        // The service does not know the session: neither its last batch nor a later like may be reported to it.
        assertNull(session.currentSessionId)
        assertEquals(WaveTrackOrigin("4:a4", null, null), session.originOf("ym:4"))
        // The track is still known to the radio, so it is played and not taken for a foreign one.
        assertEquals("4:a4" to null, session.trackOf("ym:4"))
        assertEquals("4:a4", session.originOfTrackId("4")?.queueId)
    }

    @Test
    fun terminatedSessionIsNeverTheFeedbackTargetAfterRecovery() = runTest {
        var starts = 0
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ ->
                starts++
                if (starts == 1) batch("1", session = "s1") else batch(session = "s3")
            },
            next = { _, _ -> batch(session = "s1", batchId = "b2", terminated = true) },
        )
        session.first()
        assertTrue(session.more().isEmpty())
        // The recovery opened a fresh session; the ended s1 is no longer the feedback target.
        assertEquals("s3", session.currentSessionId)
        assertEquals(2, starts)
    }

    @Test
    fun terminatedFirstBatchLeavesNoFeedbackTargetAndOpensANewSessionLater() = runTest {
        var starts = 0
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ ->
                starts++
                if (starts == 1) batch("1", session = "s1", terminated = true) else batch("2", session = "s2")
            },
            next = { _, _ -> batch() },
        )
        assertEquals(listOf("1"), (session.first() as ProviderResult.Success).value.map { it.trackId })
        assertNull(session.currentSessionId)
        assertEquals(listOf("2"), session.more().map { it.trackId })
        assertEquals("s2", session.currentSessionId)
    }

    @Test
    fun restoredTrackHasNoSessionToReportTo() = runTest {
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> batch("2") },
            next = { _, _ -> batch() },
            initialGiven = listOf("1:a1"),
        )
        session.seed(track("1"))
        assertEquals("1:a1" to null, session.trackOf("ym:1"))
        assertNull(session.originOf("ym:1")?.sessionId)
    }
}
