package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Consume TDLib's authoritative notification groups, never infer alerts from history. */
internal class TdNotifications(client: () -> Client, private val senderName: (TdApi.MessageSender?) -> String) {
    private val request = TdRequests(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val groups = mutableMapOf<Int, TelegramNoticeGroup>()
    private val chats = mutableMapOf<Long, TdApi.Chat>()
    private val resolving = mutableSetOf<Long>()
    private val _state = MutableStateFlow(TelegramNotifications())
    val state = _state.asStateFlow()

    fun initialize() { scope.launch {
        conversationResult {
            request(TdApi.SetOption("notification_group_size_max", TdApi.OptionValueInteger(10)))
            request(TdApi.SetOption("notification_group_count_max", TdApi.OptionValueInteger(25)))
            // On first opt-in TDLib can enable groups after its startup snapshot phase.
            synchronized(lock) { _state.value = _state.value.copy(initialized = true) }
        }
    } }
    suspend fun dismiss(groupId: Int, maxId: Int): Result<Unit> = conversationResult {
        request(TdApi.RemoveNotificationGroup(groupId, maxId)); Unit
    }
    fun chat(chat: TdApi.Chat) = synchronized(lock) {
        chats[chat.id] = chat
        groups.replaceAll { _, group -> if (group.chatId == chat.id) metadata(group) else group }
        publish()
    }
    fun handle(update: TdApi.Object) = synchronized(lock) {
        when (update) {
            is TdApi.UpdateActiveNotifications -> {
                groups.clear()
                update.groups.orEmpty().filterNot { it.type is TdApi.NotificationGroupTypeCalls }.forEach { group ->
                    groups[group.id] = metadata(TelegramNoticeGroup(group.id, group.chatId, totalCount = group.totalCount,
                        notices = group.notifications.orEmpty().mapNotNull(::notice), isSecret = group.type is TdApi.NotificationGroupTypeSecretChat))
                }
                _state.value = _state.value.copy(initialized = true)
            }
            is TdApi.UpdateNotificationGroup -> {
                _state.value = _state.value.copy(initialized = true)
                if (update.type is TdApi.NotificationGroupTypeCalls) { groups.remove(update.notificationGroupId); publish(); return@synchronized }
                val old = groups[update.notificationGroupId]?.takeIf { it.chatId == update.chatId }
                val removed = (update.removedNotificationIds ?: intArrayOf()).toSet()
                val added = update.addedNotifications.orEmpty()
                val merged = (old?.notices.orEmpty().filterNot { it.id in removed } + added.mapNotNull(::notice)).associateBy { it.id }.values.sortedBy { it.id }.takeLast(10)
                val alert = if (update.notificationSoundId != 0L) added.filter { !it.isSilent && it.id > (old?.notices?.maxOfOrNull { n -> n.id } ?: 0) }.maxOfOrNull { it.id } ?: 0 else 0
                if (update.totalCount == 0 || merged.isEmpty()) groups.remove(update.notificationGroupId)
                else groups[update.notificationGroupId] = metadata(TelegramNoticeGroup(update.notificationGroupId, update.chatId, totalCount = update.totalCount,
                    notices = merged, alertId = alert, isSecret = update.type is TdApi.NotificationGroupTypeSecretChat))
            }
            is TdApi.UpdateNotification -> groups[update.notificationGroupId]?.let { group ->
                val changed = notice(update.notification)
                groups[group.id] = group.copy(alertId = 0, notices = group.notices.mapNotNull { if (it.id == update.notification.id) changed else it })
            }
            is TdApi.UpdateHavePendingNotifications -> _state.value = _state.value.copy(hasPending = update.haveDelayedNotifications || update.haveUnreceivedNotifications)
            is TdApi.UpdateChatTitle -> chats[update.chatId]?.let { it.title = update.title; groups.replaceAll { _, group -> if (group.chatId == it.id) metadata(group) else group } }
            else -> return@synchronized
        }
        publish()
        if (com.beykus.beygram.BuildConfig.DEBUG && (update is TdApi.UpdateActiveNotifications || update is TdApi.UpdateNotificationGroup)) {
            android.util.Log.i("BeyGramNotify", "${update.javaClass.simpleName}: active groups=${groups.size}")
        }
        groups.values.map { it.chatId }.distinct().filter { it !in chats && resolving.add(it) }.forEach { id -> scope.launch {
            try { chat(request(TdApi.GetChat(id))) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
            finally { synchronized(lock) { resolving.remove(id) } }
        } }
    }
    private fun metadata(group: TelegramNoticeGroup): TelegramNoticeGroup = chats[group.chatId]?.let { chat ->
        group.copy(title = chat.title, metadataReady = true, isGroup = chat.type is TdApi.ChatTypeBasicGroup || chat.type is TdApi.ChatTypeSupergroup,
            isSecret = group.isSecret || chat.type is TdApi.ChatTypeSecret)
    } ?: group
    private fun publish() { _state.value = _state.value.copy(groups = groups.values.sortedBy { it.id }) }
    private fun notice(notification: TdApi.Notification): TelegramNotice? = when (val type = notification.type) {
        is TdApi.NotificationTypeNewMessage -> TelegramNotice(notification.id, notification.date.toLong(), senderName(type.message.senderId),
            if (type.showPreview) preview(type.message.content) else "Новое сообщение", type.showPreview)
        is TdApi.NotificationTypeNewSecretChat -> TelegramNotice(notification.id, notification.date.toLong(), "", "Новый секретный чат", false)
        else -> null // No FCM registration; push payloads and unimplemented calls are not rendered.
    }
    private fun preview(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> "Фото" + content.caption?.text.orEmpty().let { if (it.isBlank()) "" else ": $it" }
        is TdApi.MessageVideo -> "Видео" + content.caption?.text.orEmpty().let { if (it.isBlank()) "" else ": $it" }
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessageVoiceNote -> "Голосовое сообщение"
        is TdApi.MessageVideoNote -> "Видеосообщение"
        is TdApi.MessageAudio -> "Аудио"
        is TdApi.MessageDocument -> "Файл: ${content.document.fileName}"
        is TdApi.MessageSticker -> "${content.sticker.emoji} Стикер"
        else -> "Новое сообщение"
    }.take(1000)
}
