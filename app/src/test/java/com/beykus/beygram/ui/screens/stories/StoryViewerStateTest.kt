package com.beykus.beygram.ui.screens.stories

import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.StoryPreview
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryViewerStateTest {
    private fun preview(id: Int, date: Long = id.toLong()) = StoryPreview(id, date)

    private fun entry(vararg ids: Int) = ChatStories(
        chatId = 7L,
        title = "Author",
        initials = "A",
        avatarColorArgb = 0L,
        stories = ids.map { preview(it) }
    )

    @Test
    fun emptyListUpdateKeepsTheLastKnownAuthor() {
        val previous = entry(1, 2)

        assertSame(previous, storyViewerEntryUpdate(previous, null))
        assertSame(previous, storyViewerEntryUpdate(previous, previous.copy(stories = emptyList())))

        val updated = previous.copy(stories = listOf(preview(2), preview(3)))
        assertSame(updated, storyViewerEntryUpdate(previous, updated))
    }

    @Test
    fun pinnedStorySurvivesAMissingOrPartialList() {
        val pinned = preview(2)

        assertSame(pinned, storyViewerPinnedStory(emptyList(), pinned, initialStoryId = 3, maxReadStoryId = 0))
        assertSame(
            pinned,
            storyViewerPinnedStory(listOf(preview(1), preview(3)), pinned, initialStoryId = 3, maxReadStoryId = 0)
        )

        assertEquals(
            3,
            storyViewerPinnedStory(listOf(preview(1), preview(3)), null, initialStoryId = 3, maxReadStoryId = 0)?.storyId
        )
        assertEquals(
            1,
            storyViewerPinnedStory(listOf(preview(1), preview(3)), null, initialStoryId = 0, maxReadStoryId = 1)?.storyId
        )
    }

    @Test
    fun removalConfirmationRequiresAnAttachedViewerWithACachedAuthor() {
        assertTrue(needsStoryRemovalConfirmation(hasSnapshot = true, authorStillListed = false, connected = true))
        assertFalse(needsStoryRemovalConfirmation(hasSnapshot = false, authorStillListed = false, connected = true))
        assertFalse(needsStoryRemovalConfirmation(hasSnapshot = true, authorStillListed = true, connected = true))
        assertFalse(needsStoryRemovalConfirmation(hasSnapshot = true, authorStillListed = false, connected = false))
    }

    @Test
    fun emptyPhaseShowsOfflineWithoutAnEndlessSpinner() {
        assertEquals(
            StoryViewerEmptyPhase.Offline,
            storyViewerEmptyPhase(TelegramConnectionState.WaitingForNetwork, timedOut = false)
        )
        assertEquals(
            StoryViewerEmptyPhase.Loading,
            storyViewerEmptyPhase(TelegramConnectionState.Connecting, timedOut = false)
        )
        assertEquals(
            StoryViewerEmptyPhase.Offline,
            storyViewerEmptyPhase(TelegramConnectionState.Connecting, timedOut = true)
        )
        assertEquals(
            StoryViewerEmptyPhase.Loading,
            storyViewerEmptyPhase(TelegramConnectionState.Connected, timedOut = false)
        )
        assertEquals(
            StoryViewerEmptyPhase.Unavailable,
            storyViewerEmptyPhase(TelegramConnectionState.Connected, timedOut = true)
        )
        assertEquals(
            StoryViewerEmptyPhase.Offline,
            storyViewerEmptyPhase(TelegramConnectionState.Unknown, timedOut = true)
        )
    }

    @Test
    fun oneEmptyResponseIsNotEnoughToCloseTheViewer() = runBlocking {
        var calls = 0
        val result = confirmStoryListState(attempts = 2, pauseMillis = 0L) {
            calls++
            if (calls == 1) Result.success(emptySet()) else Result.success(setOf(4))
        }

        assertEquals(StoryListConfirmation.Present(setOf(4)), result)
        assertEquals(2, calls)
        assertFalse(result is StoryListConfirmation.Gone)
    }

    @Test
    fun twoConfirmedEmptyResponsesCloseTheViewer() = runBlocking {
        var calls = 0
        val result = confirmStoryListState(attempts = 2, pauseMillis = 0L) {
            calls++
            Result.success(emptySet())
        }

        assertEquals(StoryListConfirmation.Gone, result)
        assertEquals(2, calls)
    }

    @Test
    fun failedRemovalCheckNeverClosesTheViewer() = runBlocking {
        assertEquals(
            StoryListConfirmation.Unknown,
            confirmStoryListState(attempts = 2, pauseMillis = 0L) {
                Result.failure(IllegalStateException("network"))
            }
        )

        var calls = 0
        assertEquals(
            StoryListConfirmation.Unknown,
            confirmStoryListState(attempts = 2, pauseMillis = 0L) {
                calls++
                if (calls == 1) Result.success(emptySet()) else Result.failure(IllegalStateException("network"))
            }
        )
        assertEquals(2, calls)
    }
}
