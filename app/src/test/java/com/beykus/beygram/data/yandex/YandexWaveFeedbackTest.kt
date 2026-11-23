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
    private fun WaveListenTracker.play(key: String, from: Long, to: Long, duration: Long = 200_000L): List<WaveListenEvent> {
        val events = mutableListOf<WaveListenEvent>()
        var position = from
        while (position <= to) {
            events += onState(state(key, position = position, duration = duration))
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
        assertEquals(WaveListenEvent.Finished("ym:1", 200.0), t.onState(state("ym:2"))[0])
    }

    @Test
    fun endedTrackCountsAsFinished() {
        val t = tracker()
        t.play("ym:1", 0L, 199_000L)
        t.onState(state("ym:1", position = 200_000L, ended = true, playing = false))
        assertEquals(WaveListenEvent.Finished("ym:1", 200.0), t.onState(state("ym:2"))[0])
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
        assertEquals("b2", session.batchOfTrack("2:a2"))
        assertEquals("2:a2", session.trackIdOf("2"))
        assertNull(session.trackOf("ym:3"))
    }
}
