package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Lightweight, deliberately read-only view of one secondary TDLib account. */
internal data class TdSecondaryChatSummary(
    val key: TdAccountChatKey,
    val title: String,
    val preview: String = "",
    val order: Long = 0L,
    val unreadCount: Int = 0,
    val lastDate: Int = 0
)

internal data class TdSecondaryTextMessage(
    val key: TdAccountMessageKey,
    val text: String,
    val outgoing: Boolean,
    val date: Int
)

/**
 * No singleton caches, no main-repository access and no cross-account IDs.
 * The native update callback and UI/request coroutines may run on different
 * threads, so a single lock owns all mutations of both StateFlows.
 */
internal class TdSecondaryChatStore(val accountId: String) {
    private val lock = Any()
    private val byId = mutableMapOf<TdAccountChatKey, TdSecondaryChatSummary>()
    private val _chats = MutableStateFlow<List<TdSecondaryChatSummary>>(emptyList())
    val chats: StateFlow<List<TdSecondaryChatSummary>> = _chats.asStateFlow()
    private val _history = MutableStateFlow<Map<TdAccountChatKey, List<TdSecondaryTextMessage>>>(emptyMap())
    val history: StateFlow<Map<TdAccountChatKey, List<TdSecondaryTextMessage>>> = _history.asStateFlow()

    private fun key(chatId: Long) = TdAccountChatKey(accountId, chatId)
    private fun requireOwn(key: TdAccountChatKey) = require(key.accountId == accountId) {
        "TDLib chat belongs to a different Telegram account"
    }

    fun upsert(summary: TdSecondaryChatSummary) {
        requireOwn(summary.key)
        synchronized(lock) {
            byId[summary.key] = summary
            publish()
        }
    }

    fun replaceHistory(chat: TdAccountChatKey, messages: List<TdSecondaryTextMessage>) {
        requireOwn(chat)
        require(messages.all { it.key.accountId == accountId && it.key.chatId == chat.chatId }) {
            "Foreign Telegram account/message in chat history"
        }
        synchronized(lock) {
            _history.value = _history.value + (chat to messages.distinctBy { it.key }
                .sortedWith(compareBy<TdSecondaryTextMessage> { it.date }.thenBy { it.key.messageId }))
        }
    }

    private fun publish() {
        _chats.value = byId.values.filter { it.order > 0L }
            .sortedWith(compareByDescending<TdSecondaryChatSummary> { it.order }.thenBy { it.key.chatId })
    }

    fun receive(event: TdAccountUpdateRouter.TdAccountRoutedUpdate) {
        require(event.accountId == accountId) { "Cross-account TDLib update blocked" }
        synchronized(lock) {
            when (val update = event.update) {
                is TdApi.UpdateNewChat -> {
                    val chat = update.chat
                    val order = chat.positions.orEmpty()
                        .firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L
                    byId[key(chat.id)] = TdSecondaryChatSummary(
                        key(chat.id), chat.title.orEmpty(), preview(chat.lastMessage),
                        order, chat.unreadCount, chat.lastMessage?.date ?: 0
                    )
                    publish()
                }
                is TdApi.UpdateChatTitle -> {
                    val chatKey = key(update.chatId)
                    byId[chatKey]?.let { byId[chatKey] = it.copy(title = update.title.orEmpty()) }
                    publish()
                }
                is TdApi.UpdateChatPosition -> {
                    if (update.position.list !is TdApi.ChatListMain) return
                    val chatKey = key(update.chatId)
                    byId[chatKey]?.let { byId[chatKey] = it.copy(order = update.position.order) }
                    publish()
                }
                is TdApi.UpdateChatLastMessage -> {
                    val chatKey = key(update.chatId)
                    byId[chatKey]?.let {
                        val order = update.positions.orEmpty().firstOrNull {
                            p -> p.list is TdApi.ChatListMain
                        }?.order ?: 0L
                        byId[chatKey] = it.copy(preview = preview(update.lastMessage),
                            lastDate = update.lastMessage?.date ?: 0, order = order)
                    }
                    publish()
                }
                is TdApi.UpdateChatReadInbox -> {
                    val chatKey = key(update.chatId)
                    byId[chatKey]?.let { byId[chatKey] = it.copy(unreadCount = update.unreadCount) }
                    publish()
                }
                is TdApi.UpdateNewMessage -> {
                    val chatKey = key(update.message.chatId)
                    val existing = _history.value[chatKey] ?: return
                    val added = textMessage(update.message)
                    _history.value = _history.value + (chatKey to
                        (existing + added).distinctBy { it.key }
                            .sortedWith(compareBy<TdSecondaryTextMessage> { it.date }.thenBy { it.key.messageId }))
                }
            }
        }
    }

    fun clear() = synchronized(lock) {
        byId.clear()
        _chats.value = emptyList()
        _history.value = emptyMap()
    }

    private fun preview(message: TdApi.Message?): String = when (val content = message?.content) {
        is TdApi.MessageText -> content.text.text.orEmpty()
        null -> ""
        else -> "Медиа"
    }

    private fun textMessage(message: TdApi.Message): TdSecondaryTextMessage {
        val text = preview(message).ifBlank { "Сообщение" }
        return TdSecondaryTextMessage(
            TdAccountMessageKey(accountId, message.chatId, message.id),
            text, message.isOutgoing, message.date
        )
    }

    fun convertHistory(chat: TdAccountChatKey, messages: Array<TdApi.Message>): List<TdSecondaryTextMessage> {
        requireOwn(chat)
        require(messages.all { it.chatId == chat.chatId }) { "TDLib returned a different chat" }
        return messages.map(::textMessage)
    }
}
