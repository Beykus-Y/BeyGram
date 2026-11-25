package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackMetricsTest {
    private class FakeClock(var nowMs: Long = 1_000L) {
        fun advance(ms: Long) { nowMs += ms }
    }

    private val BUFFERING = 2
    private val READY = 3

    private fun metrics(clock: FakeClock) = VideoPlaybackMetrics(now = { clock.nowMs }, heapUsedKb = { 4_096L })

    private fun summary(m: VideoPlaybackMetrics) = m.summary(VideoStartBuffer.DEFAULT, 1L, streaming = true)

    // --- TTFF ---

    @Test fun ttffIsMeasuredFromPlayRequestToPlayingAndOnlyOnce() {
        val clock = FakeClock()
        val m = metrics(clock)
        clock.advance(500) // страница создана заранее, play нажат позже
        m.onPlayRequested()
        clock.advance(320)
        m.onPlayingChanged(true)
        clock.advance(900)
        m.onPlayingChanged(false)
        m.onPlayingChanged(true)
        assertEquals("320", summary(m)["ttff_ms"])
    }

    @Test fun frameRenderedBeforePlayRequestDoesNotLoseTtff() {
        // Первый кадр готов на паузе до запроса play: раньше TTFF терялся. Теперь он считается от запроса до «играет».
        val clock = FakeClock()
        val m = metrics(clock)
        clock.advance(200)
        m.onPlayRequested()
        clock.advance(150)
        m.onPlayingChanged(true)
        assertEquals("150", summary(m)["ttff_ms"])
    }

    @Test fun playingWithoutRequestGivesNoTtffButStillCountsAsPlayed() {
        val m = metrics(FakeClock())
        m.onPlayingChanged(true)
        assertNull(summary(m)["ttff_ms"])
        assertTrue(m.hasPlayed())
    }

    @Test fun pageWithoutPlayingOrSeekIsNotReported() {
        val clock = FakeClock()
        val m = metrics(clock)
        assertFalse(m.hasPlayed())
        m.onPlayRequested()
        clock.advance(50)
        assertFalse(m.hasPlayed())
        m.onPlayingChanged(true)
        assertTrue(m.hasPlayed())
    }

    // --- Seek-to-ready ---

    @Test fun seekWithoutStateChangeCompletesAtDiscontinuityAndDoesNotBlockLaterRebuffers() {
        // Перемотка внутри буфера: состояние остаётся READY. Раньше seek оставался открытым навсегда,
        // и следующая реальная ребуферизация не засчитывалась.
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        clock.advance(500)
        m.onSeek()
        clock.advance(5)
        m.onSeekApplied(ready = true)
        assertEquals("5", summary(m)["last_seek_to_ready_ms"])
        clock.advance(1_000)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(300)
        m.onPlaybackState(READY, playWhenReady = true)
        val s = summary(m)
        assertEquals("1", s["rebuffer_count"])
        assertEquals("300", s["rebuffer_total_ms"])
    }

    @Test fun seekWithBufferingCompletesOnReadyNotAtDiscontinuity() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        clock.advance(100)
        m.onSeek()
        m.onSeekApplied(ready = false) // плеер ждёт данных
        clock.advance(40)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(260)
        m.onPlaybackState(READY, playWhenReady = true)
        val s = summary(m)
        assertEquals("300", s["last_seek_to_ready_ms"])
        assertEquals("0", s["rebuffer_count"]) // буферизация во время перемотки не ребуферизация
    }

    @Test fun seekAppliedWhileBufferingDoesNotCompleteEarly() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        m.onSeek()
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        m.onSeekApplied(ready = true) // READY сообщён, но буферизация ещё идёт
        assertNull(summary(m)["last_seek_to_ready_ms"])
    }

    // --- Rebuffering ---

    @Test fun startupBufferingBeforePlayingIsNotARebuffer() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(700)
        m.onPlaybackState(READY, playWhenReady = true)
        val s = summary(m)
        assertEquals("0", s["rebuffer_count"])
        assertEquals("0", s["rebuffer_total_ms"])
    }

    @Test fun rebufferCountsAndDurationWhilePlaying() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        clock.advance(100)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(400)
        m.onPlaybackState(READY, playWhenReady = true)
        clock.advance(50)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(250)
        m.onPlaybackState(READY, playWhenReady = true)
        val s = summary(m)
        assertEquals("2", s["rebuffer_count"])
        assertEquals("650", s["rebuffer_total_ms"])
    }

    @Test fun repeatedBufferingSignalsCountOnce() {
        // Раньше каждое повторное BUFFERING увеличивало счётчик.
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(200)
        m.onPlaybackState(READY, playWhenReady = true)
        val s = summary(m)
        assertEquals("1", s["rebuffer_count"])
        assertEquals("200", s["rebuffer_total_ms"])
    }

    @Test fun bufferingWhilePausedIsNotARebuffer() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        clock.advance(100)
        m.onPlaybackState(BUFFERING, playWhenReady = false) // предзагрузка на паузе
        clock.advance(300)
        m.onPlaybackState(READY, playWhenReady = false)
        assertEquals("0", summary(m)["rebuffer_count"])
    }

    @Test fun pauseDuringRebufferStopsCountingAtPause() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(120)
        m.onPlaybackState(BUFFERING, playWhenReady = false) // пауза посреди буферизации
        clock.advance(900)
        m.onPlaybackState(READY, playWhenReady = false)
        val s = summary(m)
        assertEquals("1", s["rebuffer_count"])
        assertEquals("120", s["rebuffer_total_ms"])
    }

    @Test fun pauseWithPlayWhenReadyEventStopsRebufferAtPause() {
        // Тот же путь, что в плеере: onPlayWhenReadyChanged(false) приходит отдельно от смены состояния.
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        m.onPlaybackState(BUFFERING, playWhenReady = true)
        clock.advance(120)
        m.onPlayWhenReadyChanged(false)
        clock.advance(900)
        m.onPlaybackState(BUFFERING, playWhenReady = false)
        m.onPlaybackState(READY, playWhenReady = false)
        val s = summary(m)
        assertEquals("1", s["rebuffer_count"])
        assertEquals("120", s["rebuffer_total_ms"])
    }

    @Test fun retryRestartsTtffFromRetryNotFromFirstRequest() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        clock.advance(500)
        m.onFailed()
        m.onRetry()
        clock.advance(200)
        m.onPlayingChanged(true)
        assertEquals("200", summary(m)["ttff_ms"])
    }

    @Test fun failureClearsPendingSeekSoItDoesNotCompleteLater() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onPlayingChanged(true)
        m.onPlaybackState(READY, playWhenReady = true)
        m.onSeek()
        m.onFailed()
        clock.advance(400)
        m.onPlaybackState(READY, playWhenReady = true)
        val s = summary(m)
        assertNull(s["last_seek_to_ready_ms"])
        assertEquals("1", s["seek_count"])
    }

    @Test fun bytesReadAreAccumulatedAndNegativeCountsIgnored() {
        val m = metrics(FakeClock())
        m.onBytesRead(4_096)
        m.onBytesRead(-1)
        m.onBytesRead(1_024)
        assertEquals("5120", m.summary(VideoStartBuffer.DEFAULT, 9_999L, streaming = false)["bytes_read"])
    }

    @Test fun summaryHasConfigurationSourceAndNoUnmeasuredValues() {
        val m = metrics(FakeClock())
        val s = m.summary(VideoStartBuffer.MS_1000, 7_000L, streaming = true)
        assertEquals("1000", s["start_buffer"])
        assertEquals("growing_file", s["source"])
        assertEquals("7000", s["file_size_bytes"])
        assertNull(s["ttff_ms"])
        assertNull(s["last_seek_to_ready_ms"])
        assertEquals("4096", s["heap_used_kb_end"])
        assertEquals("complete_file", m.summary(VideoStartBuffer.DEFAULT, 1L, streaming = false)["source"])
        assertFalse(s.keys.any { it.contains("name") || it.contains("path") })
    }
}
