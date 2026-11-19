package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.PinnedMessagePage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pinned search: a pin change restarts the same query, and an answer of a superseded query or generation never
 * reaches the list. Paging adds older matches only for the query that is still current.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PinnedSearchStateTest {
    private fun message(id: Long) = ChatMessage(id = id, chatId = 1L, text = "pinned $id", time = "", isOutgoing = false)

    @Test
    fun aPinChangeWhileTheFirstPageLoadsRunsTheSameQueryAgain() = runTest {
        // The first answer hangs until the test lets it through. The pin change restarts the query in the meantime.
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val search = PinnedSearchState(debounceMillis = 0L) { _, _ ->
            calls++
            if (calls == 1) {
                gate.await()
                // This answer was built before the unpin, so it still lists the message that is gone.
                Result.success(PinnedMessagePage(listOf(message(300L)), 1, 0L))
            } else {
                Result.success(PinnedMessagePage(listOf(message(200L)), 1, 0L))
            }
        }
        val stale = async { search.restart("pin") }
        runCurrent()
        search.restart("pin")
        assertEquals(listOf(200L), search.results.map { it.id })
        gate.complete(Unit)
        stale.await()
        assertEquals(listOf(200L), search.results.map { it.id })
        assertFalse(search.busy)
    }

    @Test
    fun anEmptySearchPicksUpAPinThatWasAddedAfterItsAnswer() = runTest {
        var pins = emptyList<Long>()
        val search = PinnedSearchState(debounceMillis = 0L) { _, _ ->
            Result.success(PinnedMessagePage(pins.map { message(it) }, pins.size, 0L))
        }
        search.restart("pin")
        assertTrue(search.results.isEmpty())
        pins = listOf(200L)
        search.restart("pin")
        assertEquals(listOf(200L), search.results.map { it.id })
    }

    @Test
    fun aPageOfTheSupersededQueryNeverReachesTheNewOne() = runTest {
        val gate = CompletableDeferred<Unit>()
        val search = PinnedSearchState(debounceMillis = 0L) { query, fromMessageId ->
            when {
                query == "old" && fromMessageId == 0L -> Result.success(PinnedMessagePage(listOf(message(300L)), 2, 200L))
                query == "old" -> {
                    gate.await()
                    Result.success(PinnedMessagePage(listOf(message(100L)), 2, 0L))
                }
                else -> Result.success(PinnedMessagePage(listOf(message(250L)), 1, 0L))
            }
        }
        search.restart("old")
        val page = async { search.loadMore() }
        runCurrent()
        search.restart("new")
        assertEquals(listOf(250L), search.results.map { it.id })
        gate.complete(Unit)
        page.await()
        assertEquals(listOf(250L), search.results.map { it.id })
        assertEquals(0L, search.nextId)
        assertFalse(search.busy)
    }

    @Test
    fun loadMoreAppendsTheOlderMatchesAndStopsAtTheLastPage() = runTest {
        val search = PinnedSearchState(debounceMillis = 0L) { _, fromMessageId ->
            when (fromMessageId) {
                0L -> Result.success(PinnedMessagePage(listOf(message(300L), message(200L)), 4, 200L))
                200L -> Result.success(PinnedMessagePage(listOf(message(200L), message(100L)), 4, 0L))
                else -> Result.success(PinnedMessagePage(emptyList(), 4, 0L))
            }
        }
        search.restart("pin")
        search.loadMore()
        assertEquals(listOf(300L, 200L, 100L), search.results.map { it.id })
        // A spent cursor asks for nothing.
        search.loadMore()
        assertEquals(listOf(300L, 200L, 100L), search.results.map { it.id })
    }

    @Test
    fun aFailedSearchIsFlaggedAndTheSameQueryRecoversOnRestart() = runTest {
        var offline = true
        val search = PinnedSearchState(debounceMillis = 0L) { _, _ ->
            if (offline) {
                Result.failure(IllegalStateException("offline"))
            } else {
                Result.success(PinnedMessagePage(listOf(message(200L)), 1, 0L))
            }
        }
        search.restart("pin")
        assertTrue(search.failed)
        assertTrue(search.results.isEmpty())
        offline = false
        // The retry restarts the same query at once.
        search.restart("pin")
        assertFalse(search.failed)
        assertEquals(listOf(200L), search.results.map { it.id })
    }

    @Test
    fun aChangedQueryWaitsForTheTypingToPauseAndTheSameQueryDoesNot() = runTest {
        val asked = mutableListOf<String>()
        val search = PinnedSearchState(debounceMillis = 300L) { query, _ ->
            asked += query
            Result.success(PinnedMessagePage(listOf(message(200L)), 1, 0L))
        }
        val typing = async { search.restart("p") }
        runCurrent()
        // The next keystroke supersedes the first query before its pause has ended, so "p" is never asked for.
        typing.cancel()
        search.restart("pi")
        assertEquals(listOf("pi"), asked)
        // The same query again, as after a pin change, runs at once.
        search.restart("pi")
        assertEquals(listOf("pi", "pi"), asked)
    }
}
