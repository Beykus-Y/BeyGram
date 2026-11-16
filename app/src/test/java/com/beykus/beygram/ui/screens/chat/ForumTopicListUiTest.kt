package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.graphics.Color
import com.beykus.beygram.domain.model.ForumTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ForumTopicListUiTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    private val locale = Locale.US

    @Test fun unreadFilterKeepsMentionsReactionsAndPollVotes() {
        val topics = listOf(
            ForumTopic(2L, "Read"),
            ForumTopic(3L, "Message", unreadCount = 1),
            ForumTopic(4L, "Mention", unreadMentionCount = 1),
            ForumTopic(5L, "Reaction", unreadReactionCount = 2),
            ForumTopic(6L, "Poll", unreadPollVoteCount = 3)
        )
        assertEquals(listOf(3L, 4L, 5L, 6L), filteredForumTopics(topics, unreadOnly = true).map { it.id })
        assertEquals(topics, filteredForumTopics(topics, unreadOnly = false))
    }

    @Test fun serverIconColourIsRgbNotArgb() {
        val colour = forumTopicIconColor(0x6FB9F0)
        assertNotNull(colour)
        assertEquals(1f, colour!!.alpha)
        assertEquals(0.43529412f, colour.red, 0.001f)
        assertEquals(0.7254902f, colour.green, 0.001f)
        assertEquals(0.9411765f, colour.blue, 0.001f)
        assertNull(forumTopicIconColor(0))
    }

    @Test fun blackAndWhiteInkAlwaysHaveReadableContrast() {
        assertEquals(21f, forumTopicContrastRatio(Color.Black, Color.White), 0.001f)
        assertEquals(Color.Black, forumTopicIconInk(Color.White))
        assertEquals(Color.White, forumTopicIconInk(Color.Black))
        assertTrue(forumTopicContrastRatio(forumTopicIconInk(Color(0xFF6FB9F0)), Color(0xFF6FB9F0)) >= 4.5f)
    }

    @Test fun compactMomentUsesTimeTodayYesterdayAndDateOlder() {
        val today = forumTopicMoment(Instant.parse("2026-11-16T09:30:00Z").epochSecond, now, zone, locale)
        assertTrue(today is ForumTopicMoment.Clock)
        assertTrue((today as ForumTopicMoment.Clock).text.isNotBlank())

        val yesterday = forumTopicMoment(Instant.parse("2026-11-15T23:30:00Z").epochSecond, now, zone, locale)
        assertEquals(ForumTopicMoment.Yesterday, yesterday)

        val older = forumTopicMoment(Instant.parse("2026-11-10T09:30:00Z").epochSecond, now, zone, locale)
        assertTrue(older is ForumTopicMoment.Date)
        assertTrue((older as ForumTopicMoment.Date).text.isNotBlank())
    }

    @Test fun unknownTimeHasNoLabel() {
        assertEquals(ForumTopicMoment.Empty, forumTopicMoment(0L, now, zone, locale))
        assertEquals(ForumTopicMoment.Empty, forumTopicMoment(-1L, now, zone, locale))
    }

    @Test fun generalTopicCannotBeDeletedEvenByItsManager() {
        assertFalse(canDeleteForumTopic(ForumTopic(1L, "General", isGeneral = true, canManage = true)))
        assertTrue(canDeleteForumTopic(ForumTopic(2L, "Regular", canManage = true)))
        assertFalse(canDeleteForumTopic(ForumTopic(3L, "Read only", canManage = false)))
    }
}
