package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Regression tests for build 815: the queue kept a path after the copy had been evicted, so the next row was never
 * fetched again, ExoPlayer opened a dead URI and BG Mix never got a complete file for the next deck.
 *
 * The rule under test: a non-empty [PlaybackItem.path] is not proof of a copy. Only an existing, non-empty file is.
 */
class PlaybackSourceAvailabilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun item(key: String, path: String = "", fileId: Int? = null) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = path,
        title = key,
        durationSeconds = 180,
        fileId = fileId,
    )

    private fun realFile(name: String): File = tempFolder.newFile(name).apply { writeText("audio-bytes") }

    @Test
    fun aPathWhoseFileWasRemovedIsNotACopy() {
        val file = realFile("evicted.mp3")
        assertTrue(localCopyReady(file.absolutePath))
        assertTrue(item("ym:1", file.absolutePath).hasReadyLocalCopy)

        file.delete()

        assertFalse(localCopyReady(file.absolutePath))
        assertFalse(item("ym:1", file.absolutePath).hasReadyLocalCopy)
        assertFalse(item("ym:1", "").hasReadyLocalCopy)
    }

    @Test
    fun anEmptyFileIsNotAFinishedCopy() {
        val empty = tempFolder.newFile("partial.mp3")
        assertFalse(localCopyReady(empty.absolutePath))
    }

    @Test
    fun aRowWithAStalePathIsPrefetchedAgain() {
        val evicted = File(tempFolder.root, "gone.mp3")
        val current = item("ym:0", realFile("current.mp3").absolutePath)
        val stale = item("ym:1", evicted.absolutePath)
        val missing = item("ym:2")
        val ready = item("ym:3", realFile("ready.mp3").absolutePath)
        val state = PlaybackState(
            item = current,
            queue = listOf(current, stale, missing, ready),
            queueIndex = 0,
        )

        // The evicted row is a candidate again even though its path is not blank; a row with a real copy is not.
        assertEquals(listOf("ym:1", "ym:2"), prefetchCandidates(state).map { it.key })
    }

    @Test
    fun aGrowingRowIsNotPrefetchedTwice() {
        val current = item("ym:0", realFile("current.mp3").absolutePath)
        val growing = item("ym:1").copy(
            growing = GrowingPlaybackSource(
                totalSize = 100,
                readablePrefix = { 50 },
                isComplete = { false },
                completedPath = { null },
                isDownloading = { true },
                onNeedMore = {},
            )
        )
        val state = PlaybackState(item = current, queue = listOf(current, growing), queueIndex = 0)
        assertTrue(prefetchCandidates(state).isEmpty())
    }

    @Test
    fun aProviderRowWithAReadyCopyIsNotPrefetchedAgain() {
        val current = item("ym:0", realFile("current.mp3").absolutePath)
        val resolved = item("ym:1", realFile("resolved.mp3").absolutePath)
        val state = PlaybackState(item = current, queue = listOf(current, resolved), queueIndex = 0)
        // The resolver path is the production rule: an existing file means no new fetch.
        assertTrue(prefetchCandidates(state, hasCopy = { it.hasReadyLocalCopy }).isEmpty())
    }

    @Test
    fun theResolverFetchesAgainAfterItsCopyWasEvicted() = runBlocking {
        var fetches = 0
        val delivered = mutableListOf<File>()
        val resolver = object : LazyTrackResolver<String>("avail:", maxConcurrentFetches = 1) {
            override val restricted = false
            override fun pathOf(track: String): String = track
            override fun discardCopy(path: String) = Unit
            override fun handOverCopy(path: String) = Unit
            init {
                setFetch {
                    fetches++
                    val copy = tempFolder.newFile("copy-$fetches.mp3").apply { writeText("audio") }
                    delivered += copy
                    Result.success(copy.absolutePath)
                }
            }
        }
        try {
            resolver.request("avail:a")
            awaitReady(resolver, "avail:a")
            assertEquals(delivered.first().absolutePath, resolver.resolvedPath("avail:a"))

            // The cache (or the user) removed the copy: the queue row still names the old path.
            assertTrue(delivered.first().delete())
            assertNull(resolver.resolvedPath("avail:a"))

            resolver.request("avail:a")
            awaitReady(resolver, "avail:a")
            assertEquals(2, fetches)
            assertEquals(delivered.last().absolutePath, resolver.resolvedPath("avail:a"))
            assertTrue(delivered.last().isFile)
        } finally {
            resolver.reset()
        }
    }

    @Test
    fun aStaleItemPathIsRevalidatedThroughTheAccountSource() {
        val observed = realFile("observed.mp3")
        val downloads = mutableListOf<Int>()
        val fileStates = MutableStateFlow(MediaFileState(id = 7, size = 11, localPath = observed.absolutePath))
        val source = PlaybackQueueSource(
            accountId = "legacy",
            observeFile = { fileStates },
            download = { downloads += it },
        )
        val stale = item("tg:legacy|x", path = File(tempFolder.root, "deleted.mp3").absolutePath, fileId = 7)
        val handle = PlaybackSourceHandle(stale, source)

        // The dead row path is not returned: the account-scoped state is asked and names the real file.
        assertEquals(observed.absolutePath, handle.resolve()?.path)
        assertTrue(handle.canResolve())

        // Asking for the row again goes to the account source instead of trusting the old path.
        handle.request()
        assertEquals(listOf(7), downloads)
    }

    @Test
    fun aDeadPathWithoutAnySourceCannotResolveAndFailsFast() {
        val handle = PlaybackSourceHandle(item("local|legacy|/missing.mp3", path = "/missing.mp3"), queueSource = null)
        assertNull(handle.resolve())
        assertFalse(handle.canResolve())
        assertFalse(handle.failed())
    }

    @Test
    fun aProviderVerdictIsReportedAndTheRowIsNotFetchedInALoop() = runBlocking {
        val resolver = object : LazyTrackResolver<String>("verdict:", maxConcurrentFetches = 1) {
            override val restricted = false
            override fun pathOf(track: String): String = track
            override fun discardCopy(path: String) = Unit
            override fun handOverCopy(path: String) = Unit
            init {
                setFetch { Result.success(null) }
            }
        }
        try {
            resolver.request("verdict:a")
            val deadline = System.currentTimeMillis() + 5_000L
            while (!resolver.failed("verdict:a") && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertTrue(resolver.failed("verdict:a"))
            assertNull(resolver.resolvedPath("verdict:a"))
            // A verdict is final for the entry: another request does not start a fresh fetch.
            resolver.request("verdict:a")
            assertTrue(resolver.failed("verdict:a"))
        } finally {
            resolver.reset()
        }
    }

    private suspend fun awaitReady(resolver: LazyTrackResolver<String>, key: String) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (resolver.resolvedPath(key) == null && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10)
        }
    }
}