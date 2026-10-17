package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class TdBotKeyboards(client: () -> Client) {
    private val request = TdRequests(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = ConcurrentHashMap<Long, MutableStateFlow<BotKeyboard?>>()
    private val versions = ConcurrentHashMap<Long, AtomicLong>()
    private val lock = Any()
    fun observe(chatId: Long): StateFlow<BotKeyboard?> = states.getOrPut(chatId) { MutableStateFlow(null) }.asStateFlow()
    fun update(chatId: Long, message: TdApi.Message?) = synchronized(lock) {
        versions.getOrPut(chatId) { AtomicLong() }.incrementAndGet()
        states.getOrPut(chatId) { MutableStateFlow(null) }.value = botKeyboard(message)
    }
    fun refresh(chatId: Long) {
        observe(chatId)
        val version = versions.getOrPut(chatId) { AtomicLong() }
        val current = synchronized(lock) { version.incrementAndGet() }
        scope.launch {
            conversationResult {
                val chat = request(TdApi.GetChat(chatId))
                val message = if (chat.replyMarkupMessageId != 0L) request(TdApi.GetMessage(chatId, chat.replyMarkupMessageId)) else null
                synchronized(lock) { if (version.get() == current) states.getValue(chatId).value = botKeyboard(message) }
            }
        }
    }
    suspend fun dismiss(chatId: Long, messageId: Long): Result<Unit> = conversationResult {
        request(TdApi.DeleteChatReplyMarkup(chatId, messageId))
        if (states[chatId]?.value?.messageId == messageId) update(chatId, null)
    }
    suspend fun callback(chatId: Long, messageId: Long, data: List<Byte>): Result<BotCallbackAnswer> = conversationResult {
        val answer = request(TdApi.GetCallbackQueryAnswer(chatId, messageId, TdApi.CallbackQueryPayloadData(data.toByteArray())))
        BotCallbackAnswer(answer.text, answer.showAlert, answer.url)
    }
}
