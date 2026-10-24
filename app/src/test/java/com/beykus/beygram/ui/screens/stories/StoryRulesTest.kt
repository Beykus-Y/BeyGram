package com.beykus.beygram.ui.screens.stories

import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryMedia
import com.beykus.beygram.domain.model.StoryMediaKind
import com.beykus.beygram.domain.model.StoryPreview
import com.beykus.beygram.domain.model.orderedStoryBar
import com.beykus.beygram.domain.model.storyActivePeriodChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRulesTest {
    private fun preview(id: Int, date: Long = id.toLong()) = StoryPreview(id, date)

    private fun photo(id: Int, thumb: Int? = id + 1) = StoryMedia(StoryMediaKind.Photo, id, thumb)

    private fun video(id: Int, thumb: Int? = id + 1, duration: Int = 12) =
        StoryMedia(StoryMediaKind.Video, id, thumb, durationSeconds = duration)

    @Test
    fun prefetchKeepsCurrentFullFileAndOnlyNeighborPreviews() {
        val targets = storyPrefetchTargets(photo(10), photo(20), video(30))

        assertEquals(listOf(11, 10, 21, 31), targets.map { it.fileId })
        assertTrue(targets.first { it.fileId == 10 }.fullSize)
        assertTrue(targets.first { it.fileId == 11 }.fullSize.not())
        assertFalse(targets.any { it.fileId == 20 && it.fullSize })
        assertFalse(targets.any { it.fileId == 30 && it.fullSize })
        assertTrue(targets.size <= MAX_STORY_PREFETCH_FILES)
    }

    @Test
    fun prefetchDeduplicatesSamePreviewAndFullFile() {
        val targets = storyPrefetchTargets(photo(10, thumb = 10), photo(20, thumb = 10), null)

        assertEquals(listOf(10), targets.map { it.fileId })
        assertTrue(targets.single().fullSize)
    }

    @Test
    fun initialStoryPrefersRequestedThenFirstUnread() {
        val stories = listOf(preview(1), preview(2), preview(3))

        assertEquals(2, storyInitialIndex(stories, maxReadStoryId = 1, initialStoryId = 3))
        assertEquals(1, storyInitialIndex(stories, maxReadStoryId = 1, initialStoryId = 0))
        assertEquals(0, storyInitialIndex(stories, maxReadStoryId = 5, initialStoryId = 0))
        assertEquals(-1, storyInitialIndex(emptyList(), maxReadStoryId = 0, initialStoryId = 0))
    }

    @Test
    fun storyDurationUsesVideoMetadataWithSafeFallbacks() {
        assertEquals(5_000L, storyDurationMillis(null))
        assertEquals(5_000L, storyDurationMillis(StoryDetail(1, 1, 0, media = photo(10))))
        assertEquals(12_000L, storyDurationMillis(StoryDetail(1, 1, 0, media = video(10))))
        assertEquals(5_000L, storyDurationMillis(StoryDetail(1, 1, 0, media = video(10, duration = 0))))
        assertEquals(60_000L, storyDurationMillis(StoryDetail(1, 1, 0, media = video(10, duration = 600))))
    }

    @Test
    fun tapsSplitAtTheLeftThird() {
        assertEquals(StoryTapTarget.Previous, storyTapTarget(0f, 1080))
        assertEquals(StoryTapTarget.Previous, storyTapTarget(300f, 1080))
        assertEquals(StoryTapTarget.Next, storyTapTarget(400f, 1080))
        assertEquals(StoryTapTarget.Previous, storyTapTarget(100f, 1000))
        assertEquals(StoryTapTarget.Next, storyTapTarget(10f, 0))
    }

    @Test
    fun onlyTwentyFourHoursIsFree() {
        assertEquals(listOf(24 * 3600), storyActivePeriodChoices(premium = false))
        assertEquals(listOf(6 * 3600, 12 * 3600, 24 * 3600, 48 * 3600), storyActivePeriodChoices(premium = true))
    }

    @Test
    fun storyBarPutsSelfFirstThenUsesTelegramOrder() {
        val entries = listOf(
            ChatStories(chatId = 5, title = "B", initials = "B", avatarColorArgb = 0, order = 100),
            ChatStories(chatId = 7, title = "Me", initials = "M", avatarColorArgb = 0, order = 1, isSelf = true),
            ChatStories(chatId = 9, title = "C", initials = "C", avatarColorArgb = 0, order = 200),
            ChatStories(chatId = 3, title = "A", initials = "A", avatarColorArgb = 0, order = 100)
        )

        assertEquals(listOf(7L, 9L, 5L, 3L), orderedStoryBar(entries).map { it.chatId })
    }

    @Test
    fun unreadStoryUsesFirstStoryNewerThanReadWatermark() {
        val stories = ChatStories(
            chatId = 1, title = "A", initials = "A", avatarColorArgb = 0,
            stories = listOf(preview(4), preview(5), preview(6)),
            maxReadStoryId = 4
        )

        assertTrue(stories.hasUnread)
        assertEquals(5, stories.firstUnreadStoryId)

        val read = stories.copy(maxReadStoryId = 6)
        assertFalse(read.hasUnread)
        assertEquals(4, read.firstUnreadStoryId)
    }
}
