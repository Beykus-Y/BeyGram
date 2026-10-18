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
import androidx.core.content.ContextCompat
import com.beykus.beygram.R
import com.beykus.beygram.data.archive.ArchiveSettingsStore
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.text.resolve

internal class AndroidNotificationRenderer(private val context: Context, private val preferences: NotificationPreferencesStore) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val compat = NotificationManagerCompat.from(context)
    init { channels(context) }
    fun render(state: TelegramNotifications, config: NotificationPreferences, presentation: NotificationPresentation, authorized: Boolean) {
        if (!state.initialized && authorized) return // Wait for TDLib's startup snapshot before reconciling old tray entries.
        val shown = mutableSetOf<String>()
        val archiveSound = ArchiveSettingsStore.get(context).state.value.archiveSound
        for (group in state.groups) {
            val tag = tag(group.id)
            val maxId = group.notices.maxOfOrNull { it.id } ?: 0
            val fresh = group.alertId > preferences.handled(group.id)
            val notices = group.notices.filter { it.id > preferences.dismissed(group.id) }
            if (!authorized || !config.enabled || !canNotify(context) || notices.isEmpty() || presentation.foreground && group.chatId in presentation.openedChats) {
                // Deliberate suppression consumes the alert, so re-enabling notifications later does not ring for old messages.
                preferences.handled(group.id, maxId)
                continue
            }
            val hideContent = group.isSecret || !group.metadataReady
            val title = if (hideContent) "BeyGram" else group.title
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.chat_you)).build()).setConversationTitle(title).setGroupConversation(group.isGroup)
            notices.forEach { notice ->
                val preview = config.previews && notice.previewAllowed && !hideContent
                style.addMessage(if (preview) notice.text.resolve(context) else context.getString(R.string.notif_new_message), notice.dateSeconds * 1000,
                    Person.Builder().setName(if (hideContent) "Telegram" else notice.sender.ifBlank { title }).build())
            }
            val latest = notices.last()
            val public = NotificationCompat.Builder(context, MESSAGES).setSmallIcon(R.drawable.ic_notification).setContentTitle("BeyGram").setContentText(context.getString(R.string.notif_new_message)).build()
            val notification = NotificationCompat.Builder(context, MESSAGES).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
                .setContentText(if (config.previews && latest.previewAllowed && !hideContent) latest.text.resolve(context) else context.getString(R.string.notif_new_message))
                .setStyle(style).setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
                .setWhen(latest.dateSeconds * 1000).setShowWhen(true).setNumber(group.totalCount).setAutoCancel(true)
                .setContentIntent(NotificationIntents.chat(context, group.chatId, group.id, maxId)).setDeleteIntent(NotificationIntents.dismiss(context, group.id, maxId))
                .setSilent(!fresh || (group.archived && !archiveSound)).setOnlyAlertOnce(!fresh).setPriority(NotificationCompat.PRIORITY_HIGH).build()
            try {
                compat.notify(tag, 0, notification)
                // Advanced only after the system accepted the post, so a failed alert is retried on the next render.
                preferences.handled(group.id, maxId)
            } catch (_: SecurityException) { }
            // A failed post keeps the earlier entry instead of cancelling it as stale.
            shown.add(tag)
        }
        // Stale entries are cancelled only once TDLib's state has arrived (startup snapshot or a group update), so a cold start keeps the tray.
        if (state.snapshot) manager.activeNotifications.filter { it.tag?.startsWith(TAG_PREFIX) == true && it.tag !in shown }.forEach { manager.cancel(it.tag, it.id) }
    }
    fun cancelGroup(id: Int) { manager.cancel(tag(id), 0) }
    companion object {
        const val MESSAGES = "messages_v1"
        const val CONNECTION = "background_connection_v1"
        const val SERVICE_ID = 900_001
        private const val TAG_PREFIX = "beygram_message_"
        private fun tag(id: Int) = "$TAG_PREFIX$id"
        fun canNotify(context: Context): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
        /** Android can switch the Messages channel off; notify() then posts nothing and throws nothing. */
        fun messagesChannelBlocked(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(MESSAGES)?.importance == NotificationManager.IMPORTANCE_NONE
        fun channels(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
                NotificationChannel(MESSAGES, context.getString(R.string.notif_channel_messages), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.notif_channel_messages_description); lockscreenVisibility = Notification.VISIBILITY_PRIVATE },
                NotificationChannel(CONNECTION, context.getString(R.string.notif_channel_connection), NotificationManager.IMPORTANCE_LOW).apply { description = context.getString(R.string.notif_channel_connection_description); setSound(null, null); enableVibration(false); setShowBadge(false) }
            ))
        }
    }
}
