package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

/** Grapheme segmentation uses Android ICU; Robolectric provides it on the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentEmojiRecoveryTest {
    private fun message(text: String, entities: List<TdApi.TextEntity> = emptyList(), outgoing: Boolean = true): TdApi.Message =
        TdApi.Message().apply {
            isOutgoing = outgoing
            content = TdApi.MessageText().apply { this.text = TdApi.FormattedText(text, entities.toTypedArray()) }
        }

    private fun entity(offset: Int, length: Int, customEmojiId: Long) =
        TdApi.TextEntity(offset, length, TdApi.TextEntityTypeCustomEmoji(customEmojiId))

    @Test
    fun ownTextAndCaptionsGiveUnicodeAndCustomEmoji() {
        val text = "\u2764\uFE0F \uD83C\uDF89" // РІСњВ¤РїС‘РЏ СЂСџР‹вЂ°
        val custom = "x\uD83D\uDC68\u200D\uD83D\uDCBB" // xСЂСџвЂРЃРІР‚РЊСЂСџвЂ™В»; the fallback glyph belongs to a custom entity
        val extracted = extractRecentEmoji(text, emptyList()) // no custom entities
        assertEquals(listOf("\u2764\uFE0F", "\uD83C\uDF89"), extracted.unicode)

        val withCustom = extractRecentEmoji(custom, listOf(entity(1, 5, 42L)))
        assertEquals(listOf<Long>(42L), withCustom.customEmojiIds)
        assertTrue(withCustom.unicode.isEmpty())
    }

    @Test
    fun aKeycapIsAnEmojiButADigitIsNot() {
        assertEquals(listOf("#\u20E3\uFE0F"), extractRecentEmoji("#\u20E3\uFE0F", emptyList()).unicode)
        assertTrue(extractRecentEmoji("123", emptyList()).unicode.isEmpty())
    }

    @Test
    fun secretChatsAndIncomingMessagesAreSkipped() = runBlocking {
        val source = FakeSource(
            chats = listOf(1L, 2L),
            secret = setOf(1L),
            messages = mapOf(
                1L to listOf(message("\uD83D\uDE00")), // СЂСџВР‚ in a secret chat
                2L to listOf(
                    message("\uD83D\uDE00", outgoing = false),
                    message("\uD83C\uDF89")
                )
            )
        )
        val history = RecentEmojiRecovery(source) { 0L }.recover()
        assertEquals(listOf("\uD83C\uDF89"), history.unicode)
    }

    @Test
    fun limitsAreHonouredAndDuplicatesCollapse() = runBlocking {
        val source = FakeSource(
            chats = listOf(1L, 2L),
            messages = mapOf(
                1L to listOf(message("\uD83C\uDF89 \uD83C\uDF89 \u2764\uFE0F")),
                2L to listOf(message("\uD83D\uDE00 \uD83C\uDF89"))
            )
        )
        val history = RecentEmojiRecovery(source) { 0L }.recover(unicodeLimit = 2, customLimit = 0)
        assertEquals(listOf("\uD83C\uDF89", "\u2764\uFE0F"), history.unicode)
    }

    @Test
    fun aFailedReadIsNotFatal() = runBlocking {
        val source = object : RecentEmojiSource {
            override suspend fun meId(): Long = 7L
            override suspend fun chatIds(limit: Int): List<Long> = listOf(1L, 2L)
            override suspend fun chatIsSecret(chatId: Long): Boolean = chatId == 1L
            override suspend fun ownMessages(chatId: Long, senderId: Long, limit: Int): List<TdApi.Message> =
                throw IllegalStateException("offline")
        }
        assertTrue(RecentEmojiRecovery(source) { 0L }.recover().isEmpty)
    }

    @Test
    fun theWallClockBudgetStopsTheScan() = runBlocking {
        var now = 0L
        val source = object : RecentEmojiSource {
            override suspend fun meId(): Long = 7L
            override suspend fun chatIds(limit: Int): List<Long> = (1L..10L).toList()
            override suspend fun chatIsSecret(chatId: Long): Boolean {
                now += 2_000L
                return false
            }
            override suspend fun ownMessages(chatId: Long, senderId: Long, limit: Int): List<TdApi.Message> =
                listOf(message(listOf("\uD83C\uDF89", "\uD83D\uDE00", "\u2764\uFE0F", "\uD83D\uDD25")[chatId.toInt() - 1]))
        }
        // After four chats the 8 s budget is spent; later chats are not read, but the answer is still useful.
        val history = RecentEmojiRecovery(source) { now }.recover()
        assertEquals(4, history.unicode.size)
    }

    private class FakeSource(
        private val chats: List<Long>,
        private val secret: Set<Long> = emptySet(),
        private val messages: Map<Long, List<TdApi.Message>> = emptyMap()
    ) : RecentEmojiSource {
        override suspend fun meId(): Long = 7L
        override suspend fun chatIds(limit: Int): List<Long> = chats.take(limit)
        override suspend fun chatIsSecret(chatId: Long): Boolean = chatId in secret
        override suspend fun ownMessages(chatId: Long, senderId: Long, limit: Int): List<TdApi.Message> =
            messages[chatId].orEmpty().take(limit)
    }
}