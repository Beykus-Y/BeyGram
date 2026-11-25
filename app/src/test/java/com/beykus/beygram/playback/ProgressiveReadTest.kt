package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressiveReadTest {
    @Test
    fun `a completed file is read up to its real end`() {
        assertEquals(500L, readableBytesAt(position = 500L, fileLength = 1_000L, complete = true, confirmedFromPosition = 0L))
        assertEquals(0L, readableBytesAt(position = 1_000L, fileLength = 1_000L, complete = true, confirmedFromPosition = 0L))
    }

    @Test
    fun `before completion only the confirmed run at this position is readable`() {
        // The file is fully sized on disk, but only 4 KiB from this offset are proven present.
        assertEquals(4096L, readableBytesAt(position = 65_536L, fileLength = 10_000_000L, complete = false, confirmedFromPosition = 4096L))
    }

    @Test
    fun `a hole in the sparse file reports nothing`() {
        assertEquals(0L, readableBytesAt(position = 4_000_000L, fileLength = 10_000_000L, complete = false, confirmedFromPosition = 0L))
    }

    @Test
    fun `the confirmed run never exceeds the declared file length`() {
        assertEquals(100L, readableBytesAt(position = 900L, fileLength = 1_000L, complete = false, confirmedFromPosition = 5_000L))
    }

    @Test
    fun `a position past the end or before the start reads nothing`() {
        assertEquals(0L, readableBytesAt(position = 1_500L, fileLength = 1_000L, complete = false, confirmedFromPosition = 5_000L))
        assertEquals(0L, readableBytesAt(position = -1L, fileLength = 1_000L, complete = true, confirmedFromPosition = 5_000L))
    }
}