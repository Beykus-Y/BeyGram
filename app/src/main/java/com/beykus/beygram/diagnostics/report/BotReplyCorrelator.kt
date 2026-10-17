package com.beykus.beygram.diagnostics.report

import org.drinkless.tdlib.TdApi

/**
 * Корреляция конкретной команды `/report` с ответом бота.
 *
 * Вердикт выносится только для входящего текстового сообщения, которое:
 *  - пришло в приватный чат с ботом (проверка chatId),
 *  - от бота с ровно тем user id, что закреплён конфигурацией,
 *  - отвечает (reply_to) на НАШЕ отправленное сообщение (pending или финальный id).
 *
 * Ни отправка команды, ни сам факт наличия строки `correlation_id` подтверждением
 * не считаются. Посторонние сообщения бота коррелятор не потребляет (null).
 */
class BotReplyCorrelator(
    private val botUserId: Long,
    private val botChatId: Long
) {

    /** Итог проверки входящего сообщения; null — сообщение не относится к сессии. */
    sealed interface Verdict {
        data class Confirmed(val correlationId: String, val botReplyMessageId: Long) : Verdict

        /** Сервер ответил отказом по протоколу (allowlist, лимит, выключатель, форма nonce). */
        data class Refusal(val reply: BotReply) : Verdict
    }

    private val sentMessageIds = LinkedHashSet<Long>()
    private var consumedReplyId: Long = 0L

    /** Команда принята TDLib к отправке: запоминаем временный id. */
    @Synchronized
    fun trackSent(pendingMessageId: Long) {
        if (pendingMessageId != 0L) sentMessageIds.add(pendingMessageId)
    }

    /** TDLib заменил временный id финальным: обновляем набор. */
    @Synchronized
    fun onSendSucceeded(oldMessageId: Long, finalMessageId: Long) {
        sentMessageIds.remove(oldMessageId)
        if (finalMessageId != 0L) sentMessageIds.add(finalMessageId)
    }

    @Synchronized
    fun onSendFailed(oldMessageId: Long) {
        sentMessageIds.remove(oldMessageId)
    }

    /** Отправка с этим id принадлежит этой команде (ещё не заменена финальным id или не провалена). */
    @Synchronized
    fun owns(messageId: Long): Boolean = messageId in sentMessageIds

    @Synchronized
    fun onIncoming(message: TdApi.Message): Verdict? {
        // Приватный чат с ботом: только он и только он.
        if (message.chatId != botChatId) return null
        val sender = message.senderId as? TdApi.MessageSenderUser ?: return null
        if (sender.userId != botUserId) return null
        val replyTo = message.replyTo as? TdApi.MessageReplyToMessage ?: return null
        if (replyTo.chatId != 0L && replyTo.chatId != botChatId) return null
        if (replyTo.messageId !in sentMessageIds) return null
        val text = (message.content as? TdApi.MessageText)?.text?.text.orEmpty()
        return when (val reply = BotReplyParser.parse(text)) {
            is BotReply.Confirmed -> {
                // Один ответ потребляется один раз: повторная доставка не подтверждает снова.
                if (consumedReplyId != 0L && consumedReplyId == message.id) return null
                consumedReplyId = message.id
                Verdict.Confirmed(reply.correlationId, message.id)
            }
            BotReply.NotAllowed,
            BotReply.RateLimited,
            BotReply.Disabled,
            BotReply.BadNonce,
            BotReply.NeedNonce -> Verdict.Refusal(reply)
            BotReply.Unrelated -> null
        }
    }

    @Synchronized
    fun reset() {
        sentMessageIds.clear()
        consumedReplyId = 0L
    }
}
