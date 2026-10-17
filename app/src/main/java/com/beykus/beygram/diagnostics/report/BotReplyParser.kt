package com.beykus.beygram.diagnostics.report

/**
 * Разбор ответов служебного бота по текстам из `app/services/bot_worker.py`.
 * Распознаётся только ожидаемый формат; любой другой текст — [BotReply.Unrelated].
 * Само по себе распознавание НЕ подтверждает отчёт: решение принимает
 * коррелятор, проверяющий отправителя, чат и цепочку ответа.
 */
sealed interface BotReply {
    /** Подтверждение формата «Запрос подтверждён.» + correlation_id из 16 hex-символов. */
    data class Confirmed(val correlationId: String) : BotReply

    /** Аккаунт не в allowlist (`MSG_NOT_ALLOWED`). */
    data object NotAllowed : BotReply

    /** Лимит команд (`MSG_RATE_LIMITED`). */
    data object RateLimited : BotReply

    /** Приём отчётов выключен (`MSG_DISABLED`). */
    data object Disabled : BotReply

    /** Сервер отверг форму nonce (`MSG_BAD_NONCE`, включая конфликт). */
    data object BadNonce : BotReply

    /** Подсказка формата команды (`MSG_NEED_NONCE`, /start, /help). */
    data object NeedNonce : BotReply

    /** Текст не является ответом этого протокола. */
    data object Unrelated : BotReply
}

object BotReplyParser {

    private const val CONFIRMED_MARKER = "Запрос подтверждён"
    private val CORRELATION = Regex("""correlation_id:\s*([0-9a-fA-F]{16})\b""")

    private val NOT_ALLOWED_MARKERS = listOf("не в списке участников", "not allowed")
    private val RATE_LIMITED_MARKERS = listOf("слишком много запросов подтверждения", "rate limit")
    private val DISABLED_MARKERS = listOf("временно приостановлен", "temporarily suspended", "disabled")
    private val BAD_NONCE_MARKERS = listOf("код имеет неверный формат", "неверный формат")
    private val NEED_NONCE_MARKERS = listOf("/report <код>", "/report <code>")

    fun parse(text: String): BotReply {
        val normalized = text.trim()
        if (normalized.isEmpty()) return BotReply.Unrelated
        val lower = normalized.lowercase()
        return when {
            CONFIRMED_MARKER in normalized -> {
                val correlation = CORRELATION.find(normalized)?.groupValues?.get(1)
                // Подтверждением считается только полный формат: фраза + валидный id.
                if (correlation != null) BotReply.Confirmed(correlation.lowercase()) else BotReply.Unrelated
            }
            NOT_ALLOWED_MARKERS.any { it in lower } -> BotReply.NotAllowed
            RATE_LIMITED_MARKERS.any { it in lower } -> BotReply.RateLimited
            DISABLED_MARKERS.any { it in lower } -> BotReply.Disabled
            BAD_NONCE_MARKERS.any { it in lower } -> BotReply.BadNonce
            NEED_NONCE_MARKERS.any { it in lower } -> BotReply.NeedNonce
            else -> BotReply.Unrelated
        }
    }
}
