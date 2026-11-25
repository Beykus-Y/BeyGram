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

    private fun metrics(clock: FakeClock) = VideoPlaybackMetrics(now = { clock.nowMs }, heapUsedKb = { 4_096L })

    @Test fun ttffIsMeasuredFromPlayRequestAndOnlyOnce() {
        val clock = FakeClock()
        val m = metrics(clock)
        clock.advance(500) // страница создана заранее, но play нажат позже
        m.onPlayRequested()
        clock.advance(320)
        m.onFirstFrame()
        clock.advance(900)
        m.onFirstFrame()
        assertEquals("320", m.summary(VideoStartBuffer.MS_250, 2_000L, streaming = true)["ttff_ms"])
    }

    @Test fun firstFrameBeforePlayRequestDoesNotSetTtff() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onFirstFrame()
        clock.advance(200)
        m.onPlayRequested()
        clock.advance(150)
        m.onFirstFrame()
        assertEquals("150", m.summary(VideoStartBuffer.DEFAULT, 1L, streaming = true)["ttff_ms"])
    }

    @Test fun startupBufferingBeforeFirstFrameIsNotARebuffer() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onBufferingChanged(true)
        clock.advance(700)
        m.onBufferingChanged(false)
        val summary = m.summary(VideoStartBuffer.DEFAULT, 1L, streaming = true)
        assertEquals("0", summary["rebuffer_count"])
        assertEquals("0", summary["rebuffer_total_ms"])
    }

    @Test fun rebufferAfterFirstFrameCountsCountAndDuration() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onFirstFrame()
        clock.advance(100)
        m.onBufferingChanged(true)
        clock.advance(400)
        m.onBufferingChanged(false)
        clock.advance(50)
        m.onBufferingChanged(true)
        clock.advance(250)
        m.onBufferingChanged(false)
        val summary = m.summary(VideoStartBuffer.MS_100, 1L, streaming = true)
        assertEquals("2", summary["rebuffer_count"])
        assertEquals("650", summary["rebuffer_total_ms"])
    }

    @Test fun bufferingDuringSeekIsNotARebufferAndSeekLatencyIsRecorded() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onFirstFrame()
        clock.advance(500)
        m.onSeek()
        m.onBufferingChanged(true)
        clock.advance(180)
        m.onBufferingChanged(false)
        val summary = m.summary(VideoStartBuffer.DEFAULT, 1L, streaming = true)
        assertEquals("1", summary["seek_count"])
        assertEquals("0", summary["rebuffer_count"])
        assertEquals("180", summary["last_seek_to_ready_ms"])
    }

    @Test fun seekWithoutBufferingStillMeasuresLatency() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onPlayRequested()
        m.onFirstFrame()
        m.onSeek()
        clock.advance(60)
        m.onBufferingChanged(false)
        assertEquals("60", m.summary(VideoStartBuffer.DEFAULT, 1L, streaming = true)["last_seek_to_ready_ms"])
    }

    @Test fun pageWithoutPlaybackOrSeekIsNotReported() {
        val clock = FakeClock()
        val m = metrics(clock)
        assertFalse(m.hasPlayed())
        m.onPlayRequested()
        clock.advance(50)
        assertFalse(m.hasPlayed())
        m.onFirstFrame()
        assertTrue(m.hasPlayed())
    }

    @Test fun seekAloneCountsAsPlayed() {
        val m = metrics(FakeClock())
        m.onSeek()
        assertTrue(m.hasPlayed())
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
        val summary = m.summary(VideoStartBuffer.MS_1000, 7_000L, streaming = true)
        assertEquals("1000", summary["start_buffer"])
        assertEquals("growing_file", summary["source"])
        assertEquals("7000", summary["file_size_bytes"])
        assertNull(summary["ttff_ms"])
        assertNull(summary["last_seek_to_ready_ms"])
        assertEquals("4096", summary["heap_used_kb_end"])
        assertEquals("complete_file", m.summary(VideoStartBuffer.DEFAULT, 1L, streaming = false)["source"])
        assertFalse(summary.keys.any { it.contains("name") || it.contains("path") })
    }
}
