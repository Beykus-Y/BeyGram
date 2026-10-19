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
}
