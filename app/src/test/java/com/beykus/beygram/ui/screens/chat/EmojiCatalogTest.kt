package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test
    fun catalogHasTheEightCategoriesInOrder() {
        assertEquals(
            listOf("smileys", "animals", "food", "activity", "travel", "objects", "symbols", "flags"),
            EmojiCatalog.groups.map { it.id }
        )
    }

    @Test
    fun everyCategoryIsAModestSetWithoutRepeatsOrBlanks() {
        EmojiCatalog.groups.forEach { group ->
            assertTrue("${group.id} has ${group.emoji.size} emoji", group.emoji.size in 40..120)
            assertEquals("${group.id} repeats an emoji", group.emoji.size, group.emoji.distinct().size)
            assertTrue("${group.id} has a blank entry", group.emoji.none { it.isBlank() })
        }
    }

    @Test
    fun recentSectionLeadsOnlyWhenThereIsSomethingToShow() {
        assertEquals(EmojiCatalog.groups, EmojiCatalog.withRecent(emptyList()))

        val withRecent = EmojiCatalog.withRecent(listOf("😀", "🎉"))
        assertEquals(RECENT_EMOJI_GROUP_ID, withRecent.first().id)
        assertEquals(listOf("😀", "🎉"), withRecent.first().emoji)
        assertEquals(EmojiCatalog.groups, withRecent.drop(1))
    }

    @Test
    fun aPickedEmojiMovesToTheFrontWithoutDuplicates() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").withRecentEmoji("b"))
        assertEquals(listOf("z", "a"), listOf("a").withRecentEmoji("z"))
    }

    @Test
    fun recentEmojiAreCappedAtTheLimitKeepingTheNewest() {
        val existing = (1..RECENT_EMOJI_LIMIT).map { "e$it" }
        val updated = existing.withRecentEmoji("new")

        assertEquals(RECENT_EMOJI_LIMIT, updated.size)
        assertEquals("new", updated.first())
        assertFalse(updated.contains("e$RECENT_EMOJI_LIMIT"))
    }
}
