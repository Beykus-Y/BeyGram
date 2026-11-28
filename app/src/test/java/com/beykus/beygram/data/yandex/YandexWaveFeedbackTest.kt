package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexWaveFeedbackTest {

    /** The wall clock of the test: the tracker sees real time pass only when the test moves it. */
    private class Clock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    private val clock = Clock()

    private fun tracker() = WaveListenTracker(clock) { it.startsWith("ym:") }

    private fun state(
        key: String?,
        playing: Boolean = true,
        position: Long = 0L,
        duration: Long = 200_000L,
        ended: Boolean = false,
        active: Boolean = true,
        failed: Boolean = false,
        manualSkips: Long = 0L,
    ) = WaveSnapshot(active, key, playing, position, duration, ended, failed, manualSkips)

    /** Plays [key] from [from] to [to] ms in one-second steps, as the player reports progress, and lets real time pass. */
    private fun WaveListenTracker.play(
        key: String,
        from: Long,
        to: Long,
        duration: Long = 200_000L,
        manualSkips: Long = 0L,
    ): List<WaveListenEvent> {
        val events = mutableListOf<WaveListenEvent>()
        var position = from
        while (position <= to) {
            events += onState(state(key, position = position, duration = duration, manualSkips = manualSkips))
            clock.now += 1_000L
            position += 1_000L
        }
        return events
    }

    @Test
    fun trackStartsOnceWhenItPlays() {
        val t = tracker()
        assertTrue(t.onState(state("ym:1", playing = false)).isEmpty())
        assertEquals(listOf(WaveListenEvent.Started("ym:1")), t.onState(state("ym:1")))
        assertTrue(t.onState(state("ym:1", position = 5_000L)).isEmpty())
    }

    @Test
    fun earlyChangeIsASkipWithTheSecondsHeard() {
        val t = tracker()
        t.play("ym:1", 0L, 42_000L)
        val events = t.onState(state("ym:2"))
        assertEquals(WaveListenEvent.Skipped("ym:1", 42.0), events[0])
        assertEquals(WaveListenEvent.Started("ym:2"), events[1])
    }

    @Test
    fun changeInTheLastSecondsAfterListeningCountsAsFinished() {
        val t = tracker()
        t.play("ym:1", 0L, 190_000L)
        // The seconds really heard, not the length of the track.
        assertEquals(WaveListenEvent.Finished("ym:1", 190.0), t.onState(state("ym:2"))[0])
    }

    @Test
    fun endedTrackCountsAsFinished() {
        val t = tracker()
        t.play("ym:1", 0L, 199_000L)
        t.onState(state("ym:1", position = 200_000L, ended = true, playing = false))
        assertEquals(WaveListenEvent.Finished("ym:1", 199.0), t.onState(state("ym:2"))[0])
    }

    @Test
    fun finishedReportsHeardSecondsNotTheTrackLengthAfterASeekToTheEnd() {
        val t = tracker()
        // 165 s of real playback (82% of a 200 s track), then a seek to the last seconds with no time passing.
        t.play("ym:1", 0L, 164_000L)
        t.onState(state("ym:1", position = 165_000L))
        t.onState(state("ym:1", position = 199_000L))
        assertEquals(WaveListenEvent.Finished("ym:1", 165.0), t.onState(state("ym:2"))[0])
    }

    @Test
    fun seekToTheEndIsNotListening() {
        val t = tracker()
        t.onState(state("ym:1"))
        clock.now += 1_000L
        // The position jumps to the last seconds while only one second of real time passed.
        t.onState(state("ym:1", position = 199_000L))
        assertEquals(WaveListenEvent.Skipped("ym:1", 2.5), t.onState(state("ym:2"))[0])
    }

    @Test
    fun seekToTheEndWithEndedFlagIsNotFinished() {
        val t = tracker()
        t.onState(state("ym:1"))
        clock.now += 1_000L
        t.onState(state("ym:1", position = 200_000L, ended = true))
        assertEquals(WaveListenEvent.Skipped("ym:1", 2.5), t.onState(state("ym:2"))[0])
    }

    @Test
    fun manualNextIsASkipEvenNearTheEnd() {
        val t = tracker()
        t.play("ym:1", 0L, 195_000L)
        t.onState(state("ym:1", position = 196_000L, manualSkips = 1L))
        val events = t.onState(state("ym:2", manualSkips = 1L))
        assertEquals(WaveListenEvent.Skipped("ym:1", 196.0), events[0])
        assertEquals(WaveListenEvent.Started("ym:2"), events[1])
    }

    @Test
    fun restartingTheSameTrackIsNotASkip() {
        val t = tracker()
        t.play("ym:1", 0L, 100_000L)
        // Repeat One: the same track starts again from zero after a manual Next.
        t.onState(state("ym:1", position = 0L, manualSkips = 1L))
        clock.now += 1_000L
        // The counter only grows: the replay carries the count the restart already reached.
        t.play("ym:1", 1_000L, 199_000L, manualSkips = 1L)
        // Both plays count as heard (100 s + 199 s), capped at the track's length.
        assertEquals(WaveListenEvent.Finished("ym:1", 200.0), t.onState(state("ym:2", manualSkips = 1L))[0])
    }

    @Test
    fun trackThePlayerFailedOnSendsNothing() {
        val t = tracker()
        t.play("ym:1", 0L, 5_000L)
        t.onState(state("ym:1", position = 6_000L, failed = true))
        assertEquals(listOf(WaveListenEvent.Started("ym:2")), t.onState(state("ym:2")))
    }

    @Test
    fun pausedTimeIsNotListening() {
        val t = tracker()
        t.play("ym:1", 0L, 10_000L)
        // Paused for a minute: the position stays, the clock moves on.
        clock.now += 60_000L
        t.onState(state("ym:1", position = 10_000L, playing = false))
        clock.now += 1_000L
        t.onState(state("ym:1", position = 10_000L, playing = false))
        assertEquals(WaveListenEvent.Skipped("ym:1", 10.0), t.onState(state("ym:2"))[0])
    }

    @Test
    fun trackThatNeverPlayedSendsNothing() {
        val t = tracker()
        t.onState(state("ym:1", playing = false))
        assertEquals(listOf(WaveListenEvent.Started("ym:2")), t.onState(state("ym:2")))
    }

    @Test
    fun leavingTheRadioClosesTheLastTrack() {
        val t = tracker()
        t.play("ym:1", 0L, 30_000L)
        assertEquals(listOf(WaveListenEvent.Skipped("ym:1", 30.0)), t.onState(state("tg:9", active = false)))
        assertTrue(t.closed)
        assertTrue(t.onState(state("ym:3")).isEmpty())
    }

    @Test
    fun inactiveStateBeforeTheRadioStartsIsIgnored() {
        val t = tracker()
        assertTrue(t.onState(state("tg:9", active = false)).isEmpty())
        assertFalse(t.closed)
        assertEquals(listOf(WaveListenEvent.Started("ym:1")), t.onState(state("ym:1")))
    }

    @Test
    fun foreignTrackInTheQueueIsNotReported() {
        val t = tracker()
        assertTrue(t.onState(state("tg:9")).isEmpty())
    }

    @Test
    fun eventJsonHasTheServiceFieldNames() {
        val json = YandexWaveEvent(YandexWaveEvent.SKIP, 0L, "5:9", 12.5).toJson()
        assertEquals("skip", json.getString("type"))
        assertEquals("1970-01-01T00:00:00.000Z", json.getString("timestamp"))
        assertEquals("5:9", json.getString("trackId"))
        assertEquals(12.5, json.getDouble("totalPlayedSeconds"), 0.0)
        assertFalse(YandexWaveEvent(YandexWaveEvent.RADIO_STARTED, 0L).toJson().has("trackId"))
    }

    @Test
    fun sessionKnowsBatchAndAlbumOfItsTracks() = runTest {
        fun track(id: String) = ProviderTrack(id, "t", emptyList(), null, 1L, null, true, albumId = "a$id")
        val session = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> ProviderResult.Success(YandexWaveBatch("s", "b1", listOf(track("1")), false, false)) },
            next = { _, _ -> ProviderResult.Success(YandexWaveBatch("s", "b2", listOf(track("2")), false, false)) },
        )
        session.first()
        session.more()
        assertEquals("s", session.currentSessionId)
        assertEquals("1:a1" to "b1", session.trackOf("ym:1"))
        assertEquals("2:a2" to "b2", session.trackOf("ym:2"))
        assertEquals("2:a2", session.originOfTrackId("2")?.queueId)
        assertNull(session.trackOf("ym:3"))
    }

    @Test
    fun feedbackKeepsTheSessionAndBatchThatGaveTheTrackAfterASwap() = runTest {
        fun track(id: String) = ProviderTrack(id, "t", emptyList(), null, 1L, null, true, albumId = "a$id")
        val first = YandexWaveSession(
            seeds = listOf(YandexWave.MY_WAVE_SEED),
            start = { _, _ -> ProviderResult.Success(YandexWaveBatch("s1", "b1", listOf(track("1")), false, false)) },
            next = { _, _ -> ProviderResult.Success(YandexWaveBatch("s1", "b1", emptyList(), false, false)) },
        )
        first.first()
        val router = YandexWaveRouter(first) { seeds, given, origins ->
            YandexWaveSession(
                seeds = seeds,
                start = { _, _ -> ProviderResult.Success(YandexWaveBatch("s2", "b2", listOf(track("2")), false, false)) },
                next = { _, _ -> ProviderResult.Success(YandexWaveBatch("s2", "b2", emptyList(), false, false)) },
                initialGiven = given,
                initialOrigins = origins,
            )
        }
        router.swap(listOf("settingDiversity:discover"))
        // The settings change moved the radio to s2; the track s1 gave is still reported to s1 with its batch.
        val heard = waveOutgoing(router.current, WaveListenEvent.Finished("ym:1", 12.0), 5L)!!
        assertEquals("s1", heard.sessionId)
        assertEquals("b1", heard.batchId)
        assertEquals("1:a1", heard.event.trackId)
        assertEquals(YandexWaveEvent.TRACK_FINISHED, heard.event.type)
        assertEquals(12.0, heard.event.playedSeconds!!, 0.0)
        // The session that serves the queue now gets the events of its own tracks.
        val fresh = waveOutgoing(router.current, WaveListenEvent.Started("ym:2"), 6L)!!
        assertEquals("s2", fresh.sessionId)
        assertEquals("b2", fresh.batchId)
        assertEquals(YandexWaveEvent.TRACK_STARTED, fresh.event.type)
    }

    @Test
    fun everySessionIsAnnouncedOnceAndRequestsKeepTheirTarget() {
        val plan = WaveFeedbackPlan { 7L }
        val skip = YandexWaveEvent(YandexWaveEvent.SKIP, 1L, "1:a1", 3.0)
        val oldTrack = WaveOutgoing("s1", "b1", skip)
        // The radio already moved to s2, but the event of the track s1 gave is not re-addressed.
        val first = plan.requests(oldTrack, currentSessionId = "s2")
        assertEquals(listOf(YandexWaveEvent.RADIO_STARTED, YandexWaveEvent.SKIP), first.map { it.event.type })
        assertEquals(listOf("s1", "s1"), first.map { it.sessionId })
        assertEquals(listOf(null, "b1"), first.map { it.batchId })
        assertEquals(7L, first.first().event.timestampMs)
        // Its session is announced now: another event of the same session goes alone.
        assertEquals(listOf(YandexWaveEvent.SKIP), plan.requests(oldTrack, "s9").map { it.event.type })
        // The session now serving the radio is announced in turn.
        val newTrack = WaveOutgoing("s2", "b2", skip)
        assertEquals(
            listOf(YandexWaveEvent.RADIO_STARTED, YandexWaveEvent.SKIP),
            plan.requests(newTrack, currentSessionId = null).map { it.event.type },
        )
    }

    @Test
    fun restoredTrackUsesTheCurrentSessionAndIsDroppedWithoutOne() {
        val plan = WaveFeedbackPlan { 0L }
        val started = WaveOutgoing(null, null, YandexWaveEvent(YandexWaveEvent.TRACK_STARTED, 1L, "1:a1"))
        assertEquals(listOf("s9", "s9"), plan.requests(started, "s9").map { it.sessionId })
        assertTrue(plan.requests(started, null).isEmpty())
    }

    @Test
    fun lateRadioStartedForAnAnnouncedSessionIsDropped() {
        val plan = WaveFeedbackPlan { 0L }
        val started = YandexWaveEvent(YandexWaveEvent.RADIO_STARTED, 1L)
        assertEquals(1, plan.requests(WaveOutgoing("s1", null, started), null).size)
        assertTrue(plan.requests(WaveOutgoing("s1", null, started), null).isEmpty())
        // A queued start of an old session is not sent to the one that replaced it.
        assertTrue(plan.requests(WaveOutgoing("s1", null, started), "s2").isEmpty())
    }
}
