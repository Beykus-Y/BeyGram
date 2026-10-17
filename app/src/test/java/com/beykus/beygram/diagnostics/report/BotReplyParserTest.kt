package com.beykus.beygram.diagnostics.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotReplyParserTest {

    private fun confirmedText(correlation: String = "0123456789abcdef") =
        "Запрос подтверждён.\n" +
            "correlation_id: $correlation\n\n" +
            "Вернитесь в BeyGram и нажмите «Отправить отчёт». Ссылка действует 7 мин."

    @Test
    fun `full confirmation text parses with correlation id`() {
        val reply = BotReplyParser.parse(confirmedText())
        assertTrue(reply is BotReply.Confirmed)
        assertEquals("0123456789abcdef", (reply as BotReply.Confirmed).correlationId)
    }

    @Test
    fun `confirmation without correlation id is not accepted`() {
        assertTrue(BotReplyParser.parse("Запрос подтверждён. Без идентификатора.") is BotReply.Unrelated)
    }

    @Test
    fun `correlation id with wrong length is not accepted`() {
        assertTrue(BotReplyParser.parse("Запрос подтверждён.\ncorrelation_id: 12345") is BotReply.Unrelated)
    }

    @Test
    fun `refusal texts are classified`() {
        assertTrue(BotReplyParser.parse("Этот аккаунт не в списке участников беты. Обратитесь к разработчику BeyGram, чтобы получить доступ.") is BotReply.NotAllowed)
        assertTrue(BotReplyParser.parse("Слишком много запросов подтверждения. Попробуйте позже.") is BotReply.RateLimited)
        assertTrue(BotReplyParser.parse("Приём отчётов временно приостановлен. Попробуйте позже.") is BotReply.Disabled)
        assertTrue(BotReplyParser.parse("Код имеет неверный формат. Сформируйте новый отчёт в BeyGram и попробуйте снова.") is BotReply.BadNonce)
        assertTrue(BotReplyParser.parse("Отправьте команду в формате:\n/report <код>") is BotReply.NeedNonce)
    }

    @Test
    fun `foreign and empty texts are unrelated`() {
        assertTrue(BotReplyParser.parse("Привет, как дела?") is BotReply.Unrelated)
        assertTrue(BotReplyParser.parse("") is BotReply.Unrelated)
        // Строка correlation_id без фразы подтверждения — не доказательство.
        assertTrue(BotReplyParser.parse("correlation_id: 0123456789abcdef") is BotReply.Unrelated)
        // Старое/поддельное подтверждение с неверным hex не проходит.
        assertTrue(BotReplyParser.parse("Запрос подтверждён.\ncorrelation_id: zzzzzzzzzzzzzzzz") is BotReply.Unrelated)
    }
}
