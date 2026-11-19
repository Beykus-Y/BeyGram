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
 * The pinned holder's loading rules: no duplicate first-page requests, a failed page that keeps the loaded pins,
 * a retry that never turns into a paging loop, and a stale answer that a newer reload drops.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PinnedMessagesStateTest {
    private fun message(id: Long) = ChatMessage(id = id, chatId = 1L, text = "pinned $id", time = "", isOutgoing = false)

    private class ScriptedPins {
        val cursors = mutableListOf<Long>()
        var firstPage: Result<PinnedMessagePage> =
            Result.success(PinnedMessagePage(listOf(300L, 200L).map { id ->
                ChatMessage(id = id, chatId = 1L, text = "", time = "", isOutgoing = false)
            }, 4, 200L))
        var olderPages: MutableMap<Long, Result<PinnedMessagePage>> = mutableMapOf()

        fun suspendLoad(fromMessageId: Long): Result<PinnedMessagePage> {
            cursors += fromMessageId
            return if (fromMessageId == 0L) firstPage else olderPages[fromMessageId]
                ?: Result.success(PinnedMessagePage(emptyList(), 4, 0L))
        }

        fun holder(topicId: Long = 0L) = PinnedMessagesState(1L, topicId, ::suspendLoad)
    }

    @Test
    fun anUnchangedVersionAndConnectionNeverReloads() = runTest {
        val script = ScriptedPins()
        val state = script.holder()
        state.refresh(1L, connected = true)
        state.refresh(1L, connected = true)
        assertEquals(listOf(0L), script.cursors)
        assertEquals(2, state.pins.size)
        assertEquals(4, state.count)
    }

    @Test
    fun aNewVersionOrAConnectionChangeReloadsTheFirstPage() = runTest {
        val script = ScriptedPins()
        val state = script.holder()
        state.refresh(1L, connected = true)
        state.refresh(2L, connected = true)
        state.refresh(2L, connected = false)
        assertEquals(listOf(0L, 0L, 0L), script.cursors)
    }

    @Test
    fun aFailedFirstPageReloadsOnlyThroughTheRetry() = runTest {
        val script = ScriptedPins()
        script.firstPage = Result.failure(IllegalStateException("offline"))
        val state = script.holder()
        state.refresh(1L, connected = true)
        assertTrue(state.failed)
        assertTrue(state.pins.isEmpty())
        // The same inputs ask for nothing: the retry affordance owns the next attempt.
        state.refresh(1L, connected = true)
        assertEquals(1, script.cursors.size)
        script.firstPage = Result.success(PinnedMessagePage(emptyList(), 0, 0L))
        state.requestRetry()
        state.refresh(1L, connected = true)
        assertEquals(2, script.cursors.size)
        assertFalse(state.failed)
    }

    @Test
    fun loadOlderAppendsWithoutDuplicatesAndStopsAtTheLastPage() = runTest {
        val script = ScriptedPins()
        script.olderPages[200L] = Result.success(
            PinnedMessagePage(listOf(message(200L), message(150L), message(100L)), 4, 100L)
        )
        script.olderPages[100L] = Result.success(PinnedMessagePage(listOf(message(100L), message(50L)), 4, 0L))
        val state = script.holder()
        state.refresh(1L, connected = true)
        state.loadOlder()
        // Overlapping and repeated ids collapse; the order stays newest first.
        assertEquals(listOf(300L, 200L, 150L, 100L), state.pins.map { it.id })
        state.loadOlder()
        assertEquals(listOf(300L, 200L, 150L, 100L, 50L), state.pins.map { it.id })
        // A spent cursor (nextId 0) asks for nothing.
        state.loadOlder()
        assertEquals(listOf(0L, 200L, 100L), script.cursors)
    }

    @Test
    fun aFailedOlderPageKeepsTheLoadedPinsAndRetriesTheSameCursor() = runTest {
        val script = ScriptedPins()
        script.olderPages[200L] = Result.failure(IllegalStateException("page failed"))
        val state = script.holder()
        state.refresh(1L, connected = true)
        state.loadOlder()
        assertTrue(state.failed)
        assertEquals(listOf(300L, 200L), state.pins.map { it.id })
        // The last valid preview stays; the retry asks for the same page again.
        script.olderPages[200L] = Result.success(PinnedMessagePage(listOf(message(150L)), 4, 0L))
        state.loadOlder()
        assertFalse(state.failed)
        assertEquals(listOf(300L, 200L, 150L), state.pins.map { it.id })
        assertEquals(listOf(0L, 200L, 200L), script.cursors)
    }

    @Test
    fun aRetryOfTheInitialRequestNeverPages() = runTest {
        val script = ScriptedPins()
        script.firstPage = Result.failure(IllegalStateException("offline"))
        val state = script.holder()
        state.refresh(1L, connected = true)
        // The viewport is older than every (empty) pin, but there is no cursor to page from.
        state.loadOlder()
        assertEquals(listOf(0L), script.cursors)
        script.firstPage = Result.success(PinnedMessagePage(emptyList(), 0, 0L))
        state.requestRetry()
        state.refresh(1L, connected = true)
        state.loadOlder()
        assertEquals(listOf(0L, 0L), script.cursors)
    }

    @Test
    fun anAnswerFromASupersededReloadIsDropped() = runTest {
        // The first request hangs until the test lets it answer, so the second reload finishes first.
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val state = PinnedMessagesState(1L, 0L) { _ ->
            calls++
            if (calls == 1) {
                gate.await()
                Result.success(PinnedMessagePage(listOf(message(300L)), 1, 0L))
            } else {
                Result.success(PinnedMessagePage(listOf(message(999L)), 1, 0L))
            }
        }
        val stale = async { state.refresh(1L, connected = true) }
        runCurrent()
        state.refresh(2L, connected = true)
        assertEquals(listOf(999L), state.pins.map { it.id })
        gate.complete(Unit)
        stale.await()
        // The stale answer of the older generation must not replace the newer list.
        assertEquals(listOf(999L), state.pins.map { it.id })
        assertFalse(state.loading)
    }

    @Test
    fun aFailedRefreshRetriesTheFirstPageEvenWhileAnOlderCursorIsOpen() = runTest {
        val script = ScriptedPins()
        val state = script.holder()
        state.refresh(1L, connected = true)
        // A pin was removed elsewhere, but the reload fails: the last valid pins and the open older cursor stay.
        script.firstPage = Result.failure(IllegalStateException("offline"))
        state.refresh(2L, connected = true)
        assertTrue(state.failed)
        assertEquals(listOf(300L, 200L), state.pins.map { it.id })
        // The retry asks for the first page again, and never for the older page at the open cursor.
        val ticks = state.retryTick
        script.firstPage = Result.success(PinnedMessagePage(listOf(message(300L)), 3, 0L))
        state.retryFailedLoad()
        assertEquals(ticks + 1, state.retryTick)
        // The owner's effect runs the refresh that the retry asked for.
        state.refresh(2L, connected = true)
        assertEquals(listOf(0L, 0L, 0L), script.cursors)
        assertFalse(state.failed)
        assertEquals(listOf(300L), state.pins.map { it.id })
    }

    @Test
    fun anOlderPageCannotClearAFailedRefresh() = runTest {
        val script = ScriptedPins()
        val state = script.holder()
        state.refresh(1L, connected = true)
        script.firstPage = Result.failure(IllegalStateException("offline"))
        state.refresh(2L, connected = true)
        // The older cursor is still open, but the failed load is the refresh: no older page is requested.
        state.loadOlder()
        assertTrue(state.failed)
        assertEquals(listOf(0L, 0L), script.cursors)
    }

    @Test
    fun aFailedOlderPageIsRetriedAsAnOlderPage() = runTest {
        val script = ScriptedPins()
        script.olderPages[200L] = Result.failure(IllegalStateException("page failed"))
        val state = script.holder()
        state.refresh(1L, connected = true)
        state.loadOlder()
        assertTrue(state.failed)
        script.olderPages[200L] = Result.success(PinnedMessagePage(listOf(message(150L)), 4, 0L))
        state.retryFailedLoad()
        assertFalse(state.failed)
        assertEquals(listOf(300L, 200L, 150L), state.pins.map { it.id })
        assertEquals(listOf(0L, 200L, 200L), script.cursors)
    }
}
