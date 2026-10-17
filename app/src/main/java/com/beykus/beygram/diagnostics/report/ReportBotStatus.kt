package com.beykus.beygram.diagnostics.report

/** Причина отказа канала подтверждения через бота; UX-текст выбирает UI-слой. */
enum class ReportBotFailure {
    /** Бот не найден по username или Telegram отклонил создание чата. */
    BOT_UNRESOLVED,

    /** User id найденного бота не совпадает с закреплённым в конфигурации. */
    CONFIG_MISMATCH,

    /** Найденный пользователь — не бот. */
    NOT_A_BOT,

    /** Команда не отправлена (нет сети, flood, чат недоступен). */
    SEND_FAILED,

    /** Отправка не завершилась: TDLib сообщил об ошибке отправки. */
    SEND_FAILED_PERMANENT,

    /** Подтверждение не пришло за отведённое время. */
    WAIT_TIMEOUT,

    /** Сервер ответил отказом: аккаунт не в allowlist. */
    REFUSED_NOT_ALLOWED,

    /** Сервер ответил отказом: лимит команд подтверждения. */
    REFUSED_RATE_LIMITED,

    /** Сервер ответил отказом: приём отчётов приостановлен. */
    REFUSED_DISABLED,

    /** Сервер отверг форму nonce: нужно новое подтверждение. */
    REFUSED_BAD_NONCE,

    /** Нестандартный ответ протокола: пере-подтверждение не состоялось. */
    REFUSED_PROTOCOL,
}

/** Ошибка канала подтверждения, которую координатор переводит в состояние UI. */
class ReportBotException(val failure: ReportBotFailure, detail: String = "", cause: Throwable? = null) :
    RuntimeException(detail.ifEmpty { failure.name }, cause)

/** Итог ожидания ответа бота в рамках одной сессии корреляции. */
sealed interface BotWaitOutcome {
    /** Бот подтвердил nonce; correlation_id — для показа, не для решений. */
    data class Confirmed(val correlationId: String, val botReplyMessageId: Long) : BotWaitOutcome

    /** Бот ответил отказом по протоколу. */
    data class Refused(val reply: com.beykus.beygram.diagnostics.report.BotReply) : BotWaitOutcome
}

/** Итог ожидания: бот подтвердил nonce; correlation_id — для показа, не для решений. */
data class BotConfirmation(val correlationId: String, val botReplyMessageId: Long)
