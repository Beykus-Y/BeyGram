package com.beykus.beygram.playback

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicBotCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun source(): File = temporary.newFile().apply { writeText("audio") }

    private val dir: File by lazy { File(temporary.root, DIR) }

    @Test fun copiesGetOwnNamesAndContent() = runBlocking {
        val first = MusicBotCache.copyReserved(source(), dir)
        val second = MusicBotCache.copyReserved(source(), dir)

        assertTrue(first.exists() && second.exists())
        assertFalse(first.absolutePath == second.absolutePath)
        assertEquals("audio", first.readText())
        MusicBotCache.discard(first.absolutePath)
        MusicBotCache.discard(second.absolutePath)
    }

    @Test fun pruneKeepsPlayedAndPendingCopiesAndRemovesTheRest() = runBlocking {
        val playing = MusicBotCache.copyReserved(source(), dir)
        MusicBotCache.handOver(playing.absolutePath)
        val pending = MusicBotCache.copyReserved(source(), dir)
        val stale = MusicBotCache.copyReserved(source(), dir)
        MusicBotCache.handOver(stale.absolutePath)

        MusicBotCache.prune(dir, setOf(playing.absolutePath))

        assertTrue(playing.exists())
        assertTrue(pending.exists())
        assertFalse(stale.exists())
        MusicBotCache.discard(pending.absolutePath)
        MusicBotCache.discard(playing.absolutePath)
    }

    @Test fun copyStaysProtectedUntilHandedOverAndIsRemovedAfterwards() = runBlocking {
        val copy = MusicBotCache.copyReserved(source(), dir)

        MusicBotCache.prune(dir, emptySet())
        assertTrue(copy.exists())

        MusicBotCache.handOver(copy.absolutePath)
        MusicBotCache.prune(dir, emptySet())
        assertFalse(copy.exists())
    }

    @Test fun discardRemovesACopyNobodyWillPlay() = runBlocking {
        val copy = MusicBotCache.copyReserved(source(), dir)

        MusicBotCache.discard(copy.absolutePath)

        assertFalse(copy.exists())
    }

    @Test fun cancelledCopyLeavesNoFileAndNoReservation() = runBlocking {
        val before = MusicBotCache.pendingCount
        val result = runCatching {
            withTimeout(0L) { MusicBotCache.copyReserved(source(), dir) }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(before, MusicBotCache.pendingCount)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    private companion object {
        const val DIR = "music-bot"
    }
}
