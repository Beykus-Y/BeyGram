package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForumTopicIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumTopicEditorTest {
    @Test fun nameNeedsTextAndAtMostTheTdlibLimitInCodePoints() {
        assertFalse(isForumTopicNameValid(""))
        assertFalse(isForumTopicNameValid("   "))
        assertTrue(isForumTopicNameValid(" a "))
        assertTrue(isForumTopicNameValid("a".repeat(ForumTopicNameLimit)))
        assertFalse(isForumTopicNameValid("a".repeat(ForumTopicNameLimit + 1)))
        // An emoji is two UTF-16 units but one code point, so 128 of them still fit.
        val smiley = "😀"
        assertTrue(isForumTopicNameValid(smiley.repeat(ForumTopicNameLimit)))
        assertFalse(isForumTopicNameValid(smiley.repeat(ForumTopicNameLimit + 1)))
    }

    @Test fun iconIsSentOnlyWhenItChanges() {
        val withEmoji = ForumTopic(2L, "Topic", icon = ForumTopicIcon(colorArgb = 0x6FB9F0, customEmojiId = 42L))
        assertNull(forumTopicIconUpdate(withEmoji, selectedEmojiId = 42L))
        assertEquals(43L, forumTopicIconUpdate(withEmoji, selectedEmojiId = 43L))
        assertEquals(0L, forumTopicIconUpdate(withEmoji, selectedEmojiId = 0L))
        // A topic without a custom emoji that keeps "no icon" is unchanged as well.
        assertNull(forumTopicIconUpdate(ForumTopic(3L, "Bare"), selectedEmojiId = 0L))
    }

    @Test fun generalTopicNeverChangesItsIcon() {
        val general = ForumTopic(1L, "General", isGeneral = true, icon = ForumTopicIcon(colorArgb = 0x6FB9F0, customEmojiId = 7L))
        assertNull(forumTopicIconUpdate(general, selectedEmojiId = 0L))
        assertNull(forumTopicIconUpdate(general, selectedEmojiId = 9L))
    }

    @Test fun iconGridStartsWithNoIconAndKeepsTheTopicsOwnEmoji() {
        assertEquals(listOf(0L, 5L, 6L), forumTopicIconOptions(current = 0L, defaults = listOf(5L, 6L)))
        assertEquals(listOf(0L, 9L, 5L, 6L), forumTopicIconOptions(current = 9L, defaults = listOf(5L, 6L)))
        assertEquals(listOf(0L, 5L, 6L), forumTopicIconOptions(current = 5L, defaults = listOf(5L, 6L)))
    }
}
