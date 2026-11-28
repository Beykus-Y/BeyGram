package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CustomEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdCustomEmojisTest {
    private val emoji = CustomEmoji(7L, 1, "🙂", "image/webp")

    @Test
    fun observerGetsTheEmojiWhenTdLibAnswersAfterTheRetryBudget() = runTest {
        var online = false
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val emojis = TdCustomEmojis(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                lookup = { if (online) CustomEmojiAnswer.Found(emoji) else CustomEmojiAnswer.Unanswered },
            )
            val observed = emojis.observe(emoji.customEmojiId)

            // Пауз повтора 2 + 5 + 15 + 30 = 52 с: до конца бюджета сеть не отвечает, наблюдатель ждёт.
            advanceTimeBy(60_000L)
            assertNull(observed.value)

            online = true
            advanceTimeBy(60_000L)
            assertEquals(emoji, observed.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun answeredEmojiIsNotAskedAgain() = runTest {
        var asks = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val emojis = TdCustomEmojis(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                lookup = {
                    asks++
                    CustomEmojiAnswer.Found(emoji)
                },
            )
            val first = emojis.observe(emoji.customEmojiId)
            advanceTimeBy(10_000L)
            assertEquals(emoji, first.value)
            assertEquals(emoji, emojis.observe(emoji.customEmojiId).value)
            assertEquals(1, asks)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun emptyAnswerIsAskedOnlyWithinTheRetryBudget() = runTest {
        var asks = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val emojis = TdCustomEmojis(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                lookup = {
                    asks++
                    CustomEmojiAnswer.Missing
                },
            )
            val observed = emojis.observe(emoji.customEmojiId)

            // Первый запрос и четыре повтора по бюджету пауз: 2, 5, 15 и 30 с. Дольше пустой id не спрашивается.
            advanceTimeBy(600_000L)
            assertNull(observed.value)
            assertEquals(5, asks)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun coalescesConcurrentObserversIntoSingleBatchAndSplitsLargeBursts() = runTest {
        val batches = mutableListOf<List<Long>>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val emojis = TdCustomEmojis(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                batchLookup = { ids ->
                    batches += ids.toList()
                    ids.associateWith { id ->
                        CustomEmojiAnswer.Found(CustomEmoji(id, id.toInt(), "🙂", "application/x-tgsticker"))
                    }
                }
            )
            val observed = (1L..120L).map { emojis.observe(it) }
            advanceTimeBy(100L)

            assertEquals(listOf(100, 20), batches.map { it.size })
            observed.forEachIndexed { index, state ->
                assertEquals((index + 1).toLong(), state.value?.customEmojiId)
            }

            emojis.observe(42L)
            advanceTimeBy(100L)
            assertEquals(2, batches.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun partialMissingAndUnansweredRetryIndependentlyWithinBatch() = runTest {
        var online = false
        val askedCounts = HashMap<Long, Int>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val emojis = TdCustomEmojis(
                client = { error("the lookup is replaced in this test") },
                scope = scope,
                batchLookup = { ids ->
                    ids.associateWith { id ->
                        askedCounts[id] = (askedCounts[id] ?: 0) + 1
                        when (id) {
                            1L -> CustomEmojiAnswer.Found(CustomEmoji(1L, 1, "🙂", "image/webp"))
                            2L -> CustomEmojiAnswer.Missing
                            else -> if (online) CustomEmojiAnswer.Found(CustomEmoji(3L, 3, "🎉", "image/webp")) else CustomEmojiAnswer.Unanswered
                        }
                    }
                }
            )
            val found = emojis.observe(1L)
            val missing = emojis.observe(2L)
            val unanswered = emojis.observe(3L)

            advanceTimeBy(100L)
            assertEquals(1L, found.value?.customEmojiId)
            assertNull(missing.value)
            assertNull(unanswered.value)

            advanceTimeBy(60_000L)
            online = true
            advanceTimeBy(60_000L)
            assertEquals(3L, unanswered.value?.customEmojiId)
            assertEquals(1, askedCounts[1L])
            assertEquals(5, askedCounts[2L])
        } finally {
            scope.cancel()
        }
    }
}
