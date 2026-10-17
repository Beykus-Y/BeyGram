package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.CallRecord
import com.beykus.beygram.domain.model.CallStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

private const val CALL_PAGE_SIZE = 50

/** История звонков. TDLib хранит каждый звонок сообщением в чате, поэтому страницы ищутся сразу по всем чатам. */
internal class TdCallHistory(private val client: () -> Client, private val profiles: TdProfiles) {
    private val requests = TdRequests(client)

    suspend fun page(offset: String): CallHistoryPage = coroutineScope {
        val found = requests(TdApi.SearchCallMessages(offset, CALL_PAGE_SIZE, false))
        val messages = found.messages.orEmpty().filter { it.content is TdApi.MessageCall }
        // Названия чатов грузим параллельно: на одной странице их обычно несколько.
        val chats = messages.map { it.chatId }.distinct()
            .map { chatId -> async { chatId to chatOrNull(chatId) } }
            .awaitAll()
            .toMap()
        CallHistoryPage(
            calls = messages.map { it.toRecord(chats[it.chatId]) },
            nextOffset = found.nextOffset.orEmpty()
        )
    }

    /** Чат может быть недоступен, например, его уже нет в аккаунте: тогда звонок показываем без имени. */
    private suspend fun chatOrNull(chatId: Long): TdApi.Chat? = try {
        requests(TdApi.GetChat(chatId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun TdApi.Message.toRecord(chat: TdApi.Chat?): CallRecord {
        val call = content as TdApi.MessageCall
        return CallRecord(
            messageId = id,
            chatId = chatId,
            title = chat?.title?.takeIf { it.isNotBlank() }.orEmpty(),
            photo = chat?.let { profiles.photo(it.photo) },
            avatarColorArgb = chatId.stableColor(),
            isVideo = call.isVideo,
            isOutgoing = isOutgoing,
            status = call.discardReason.toStatus(call.duration, isOutgoing),
            durationSeconds = call.duration,
            dateSeconds = date.toLong()
        )
    }
}

private fun TdApi.CallDiscardReason?.toStatus(duration: Int, isOutgoing: Boolean): CallStatus = when {
    duration > 0 -> CallStatus.Answered
    this is TdApi.CallDiscardReasonDeclined -> CallStatus.Declined
    // TDLib помечает пропущенным вызов, завершённый до начала разговора: входящий пропущен, исходящий остался без ответа.
    this is TdApi.CallDiscardReasonMissed && !isOutgoing -> CallStatus.Missed
    // Разрыв связи и сброс вызова не значат, что звонок пропущен, поэтому остальное показываем как «без ответа».
    else -> CallStatus.NoAnswer
}
