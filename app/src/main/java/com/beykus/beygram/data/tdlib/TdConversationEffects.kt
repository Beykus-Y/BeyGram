package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.InteractionEffect
import com.beykus.beygram.domain.model.InteractionPolicy
import kotlinx.coroutines.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** The only gateway for conversation receipts and presence; no receipts are queued while suppressed. */
internal class TdConversationEffects(private val client: () -> Client, private val policy: InteractionPolicy) {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var foreground = false
    private var ready = false
    private var lastOnline: Boolean? = null
    private val opened = mutableSetOf<Long>()
    private val viewed = mutableMapOf<Long, MutableSet<Long>>()

    fun authorized() = synchronized(lock) { ready = true; lastOnline = null; updatePresence() }
    fun refreshPolicy() = synchronized(lock) { viewed.clear(); updatePresence() }
    fun setForeground(active: Boolean) = synchronized(lock) {
        foreground = active
        if (!active) viewed.clear()
        updatePresence()
    }
    fun opened(chatId: Long) = synchronized(lock) { opened.add(chatId); Unit }
    fun closed(chatId: Long) = synchronized(lock) { opened.remove(chatId); viewed.remove(chatId); Unit }
    private fun updatePresence() {
        val online = foreground && policy.allows(InteractionEffect.OnlinePresence, null)
        if (ready && online != lastOnline) {
            lastOnline = online
            client().send(TdApi.SetOption("online", TdApi.OptionValueBoolean(online)), Client.ResultHandler { result ->
                if (result is TdApi.Error) synchronized(lock) { if (lastOnline == online) lastOnline = null }
            })
        }
    }
    private fun canRead(chatId: Long) = ready && foreground && chatId in opened && policy.allows(InteractionEffect.ReadHistory, chatId)
    fun visible(chatId: Long, ids: List<Long>) {
        val batch = synchronized(lock) {
            if (!canRead(chatId)) return
            val sent = viewed.getOrPut(chatId) { mutableSetOf() }
            ids.filter { it > 0 && it !in sent }.distinct().take(100).onEach { sent.add(it) }.toLongArray()
        }
        if (batch.isEmpty()) return
        scope.launch {
            repeat(3) { attempt ->
                if (!synchronized(lock) { canRead(chatId) }) return@launch
                val result = conversationResult { TdRequests(client)(TdApi.ViewMessages(chatId, batch, TdApi.MessageSourceChatHistory(), false)) }
                if (result.isSuccess) return@launch
                if (attempt < 2) delay(1500L * (attempt + 1))
            }
            synchronized(lock) { viewed[chatId]?.removeAll(batch.toSet()) }
        }
    }
    fun contentOpened(chatId: Long, messageId: Long) = synchronized(lock) {
        if (ready && foreground && chatId in opened && policy.allows(InteractionEffect.OpenContent, chatId)) {
            client().send(TdApi.OpenMessageContent(chatId, messageId), Client.ResultHandler { })
        }
    }
}
