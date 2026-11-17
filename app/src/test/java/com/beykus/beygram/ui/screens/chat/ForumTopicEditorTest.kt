package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForumTopicIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test fun premiumRowsShowEachSetsCoversUntilItsEmojiArrive() {
        val sets = listOf(CustomEmojiSetBrief(1L, "Cats", listOf(10L)), CustomEmojiSetBrief(2L, "Dogs", listOf(20L)))
        assertEquals(
            listOf(
                PremiumEmojiRow.Header(1L, "Cats"), PremiumEmojiRow.Emoji(1L, 10L, 1),
                PremiumEmojiRow.Header(2L, "Dogs"), PremiumEmojiRow.Emoji(2L, 20L, 1)
            ),
            premiumEmojiRows(sets, emptyMap())
        )
        assertEquals(
            listOf(
                PremiumEmojiRow.Header(1L, "Cats"), PremiumEmojiRow.Emoji(1L, 10L, 1),
                PremiumEmojiRow.Header(2L, "Dogs"), PremiumEmojiRow.Emoji(2L, 21L, 1), PremiumEmojiRow.Emoji(2L, 22L, 2)
            ),
            premiumEmojiRows(sets, mapOf(2L to CustomEmojiSetState.Loaded(listOf(21L, 22L))))
        )
    }

    @Test fun failedSetKeepsItsCoversAndRowKeysStayUnique() {
        val sets = listOf(CustomEmojiSetBrief(1L, "Cats", listOf(10L, 11L)), CustomEmojiSetBrief(1L, "Cats", listOf(10L, 11L)))
        val rows = premiumEmojiRows(sets, mapOf(1L to CustomEmojiSetState.Failed))
        assertEquals(listOf("set-1", "emoji-1-10", "emoji-1-11"), rows.map { it.key })
    }

    @Test fun aSetIsAskedOnceWhileLoadingAndKeepsItsEmojiAfterwards() = runTest {
        var asks = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val loader = CustomEmojiSetLoader(scope) {
                asks++
                Result.success(listOf(5L, 6L))
            }
            loader.request(3L)
            loader.request(3L)
            advanceUntilIdle()
            loader.request(3L)
            advanceUntilIdle()
            assertEquals(1, asks)
            assertEquals(CustomEmojiSetState.Loaded(listOf(5L, 6L)), loader.states.value[3L])
        } finally {
            scope.cancel()
        }
    }

    @Test fun noMoreThanTwoSetsLoadAtOnce() = runTest {
        var running = 0
        var peak = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val loader = CustomEmojiSetLoader(scope) { setId ->
                running++
                peak = maxOf(peak, running)
                delay(1_000L)
                running--
                Result.success(listOf(setId))
            }
            repeat(5) { loader.request(it.toLong()) }
            advanceUntilIdle()
            assertEquals(2, peak)
            assertEquals(5, loader.states.value.values.count { it is CustomEmojiSetState.Loaded })
        } finally {
            scope.cancel()
        }
    }

    @Test fun aFailedSetIsAskedAgainWhenItsTitleComesBackIntoView() = runTest {
        var asks = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val loader = CustomEmojiSetLoader(scope) {
                asks++
                if (asks == 1) Result.failure<List<Long>>(IllegalStateException("offline")) else Result.success(listOf(9L))
            }
            loader.request(4L)
            advanceUntilIdle()
            assertEquals(CustomEmojiSetState.Failed, loader.states.value[4L])
            loader.request(4L)
            advanceUntilIdle()
            assertEquals(CustomEmojiSetState.Loaded(listOf(9L)), loader.states.value[4L])
            assertEquals(2, asks)
        } finally {
            scope.cancel()
        }
    }
}
