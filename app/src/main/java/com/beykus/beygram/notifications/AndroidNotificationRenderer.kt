package com.beykus.beygram.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.data.archive.ArchiveSettingsStore
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.text.resolve
import java.util.concurrent.ConcurrentHashMap

/** Stable across restarts; Android tags must never collide across TDLib clients. */
internal fun accountNotificationTag(accountId: String, groupId: Int): String {
    require(accountId.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
    return if (accountId == "legacy") "beygram_message_$groupId"
    else "beygram_message_${accountId}_$groupId"
}

internal class AndroidNotificationRenderer(
    private val context: Context,
    private val preferences: NotificationPreferencesStore,
    private val accountId: String = "legacy"
) {
    private val ownTagPrefix = if (accountId == "legacy") "beygram_message_"
        else "beygram_message_${accountId}_"
    private fun tag(id: Int) = accountNotificationTag(accountId, id)
    // Re-read the selected locale at render time, not only at service creation.
    private val localizedContext get() = AppLocale.context(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val compat = NotificationManagerCompat.from(context)
    // The last reason Messages notifications were blocked; the diagnostics journal records only changes.
    private var blockedReason: String? = null
    // Groups whose direct reply was rejected, with the newest notice id at that time. The note stays until a newer notice arrives or the group is cancelled.
    private val failedReplies = ConcurrentHashMap<Int, Int>()
    // Tags posted by this process: lets stale entries be cancelled even before TDLib's startup snapshot arrives.
    private val postedTags = ConcurrentHashMap.newKeySet<String>()
    init { channels(context) }
    fun render(state: TelegramNotifications, config: NotificationPreferences, presentation: NotificationPresentation, authorized: Boolean) {
        if (!state.initialized && authorized) return // Wait for TDLib's startup snapshot before reconciling old tray entries.
        if (authorized) {
            val blocked = when {
                !config.enabled -> "disabled"
                !canNotify(context) -> "permission"
                messagesChannelBlocked(context) -> "channel_blocked"
                else -> null
            }
            if (blocked != blockedReason) {
                blockedReason = blocked
                recordAppEvent(EventKind.NOTIFICATION, "messages_state", blockedText(blocked), if (blocked == null || blocked == "disabled") Severity.INFO else Severity.WARNING, mapOf("reason" to (blocked ?: "none")))
            }
        }
        val shown = mutableSetOf<String>()
        val archiveSound = ArchiveSettingsStore.get(context, accountId).state.value.archiveSound
        for (group in state.groups) {
            val tag = tag(group.id)
            val maxId = group.notices.maxOfOrNull { it.id } ?: 0
            val fresh = group.alertId > preferences.handled(group.id)
            val failedAt = failedReplies[group.id]
            if (failedAt != null && maxId > failedAt) failedReplies.remove(group.id)
            val notices = group.notices.filter { it.id > preferences.dismissed(group.id) }
            if (!authorized || !config.enabled || !canNotify(context) || notices.isEmpty() || presentation.foreground && group.chatId in presentation.openedChats) {
                // Deliberate suppression consumes the alert, so re-enabling notifications later does not ring for old messages.
                // A signed-out state consumes nothing: sign-out already dropped the watermarks for the next account.
                if (authorized) preferences.handled(group.id, maxId)
                continue
            }
            val hideContent = group.isSecret || !group.metadataReady
            val title = if (hideContent) "BeyGram" else group.title
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName(localizedContext.getString(R.string.chat_you)).build()).setConversationTitle(title).setGroupConversation(group.isGroup)
            notices.forEach { notice ->
                val preview = config.previews && notice.previewAllowed && !hideContent
                style.addMessage(if (preview) notice.text.resolve(localizedContext) else localizedContext.getString(R.string.notif_new_message), notice.dateSeconds * 1000,
                    Person.Builder().setName(if (hideContent) "Telegram" else notice.sender.ifBlank { title }).build())
            }
            val latest = notices.last()
            val public = NotificationCompat.Builder(context, MESSAGES).setSmallIcon(R.drawable.ic_notification).setContentTitle("BeyGram").setContentText(localizedContext.getString(R.string.notif_new_message)).build()
            val builder = NotificationCompat.Builder(context, MESSAGES).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
                .setContentText(if (config.previews && latest.previewAllowed && !hideContent) latest.text.resolve(localizedContext) else localizedContext.getString(R.string.notif_new_message))
                .setStyle(style).setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
                .setWhen(latest.dateSeconds * 1000).setShowWhen(true).setNumber(group.totalCount).setAutoCancel(true)
                .setContentIntent(NotificationIntents.chat(context, group.chatId, group.id, maxId, latest.topicId, accountId)).setDeleteIntent(NotificationIntents.dismiss(context, group.id, maxId, accountId))
                .setSilent(!fresh || (group.archived && !archiveSound)).setOnlyAlertOnce(!fresh).setPriority(NotificationCompat.PRIORITY_HIGH)
            // A reply needs a visible chat title and a chat the user can write to, so other groups get no reply action.
            // ReplyOutbox still has legacy-only ownership. Do not expose a
            // secondary quick-reply PendingIntent until its outbox is scoped.
            if (accountId == "legacy" && !hideContent && group.replyable)
                builder.addAction(replyAction(group.chatId, group.id, maxId, latest.messageId, latest.topicId))
            if (failedReplies.containsKey(group.id)) builder.setSubText(localizedContext.getString(R.string.notif_reply_failed))
            val notification = builder.build()
            try {
                compat.notify(tag, 0, notification)
                postedTags.add(tag)
                // Advanced only after the system accepted the post, so a failed alert is retried on the next render.
                preferences.handled(group.id, maxId)
            } catch (error: RuntimeException) {
                recordAppEvent(EventKind.NOTIFICATION, "notify_failed", "Уведомление не опубликовано: ${error.javaClass.simpleName}", Severity.WARNING)
            }
            // A failed post keeps the earlier entry instead of cancelling it as stale.
            shown.add(tag)
        }
        // Stale entries are cancelled only once TDLib's state has arrived (startup snapshot or a group update), so a cold start keeps the tray.
        if (!state.snapshot) {
            postedTags.filter { it !in shown }.forEach { manager.cancel(it, 0); postedTags.remove(it) }
        }
        if (state.snapshot) {
            postedTags.retainAll(shown)
            manager.activeNotifications.filter { it.tag?.startsWith(ownTagPrefix) == true &&
                (accountId != "legacy" || it.tag?.removePrefix(ownTagPrefix)?.toIntOrNull() != null) &&
                it.tag !in shown }.forEach { manager.cancel(it.tag, it.id) }
            // A failure note belongs to its group; once the group is gone, a reused group ID must not inherit the note.
            failedReplies.keys.filter { id -> state.groups.none { it.id == id } }.forEach { failedReplies.remove(it) }
        }
    }
    fun cancelGroup(id: Int) { manager.cancel(tag(id), 0); failedReplies.remove(id) }
    fun cancelAllOwned() {
        manager.activeNotifications.filter { it.tag?.startsWith(ownTagPrefix) == true &&
            (accountId != "legacy" || it.tag?.removePrefix(ownTagPrefix)?.toIntOrNull() != null)
        }.forEach { manager.cancel(it.tag, it.id) }
        postedTags.clear()
        failedReplies.clear()
    }
    fun markReplyFailed(id: Int, maxId: Int) { failedReplies[id] = maxId }
    private fun replyAction(chatId: Long, groupId: Int, maxId: Int, replyToMessageId: Long, topicId: Long): NotificationCompat.Action = NotificationCompat.Action.Builder(0, localizedContext.getString(R.string.notif_action_reply), NotificationIntents.reply(context, chatId, groupId, maxId, replyToMessageId, topicId))
        .addRemoteInput(RemoteInput.Builder(NotificationIntents.REPLY_TEXT).setLabel(localizedContext.getString(R.string.notif_reply_hint)).build())
        .setAllowGeneratedReplies(false)
        .build()
    private fun blockedText(reason: String?): String = when (reason) {
        "disabled" -> "Уведомления о сообщениях выключены в настройках"
        "permission" -> "Нет разрешения на показ уведомлений"
        "channel_blocked" -> "Канал «Сообщения» выключен в Android"
        else -> "Показ уведомлений о сообщениях разрешён"
    }
    companion object {
        const val MESSAGES = "messages_v1"
        const val CONNECTION = "background_connection_v1"
        const val PROXY = "beygram-mihomo-proxy"
        const val SERVICE_ID = 900_001
        fun canNotify(context: Context): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
        /** Android can switch a channel off; notify() then posts nothing and throws nothing. */
        fun channelBlocked(context: Context, channelId: String): Boolean =
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        fun messagesChannelBlocked(context: Context): Boolean = channelBlocked(context, MESSAGES)
        fun channels(context: Context) {
            val localizedContext = AppLocale.context(context)
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
                NotificationChannel(MESSAGES, localizedContext.getString(R.string.notif_channel_messages), NotificationManager.IMPORTANCE_HIGH).apply { description = localizedContext.getString(R.string.notif_channel_messages_description); lockscreenVisibility = Notification.VISIBILITY_PRIVATE },
                NotificationChannel(CONNECTION, localizedContext.getString(R.string.notif_channel_connection), NotificationManager.IMPORTANCE_LOW).apply { description = localizedContext.getString(R.string.notif_channel_connection_description); setSound(null, null); enableVibration(false); setShowBadge(false) },
                NotificationChannel(PROXY, localizedContext.getString(R.string.proxy_notification_title), NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false); setShowBadge(false) }
            ))
        }
    }
}
