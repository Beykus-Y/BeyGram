package com.beykus.beygram.domain.repository

import com.beykus.beygram.diagnostics.report.BotConfirmation

/**
 * Канал подтверждения отчёта через служебного бота. Реализации: TDLib-репозиторий
 * и репозиторий «Telegram недоступен». UI не делает TDLib-запросов сам.
 *
 * Подтверждением считается только входящее сообщение от бота с закреплённым
 * user id в его приватном чате, отвечающее на нашу команду, — корреляция
 * выполняется внутри реализации.
 */
interface ReportBotChannel {

    /**
     * Открывает (при необходимости создаёт) приватный чат со служебным ботом
     * и проверяет, что user id бота совпадает с закреплённым в конфигурации.
     * Возвращает id чата.
     */
    suspend fun openBotChat(expectedBotUserId: Long, username: String): Result<Long>

    /** Отправляет видимое сообщение `/report <nonce>`. Успех отправки — ещё не подтверждение. */
    suspend fun sendReportCommand(botChatId: Long, nonce: String): Result<Unit>

    /** Ждёт входящее подтверждение бота, относящееся к отправленной команде, не дольше [timeoutMillis]. */
    suspend fun awaitBotConfirmation(botChatId: Long, timeoutMillis: Long): Result<BotConfirmation>

    /** Прерывает ожидание ещё не пришедшего подтверждения. Отправленное сообщение не отзывается. */
    fun cancelBotWait()
}
