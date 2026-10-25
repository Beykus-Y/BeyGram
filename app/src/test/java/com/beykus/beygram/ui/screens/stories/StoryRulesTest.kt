package com.beykus.beygram.ui.screens.stories

import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.StoryCropRect
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryMedia
import com.beykus.beygram.domain.model.StoryMediaKind
import com.beykus.beygram.domain.model.StoryPostDenial
import com.beykus.beygram.domain.model.StoryPostEvent
import com.beykus.beygram.domain.model.StoryPostTarget
import com.beykus.beygram.domain.model.StoryPreview
import com.beykus.beygram.domain.model.matchesPendingStoryEvent
import com.beykus.beygram.domain.model.orderedStoryBar
import com.beykus.beygram.domain.model.orderedStoryPostTargets
import com.beykus.beygram.domain.model.storyActivePeriodChoices
import com.beykus.beygram.domain.model.storyCropRect
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
    fun liveListUpdateKeepsTheVisibleStoryById() {
        val before = listOf(preview(1), preview(2), preview(3))

        assertEquals(1, storyIndexAfterUpdate(before, previousIndex = 1, visibleStoryId = 2))
        assertEquals(0, storyIndexAfterUpdate(listOf(preview(2), preview(3)), previousIndex = 1, visibleStoryId = 2))
        // The visible story was removed in the middle: show the next one, not the old numeric index.
        assertEquals(1, storyIndexAfterUpdate(listOf(preview(1), preview(3)), previousIndex = 1, visibleStoryId = 2))
        assertEquals(-1, storyIndexAfterUpdate(listOf(preview(1)), previousIndex = 1, visibleStoryId = 2))
        assertEquals(-1, storyIndexAfterUpdate(emptyList(), previousIndex = 1, visibleStoryId = 2))
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
    fun fractionalVideoDurationKeepsSubSecondPrecision() {
        val media = StoryMedia(
            StoryMediaKind.Video,
            fileId = 10,
            thumbnailFileId = 11,
            durationSeconds = 1,
            durationMillis = 1_900L
        )
        assertEquals(1_900L, storyDurationMillis(StoryDetail(1, 1, 0, media = media)))
    }

    @Test
    fun pendingTicketIgnoresFailuresOfOlderUploads() {
        val olderFailure = StoryPostEvent.Failed(posterChatId = 7, storyId = 10, reason = StoryPostDenial.Unavailable)
        assertFalse(matchesPendingStoryEvent(olderFailure, posterChatId = 7, storyId = 11))
        assertTrue(matchesPendingStoryEvent(olderFailure, posterChatId = 7, storyId = 10))
        assertFalse(matchesPendingStoryEvent(olderFailure, posterChatId = 8, storyId = 10))
    }

    @Test
    fun publishedEventMatchesTemporaryOrFinalStoryId() {
        val published = StoryPostEvent.Published(posterChatId = 7, storyId = 12, oldStoryId = 11)
        assertTrue(matchesPendingStoryEvent(published, posterChatId = 7, storyId = 11))
        assertTrue(matchesPendingStoryEvent(published, posterChatId = 7, storyId = 12))
        assertFalse(matchesPendingStoryEvent(published, posterChatId = 7, storyId = 13))
        assertFalse(matchesPendingStoryEvent(published, posterChatId = 8, storyId = 11))
    }

    @Test
    fun storyPostTargetsPutSelfFirstThenSortChannels() {
        val targets = listOf(
            StoryPostTarget(chatId = 20, title = "Zeta"),
            StoryPostTarget(chatId = 7, title = "Me", isSelf = true),
            StoryPostTarget(chatId = 10, title = "Alpha")
        )
        assertEquals(listOf(7L, 10L, 20L), orderedStoryPostTargets(targets).map { it.chatId })
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
    fun storyCropIsNineBySixteen() {
        assertEquals(StoryCropRect(0, 0, 1080, 1920), storyCropRect(1080, 1920))
        val landscape = storyCropRect(4000, 3000)
        assertEquals(1687, landscape.width)
        assertEquals(3000, landscape.height)
        assertEquals(1156, landscape.left)
        val tall = storyCropRect(1000, 2000)
        assertEquals(1000, tall.width)
        assertEquals(1777, tall.height)
        assertEquals(111, tall.top)
        assertEquals(StoryCropRect(0, 0, 0, 0), storyCropRect(0, 100))
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
