package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPlaybackMetricsTest {
    private class FakeClock(var nowMs: Long = 1_000L) {
        fun advance(ms: Long) { nowMs += ms }
    }

    private fun metrics(clock: FakeClock) = VideoPlaybackMetrics(now = { clock.nowMs }, heapUsedKb = { 4_096L })

    @Test fun ttffIsMeasuredFromPlayerCreationAndOnlyOnce() {
        val clock = FakeClock()
        val m = metrics(clock)
        clock.advance(320)
        m.onFirstFrame()
        clock.advance(900)
        m.onFirstFrame()
        val summary = m.summary(VideoStartBuffer.MS_250, fileSizeBytes = 2_000L)
        assertEquals("320", summary["ttff_ms"])
    }

    @Test fun startupBufferingBeforeFirstFrameIsNotARebuffer() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onBufferingChanged(true)
        clock.advance(700)
        m.onBufferingChanged(false)
        val summary = m.summary(VideoStartBuffer.DEFAULT, fileSizeBytes = 1L)
        assertEquals("0", summary["rebuffer_count"])
        assertEquals("0", summary["rebuffer_total_ms"])
    }

    @Test fun rebufferAfterFirstFrameCountsCountAndDuration() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onFirstFrame()
        clock.advance(100)
        m.onBufferingChanged(true)
        clock.advance(400)
        m.onBufferingChanged(false)
        clock.advance(50)
        m.onBufferingChanged(true)
        clock.advance(250)
        m.onBufferingChanged(false)
        val summary = m.summary(VideoStartBuffer.MS_100, fileSizeBytes = 1L)
        assertEquals("2", summary["rebuffer_count"])
        assertEquals("650", summary["rebuffer_total_ms"])
    }

    @Test fun bufferingDuringSeekIsNotARebufferAndSeekLatencyIsRecorded() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onFirstFrame()
        clock.advance(500)
        m.onSeek()
        m.onBufferingChanged(true)
        clock.advance(180)
        m.onBufferingChanged(false)
        val summary = m.summary(VideoStartBuffer.DEFAULT, fileSizeBytes = 1L)
        assertEquals("1", summary["seek_count"])
        assertEquals("0", summary["rebuffer_count"])
        assertEquals("180", summary["last_seek_to_ready_ms"])
    }

    @Test fun seekBeforeReadyWithoutBufferingStillMeasuresLatency() {
        val clock = FakeClock()
        val m = metrics(clock)
        m.onFirstFrame()
        m.onSeek()
        clock.advance(60)
        m.onBufferingChanged(false)
        assertEquals("60", m.summary(VideoStartBuffer.DEFAULT, 1L)["last_seek_to_ready_ms"])
    }

    @Test fun bytesReadAreAccumulatedAndNegativeCountsIgnored() {
        val m = metrics(FakeClock())
        m.onBytesRead(4_096)
        m.onBytesRead(-1)
        m.onBytesRead(1_024)
        assertEquals("5120", m.summary(VideoStartBuffer.DEFAULT, 9_999L)["bytes_read"])
    }

    @Test fun summaryHasConfigurationAndNoUnmeasuredValues() {
        val m = metrics(FakeClock())
        val summary = m.summary(VideoStartBuffer.MS_1000, fileSizeBytes = 7_000L)
        assertEquals("1000", summary["start_buffer"])
        assertEquals("7000", summary["file_size_bytes"])
        assertNull(summary["ttff_ms"])
        assertNull(summary["last_seek_to_ready_ms"])
        assertEquals("4096", summary["heap_used_kb_end"])
        assertFalse(summary.keys.any { it.contains("name") || it.contains("path") })
    }
}
