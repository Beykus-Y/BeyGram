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

    private fun batch(vararg ids: String, session: String = "s1", unknown: Boolean = false) = ProviderResult.Success(
        YandexWaveBatch(session, "b", ids.map { track(it) }, unknownSession = unknown, terminated = false)
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
}
