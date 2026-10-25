package com.beykus.beygram.ui.screens.stories

import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryMedia
import com.beykus.beygram.domain.model.StoryMediaKind
import com.beykus.beygram.domain.model.StoryPostEvent
import com.beykus.beygram.domain.model.StoryPreview

/** A file the viewer may ask TDLib to download before or while a story is shown. */
data class StoryPrefetchTarget(val fileId: Int, val fullSize: Boolean)

/** Upper bound for the files requested around one story: full current, its preview and two neighbor previews. */
const val MAX_STORY_PREFETCH_FILES = 4

private const val PHOTO_STORY_DURATION_MS = 5_000L
private const val MAX_VIDEO_STORY_DURATION_MS = 60_000L
private const val MIN_STORY_DURATION_MS = 1_000L

/**
 * The viewer downloads only what it can display next: the current story plus small previews of its
 * neighbors. A neighbor's full video/photo is requested only after the viewer actually moves to it.
 */
fun storyPrefetchTargets(
    current: StoryMedia?,
    previous: StoryMedia?,
    next: StoryMedia?
): List<StoryPrefetchTarget> {
    val targets = linkedMapOf<Int, StoryPrefetchTarget>()
    fun add(fileId: Int?, fullSize: Boolean) {
        if (fileId == null || fileId <= 0) return
        val existing = targets[fileId]
        if (existing == null || (fullSize && !existing.fullSize)) {
            targets[fileId] = StoryPrefetchTarget(fileId, fullSize)
        }
    }
    current?.let {
        add(it.thumbnailFileId ?: it.fileId, false)
        add(it.fileId, true)
    }
    add(previous?.thumbnailFileId ?: previous?.let { if (it.kind == StoryMediaKind.Photo) it.fileId else null }, false)
    add(next?.thumbnailFileId ?: next?.let { if (it.kind == StoryMediaKind.Photo) it.fileId else null }, false)
    return targets.values.take(MAX_STORY_PREFETCH_FILES)
}

/** Index to open in the viewer: the requested story, otherwise the first unseen story, otherwise the first one. */
fun storyInitialIndex(stories: List<StoryPreview>, maxReadStoryId: Int, initialStoryId: Int): Int {
    if (stories.isEmpty()) return -1
    if (initialStoryId > 0) {
        val requested = stories.indexOfFirst { it.storyId == initialStoryId }
        if (requested >= 0) return requested
    }
    val firstUnread = stories.indexOfFirst { it.storyId > maxReadStoryId }
    return if (firstUnread >= 0) firstUnread else 0
}

/** Telegram keeps photos for five seconds; video length is its own, with a sane fallback when metadata is missing. */
fun storyDurationMillis(story: StoryDetail?): Long {
    val media = story?.media ?: return PHOTO_STORY_DURATION_MS
    return when (media.kind) {
        StoryMediaKind.Photo -> PHOTO_STORY_DURATION_MS
        StoryMediaKind.Video -> {
            val precise = media.durationMillis.takeIf { it > 0L } ?: (media.durationSeconds * 1_000L)
            if (precise <= 0L) PHOTO_STORY_DURATION_MS
            else precise.coerceIn(MIN_STORY_DURATION_MS, MAX_VIDEO_STORY_DURATION_MS)
        }
    }
}

/**
 * A pending ticket owns only its own terminal update. `TdStories` replays several events, so a
 * failure of an earlier upload must not clear a newer ticket for the same poster.
 */
fun matchesPendingStoryEvent(event: StoryPostEvent, posterChatId: Long, storyId: Int): Boolean = when (event) {
    is StoryPostEvent.Published -> event.posterChatId == posterChatId &&
        (event.storyId == storyId || event.oldStoryId == storyId)
    is StoryPostEvent.Failed -> event.posterChatId == posterChatId && event.storyId == storyId
}

enum class StoryTapTarget { Previous, Next }

/** Left third goes back, the rest goes forward, matching Telegram's story viewer. */
fun storyTapTarget(x: Float, width: Int): StoryTapTarget {
    if (width <= 0) return StoryTapTarget.Next
    return if (x < width * 0.34f) StoryTapTarget.Previous else StoryTapTarget.Next
}
