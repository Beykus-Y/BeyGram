package com.beykus.beygram.ui.screens.stories

import com.beykus.beygram.diagnostics.StructuredEventLog
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.StoryPreview
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.delay

/** Why the viewer was closed. Only the stable code is logged, never chat titles or story IDs. */
enum class StoryViewerCloseReason(val wire: String) {
    Back("back"),
    CloseButton("close_button"),
    Completed("completed"),
    Deleted("deleted"),
    Unavailable("unavailable")
}

/** What the empty viewer should tell the user while the story list has no content. */
enum class StoryViewerEmptyPhase {
    Loading,
    Offline,
    Unavailable
}

fun storyViewerEmptyPhase(
    connection: TelegramConnectionState,
    timedOut: Boolean
): StoryViewerEmptyPhase = when {
    connection == TelegramConnectionState.WaitingForNetwork ||
        connection == TelegramConnectionState.Unknown -> StoryViewerEmptyPhase.Offline
    connection != TelegramConnectionState.Connected && timedOut -> StoryViewerEmptyPhase.Offline
    timedOut -> StoryViewerEmptyPhase.Unavailable
    else -> StoryViewerEmptyPhase.Loading
}

/**
 * A live list update without stories is not proof that the author's stories are gone: TDLib can
 * omit an entry while the list is still loading. Keep the last known metadata until a direct request
 * confirms that the active list is truly empty.
 */
fun storyViewerEntryUpdate(previous: ChatStories?, incoming: ChatStories?): ChatStories? = when {
    incoming == null -> previous
    incoming.stories.isEmpty() && previous != null -> previous
    else -> incoming
}

/**
 * The open story is pinned by its object rather than its index. A partial or empty list update must
 * not reset the viewer, even when the author has disappeared from the visible list for a moment.
 */
fun storyViewerPinnedStory(
    stories: List<StoryPreview>,
    pinned: StoryPreview?,
    initialStoryId: Int,
    maxReadStoryId: Int
): StoryPreview? = pinned ?: stories.getOrNull(storyInitialIndex(stories, maxReadStoryId, initialStoryId))

/** A removal request is useful only while the list is connected and the viewer has a cached entry. */
fun needsStoryRemovalConfirmation(
    hasSnapshot: Boolean,
    authorStillListed: Boolean,
    connected: Boolean
): Boolean = hasSnapshot && !authorStillListed && connected

/**
 * Index to show after [removedStoryId] is confirmed gone. If the story is still in the cached page,
 * its neighbour is next. If it already disappeared from the refreshed page, [previousIndex] now
 * points at its successor; falling back to the first story would send the viewer backwards.
 */
fun storySuccessorIndex(
    stories: List<StoryPreview>,
    removedStoryId: Int,
    previousIndex: Int
): Int? {
    if (stories.isEmpty()) return null
    val at = stories.indexOfFirst { it.storyId == removedStoryId }
    return when {
        at in 0 until stories.lastIndex -> at + 1
        at > 0 -> at - 1
        at == 0 -> null
        previousIndex in stories.indices -> previousIndex
        previousIndex > 0 -> stories.lastIndex
        else -> 0
    }
}

/** Result of asking TDLib whether the author still has active stories. */
sealed interface StoryListConfirmation {
    data object Gone : StoryListConfirmation

    data class Present(val storyIds: Set<Int>) : StoryListConfirmation

    data object Unknown : StoryListConfirmation
}

/**
 * Closes the viewer only after two consecutive successful empty answers from TDLib. A single empty
 * or failed response can be a transient network/cache state, so it keeps the viewer open.
 */
suspend fun confirmStoryListState(
    attempts: Int = 2,
    pauseMillis: Long = 750L,
    loadActiveStoryIds: suspend () -> Result<Set<Int>>
): StoryListConfirmation {
    if (attempts <= 0) return StoryListConfirmation.Unknown
    repeat(attempts) { attempt ->
        val ids = loadActiveStoryIds().getOrElse { return StoryListConfirmation.Unknown }
        if (ids.isNotEmpty()) return StoryListConfirmation.Present(ids)
        if (attempt < attempts - 1 && pauseMillis > 0L) delay(pauseMillis)
    }
    return StoryListConfirmation.Gone
}

internal fun logStoryViewerClose(reason: StoryViewerCloseReason, storyCount: Int) {
    StructuredEventLog.shared.record(
        kind = EventKind.UI,
        severity = Severity.INFO,
        code = "stories_viewer_close",
        message = "Story viewer closed",
        attrs = mapOf(
            "reason" to reason.wire,
            "stories" to storyCount.coerceAtLeast(0).toString()
        )
    )
}
