package com.beykus.beygram.playback

import com.beykus.beygram.data.media.Mp4Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveAttemptDiagnosticsTest {
    private class FakeClock(var nowMs: Long = 1_000L) {
        fun advance(ms: Long) { nowMs += ms }
    }

    private fun attempt(clock: FakeClock, streaming: Boolean = true) =
        ProgressiveAttemptDiagnostics(attempt = 7, senderSupportsStreaming = streaming, now = { clock.nowMs })

    private fun ProgressiveAttemptDiagnostics.waitingFor(result: String, types: List<String>) {
        probeStarted()
        probeFinished(result, Mp4Layout(types, moovPayloadBytes = null))
    }

    @Test fun moovAfterMediaDataGivesMoovAtEnd() {
        val clock = FakeClock()
        val d = attempt(clock)
        d.snapshot(hasPartialPath = true, prefixBytes = 8_000_000, totalBytes = 40_000_000, downloadOffset = 0, downloading = true)
        d.waitingFor("MoovAfterMediaData", listOf("ftyp", "mdat", "moov"))
        assertEquals("moov_at_end", d.cause())
    }

    @Test fun missingMoovAfterMdatIsSuspectedMoovAtEnd() {
        val clock = FakeClock()
        val d = attempt(clock)
        d.snapshot(true, 8_000_000, 40_000_000, 0, true)
        d.waitingFor("MissingMoov", listOf("ftyp", "free", "mdat"))
        assertEquals("moov_at_end", d.cause())
    }

    @Test fun missingMoovBeforeAnyMdatIsPrefixTooSmall() {
        val clock = FakeClock()
        val d = attempt(clock)
        d.snapshot(true, 300_000, 40_000_000, 0, true)
        d.waitingFor("MissingMoov", listOf("ftyp"))
        assertEquals("prefix_too_small", d.cause())
    }

    @Test fun truncatedMoovIsPrefixTooSmall() {
        val d = attempt(FakeClock())
        d.snapshot(true, 1_000_000, 40_000_000, 0, true)
        d.waitingFor("TruncatedMoov", listOf("ftyp", "moov"))
        assertEquals("prefix_too_small", d.cause())
    }

    @Test fun nonZeroDownloadOffsetIsReportedFirst() {
        val d = attempt(FakeClock())
        d.snapshot(true, 1_000_000, 40_000_000, downloadOffset = 4_096, downloading = true)
        d.waitingFor("NotMp4", emptyList())
        assertEquals("offset_nonzero", d.cause())
    }

    @Test fun probeOnlyCancelledIsProbeStarved() {
        val clock = FakeClock()
        val d = attempt(clock)
        d.snapshot(true, 500_000, 40_000_000, 0, true)
        d.probeStarted()
        clock.advance(120)
        d.probeCancelled()
        d.probeStarted()
        clock.advance(80)
        d.probeCancelled()
        assertEquals("probe_starved", d.cause())
        assertTrue(d.finish()!!["probe"]!!.contains("runs=2,cancelled=2,ms=200"))
    }

    @Test fun unsupportedCodecAndUnknownCases() {
        val d = attempt(FakeClock())
        d.snapshot(true, 9_000_000, 40_000_000, 0, true)
        d.waitingFor("UnsupportedVideoCodec", listOf("ftyp", "moov", "mdat"))
        assertEquals("codec_unsupported", d.cause())

        val e = attempt(FakeClock())
        e.snapshot(false, 0, 40_000_000, 0, false)
        assertEquals("unknown", e.cause())
    }

    @Test fun unlockedAttemptHasNoCause() {
        val clock = FakeClock()
        val d = attempt(clock)
        d.snapshot(true, 2_000_000, 40_000_000, 0, true)
        d.waitingFor("Streamable", listOf("ftyp", "moov", "mdat"))
        clock.advance(250)
        d.unlocked()
        assertEquals("none", d.cause())
        assertEquals("250", d.finish()!!["unlock_ms"])
    }

    @Test fun attemptWithoutPlaybackIsStillRecordedOnce() {
        val d = attempt(FakeClock(), streaming = false)
        d.snapshot(false, 0, 40_000_000, 0, false)
        val first = d.finish()
        assertNotNull(first)
        assertEquals("false", first!!["has_partial_path"])
        assertEquals("false", first["sender_streaming"])
        assertEquals("false", first["unlocked"])
        assertEquals("none", first["probe_result"])
        assertNull(d.finish())
    }

    @Test fun readsWaitsAndStallsAreCounted() {
        val clock = FakeClock()
        val d = attempt(clock)
        d.onWait()
        d.onWait()
        d.onNeedMore()
        clock.advance(40)
        d.onRead(4_096)
        d.onRead(-1)
        d.onRead(1_024)
        d.onStall("no_transfer")
        d.onStall("timeout")
        d.onStall("timeout")
        val attrs = d.finish()!!
        assertEquals("waits=2,need_more=1", attrs["reads"])
        assertEquals("5120", attrs["bytes_read"])
        assertEquals("40", attrs["first_read_ms"])
        assertEquals("no_transfer=1,timeout=2", attrs["stalls"])
    }

    @Test fun firstReadKeepsItsFirstTimestamp() {
        val clock = FakeClock()
        val d = attempt(clock)
        clock.advance(30)
        d.onRead(10)
        clock.advance(500)
        d.onRead(10)
        assertEquals("30", d.finish()!!["first_read_ms"])
    }

    @Test fun mediaStatesAndPlaybackTimingAreRecordedOncePerState() {
        val clock = FakeClock()
        val d = attempt(clock)
        clock.advance(100)
        d.onMediaState(2) // BUFFERING
        clock.advance(200)
        d.onMediaState(3) // READY
        clock.advance(50)
        d.onMediaState(2) // повтор BUFFERING не перезаписывает отметку
        clock.advance(10)
        d.onMediaState(3)
        d.onPlaying()
        clock.advance(999)
        d.onPlaying()
        val attrs = d.finish()!!
        assertEquals("BUFFERING@100,READY@300", attrs["media_states"])
        assertEquals("300", attrs["first_ready_ms"])
        // Первый onPlaying пришёл на 360 мс; повтор на 1359 мс отметку не меняет.
        assertEquals("360", attrs["playing_ms"])
    }

    @Test fun playerErrorKeepsTheFirstCodeName() {
        val d = attempt(FakeClock())
        d.onPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED")
        d.onPlayerError("ERROR_CODE_UNSPECIFIED")
        assertEquals("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", d.finish()!!["player_error"])
    }

    @Test fun completedFileIsNotAnAttemptAndNotUnknown() {
        val d = attempt(FakeClock())
        d.snapshot(hasPartialPath = true, prefixBytes = 40_000_000, totalBytes = 40_000_000, downloadOffset = 0, downloading = false, completed = true)
        assertFalse(d.hasAttempt())
        assertEquals("complete", d.cause())
    }

    @Test fun onlyRealProgressiveWorkCountsAsAttempt() {
        val d = attempt(FakeClock())
        d.snapshot(hasPartialPath = false, prefixBytes = 0, totalBytes = 40_000_000, downloadOffset = 0, downloading = false)
        assertFalse(d.hasAttempt())
        d.probeStarted()
        d.probeFinished("NotMp4", null)
        assertTrue(d.hasAttempt())
    }

    @Test fun recordIsCompactAndHasNoFileOrChatIdentifiers() {
        val clock = FakeClock()
        val d = attempt(clock)
        d.snapshot(true, 1_000_000, 40_000_000, 0, true)
        d.waitingFor("MissingMoov", listOf("ftyp", "mdat", "free", "moov", "udta", "wide", "skip", "meta", "junk"))
        d.rejected("NotMp4")
        val attrs = d.finish()!!
        assertTrue("at most 24 attributes", attrs.size <= 24)
        assertEquals("ftyp,mdat,free,moov,udta,wide,skip,meta", attrs["layout"])
        for ((key, value) in attrs) {
            assertFalse("no path in $key", value.contains('/') || value.contains(".mp4") || value.contains("content:"))
            assertFalse("no key names files or chats: $key", key.contains("name") || key.contains("chat") || key.contains("file_id"))
        }
    }
}
