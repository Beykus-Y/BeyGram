package com.beykus.beygram.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicBotCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun source(): File = temporary.newFile().apply { writeText("audio") }

    @Test fun storedCopyHasItsOwnNameAndContent() {
        val dir = File(temporary.root, DIR)
        val first = MusicBotCache.store(source(), dir)
        val second = MusicBotCache.store(source(), dir)

        assertTrue(first.exists() && second.exists())
        assertFalse(first.absolutePath == second.absolutePath)
        assertEquals("audio", first.readText())
        MusicBotCache.discard(first.absolutePath)
        MusicBotCache.discard(second.absolutePath)
    }

    @Test fun pruneKeepsPlayedAndPendingCopiesAndRemovesTheRest() {
        val dir = File(temporary.root, DIR)
        val playing = MusicBotCache.store(source(), dir)
        MusicBotCache.handOver(playing.absolutePath)
        val pending = MusicBotCache.store(source(), dir)
        val stale = MusicBotCache.store(source(), dir)
        MusicBotCache.handOver(stale.absolutePath)

        MusicBotCache.prune(dir, setOf(playing.absolutePath))

        assertTrue(playing.exists())
        assertTrue(pending.exists())
        assertFalse(stale.exists())
        MusicBotCache.discard(pending.absolutePath)
        MusicBotCache.discard(playing.absolutePath)
    }

    @Test fun copyStaysProtectedUntilHandedOverAndIsRemovedAfterwards() {
        val dir = File(temporary.root, DIR)
        val copy = MusicBotCache.store(source(), dir)

        MusicBotCache.prune(dir, emptySet())
        assertTrue(copy.exists())

        MusicBotCache.handOver(copy.absolutePath)
        MusicBotCache.prune(dir, emptySet())
        assertFalse(copy.exists())
    }

    @Test fun discardRemovesACopyNobodyWillPlay() {
        val copy = MusicBotCache.store(source(), File(temporary.root, DIR))

        MusicBotCache.discard(copy.absolutePath)

        assertFalse(copy.exists())
    }

    private companion object {
        const val DIR = "music-bot"
    }
}
