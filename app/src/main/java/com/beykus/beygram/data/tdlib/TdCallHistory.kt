package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.CallRecord
import com.beykus.beygram.domain.model.CallStatus
import kotlinx.coroutines.CancellationException
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

private const val CALL_PAGE_SIZE = 50

/** История звонков. TDLib хранит каждый звонок сообщением в чате, поэтому страницы ищутся сразу по всем чатам. */
internal class TdCallHistory(private val client: () -> Client, private val profiles: TdProfiles) {
    private val requests = TdRequests(client)

    suspend fun page(offset: String): CallHistoryPage {
        val found = requests(TdApi.SearchCallMessages(offset, CALL_PAGE_SIZE, false))
        val messages = found.messages.orEmpty().filter { it.content is TdApi.MessageCall }
        val chats = mutableMapOf<Long, TdApi.Chat?>()
        for (message in messages) {
            if (message.chatId !in chats) chats[message.chatId] = chatOrNull(message.chatId)
        }
        return CallHistoryPage(
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
            title = chat?.title?.takeIf { it.isNotBlank() } ?: "Звонок",
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
    this is TdApi.CallDiscardReasonMissed -> if (isOutgoing) CallStatus.NoAnswer else CallStatus.Missed
    else -> CallStatus.NoAnswer
}
