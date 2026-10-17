package com.beykus.beygram.diagnostics.report

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotReplyCorrelatorTest {

    private val botUserId = 8841599020L
    private val chatId = 8841599020L

    private fun correlator() = BotReplyCorrelator(botUserId, chatId)

    private fun botReply(replyToMessageId: Long, text: String, senderId: Long = botUserId, inChatId: Long = chatId): TdApi.Message {
        val content = TdApi.MessageText().apply {
            this.text = TdApi.FormattedText(text, arrayOf())
        }
        return TdApi.Message().apply {
            id = 555_000L
            this.chatId = inChatId
            this.senderId = TdApi.MessageSenderUser(senderId)
            this.content = content
            replyTo = TdApi.MessageReplyToMessage().apply { messageId = replyToMessageId; this.chatId = inChatId; pollOptionId = "" }
        }
    }

    private fun confirmedText() =
        "Запрос подтверждён.\ncorrelation_id: 0123456789abcdef\n\nВернитесь в BeyGram."

    @Test
    fun `valid reply from the same bot chat confirms`() {
        val c = correlator()
        c.trackSent(42L)
        c.onSendSucceeded(42L, 100L)
        val verdict = c.onIncoming(botReply(100L, confirmedText()))
        assertTrue(verdict is BotReplyCorrelator.Verdict.Confirmed)
        assertEquals("0123456789abcdef", (verdict as BotReplyCorrelator.Verdict.Confirmed).correlationId)
    }

    @Test
    fun `reply to the pending message id confirms`() {
        val c = correlator()
        c.trackSent(42L)
        val verdict = c.onIncoming(botReply(42L, confirmedText()))
        assertTrue(verdict is BotReplyCorrelator.Verdict.Confirmed)
    }

    @Test
    fun `outgoing send alone never confirms`() {
        val c = correlator()
        c.trackSent(42L)
        c.onSendSucceeded(42L, 100L)
        // Никаких входящих: подтверждения нет.
        assertNull(c.onIncoming(botReply(100L, confirmedText(), senderId = 111L)))
    }

    @Test
    fun `spoofed sender is rejected`() {
        val c = correlator()
        c.trackSent(42L)
        assertNull(c.onIncoming(botReply(42L, confirmedText(), senderId = 999L)))
    }

    @Test
    fun `foreign chat is rejected`() {
        val c = correlator()
        c.trackSent(42L)
        assertNull(c.onIncoming(botReply(42L, confirmedText(), inChatId = -777L)))
    }

    @Test
    fun `reply to someone else's message is rejected`() {
        val c = correlator()
        c.trackSent(42L)
        assertNull(c.onIncoming(botReply(7777L, confirmedText())))
    }

    @Test
    fun `message without reply chain is rejected`() {
        val c = correlator()
        c.trackSent(42L)
        val message = botReply(42L, confirmedText()).apply { replyTo = null }
        assertNull(c.onIncoming(message))
    }

    @Test
    fun `unrelated bot texts are ignored`() {
        val c = correlator()
        c.trackSent(42L)
        assertNull(c.onIncoming(botReply(42L, "Привет! Чем могу помочь?")))
    }

    @Test
    fun `stale confirmation from a previous session does not confirm a new nonce`() {
        val c = correlator()
        c.trackSent(42L)
        c.onSendSucceeded(42L, 100L)
        c.reset() // новая сессия: новый nonce
        c.trackSent(999L)
        // Старый ответ ссылается на старое наше сообщение — больше не в наборе.
        assertNull(c.onIncoming(botReply(100L, confirmedText())))
    }

    @Test
    fun `reply in another chat of the same bot is rejected`() {
        val c = correlator()
        c.trackSent(42L)
        // Письмо от того же бота, но из другого чата (бот может писать в группы).
        assertNull(c.onIncoming(botReply(42L, confirmedText(), inChatId = -100200300L)))
    }

    @Test
    fun `redelivered confirmation is consumed once`() {
        val c = correlator()
        c.trackSent(42L)
        val first = c.onIncoming(botReply(42L, confirmedText()))
        assertTrue(first is BotReplyCorrelator.Verdict.Confirmed)
        val second = c.onIncoming(botReply(42L, confirmedText()))
        assertNull(second)
    }

    @Test
    fun `refusals surface as verdicts, not confirmations`() {
        val c = correlator()
        c.trackSent(42L)
        val verdict = c.onIncoming(botReply(42L, "Этот аккаунт не в списке участников беты. Обратитесь к разработчику BeyGram."))
        assertTrue(verdict is BotReplyCorrelator.Verdict.Refusal)
        assertTrue((verdict as BotReplyCorrelator.Verdict.Refusal).reply is BotReply.NotAllowed)
    }

    @Test
    fun `send failure removes the tracked id`() {
        val c = correlator()
        c.trackSent(42L)
        c.onSendFailed(42L)
        assertNull(c.onIncoming(botReply(42L, confirmedText())))
    }

    @Test
    fun `send success that arrives before tracking still confirms the final id`() {
        val c = correlator()
        c.onSendSucceeded(42L, 100L)
        c.trackSent(42L)
        val verdict = c.onIncoming(botReply(100L, confirmedText()))
        assertTrue(verdict is BotReplyCorrelator.Verdict.Confirmed)
    }

    @Test
    fun `ownership covers only the tracked command`() {
        val c = correlator()
        c.trackSent(42L)
        assertTrue(c.owns(42L))
        assertFalse(c.owns(7L))
        c.onSendSucceeded(42L, 100L)
        assertFalse(c.owns(42L))
    }
}
