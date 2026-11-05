package com.beykus.beygram.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.beykus.beygram.MainActivity
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.domain.triggers.TriggerMention

/**
 * Alerts for trigger words in muted chats. They use their own channel, so Messages keeps its sound and
 * importance. The alert opens the app; the mention itself is in the trigger words feed.
 */
internal object TriggerAlerts {
    const val CHANNEL_ID = "trigger_words_v1"
    private const val TAG_PREFIX = "trigger_"
    /** Trigger alerts kept in the tray at once. Android refuses new posts from an app that has too many outstanding. */
    private const val MAX_OUTSTANDING = 10

    fun post(context: Context, mention: TriggerMention, preferences: NotificationPreferences) {
        if (!preferences.enabled || !AndroidNotificationRenderer.canNotify(context)) return
        val localized = AppLocale.context(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, localized.getString(R.string.trigger_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = localized.getString(R.string.trigger_channel_description)
            }
        )
        val word = mention.triggers.firstOrNull().orEmpty()
        // Without previews the alert names the trigger word only, never the message text.
        val snippet = mention.snippet?.takeIf { preferences.previews && it.isNotEmpty() }
        val text = if (snippet != null) {
            localized.getString(R.string.trigger_alert_text, mention.senderName.ifBlank { mention.chatTitle }, snippet)
        } else {
            localized.getString(R.string.trigger_alert_hidden, word)
        }
        val title = mention.chatTitle.ifBlank { localized.getString(R.string.trigger_channel_name) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(mention.dateSeconds * 1000)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        dropOldest(manager)
        try {
            NotificationManagerCompat.from(context).notify(tag(mention), 0, notification)
        } catch (_: SecurityException) {
            // The notification permission was revoked between the check and the post; the feed still has the mention.
        }
    }

    /** Removes the alerts of some chats of one account, after Telegram stopped allowing their text in notifications. */
    fun cancelChats(context: Context, accountId: String, chatIds: Set<Long>) {
        if (chatIds.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val prefixes = chatIds.map { accountTagPrefix(accountId) + "${it}_" }
        manager.activeNotifications.filter { notification -> prefixes.any { notification.tag?.startsWith(it) == true } }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /** Removes the alerts of one account, so a signed-out account's chat titles and text leave the tray. */
    fun cancelAccount(context: Context, accountId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val prefix = accountTagPrefix(accountId)
        manager.activeNotifications.filter { it.tag?.startsWith(prefix) == true }.forEach { manager.cancel(it.tag, it.id) }
    }

    /**
     * Keeps the newest [MAX_OUTSTANDING] - 1 alerts, so the one about to be posted still fits. Android counts all of the
     * app's notifications against one limit, so unbounded alerts would also block ordinary message notifications.
     * Older matches stay in the feed.
     */
    private fun dropOldest(manager: NotificationManager) {
        manager.activeNotifications
            .filter { it.tag?.startsWith(TAG_PREFIX) == true }
            .sortedByDescending { it.postTime }
            .drop(MAX_OUTSTANDING - 1)
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /** One tag per message, so a later alert never replaces an earlier one. */
    private fun tag(mention: TriggerMention): String = accountTagPrefix(mention.accountId) + "${mention.chatId}_${mention.messageId}"

    private fun accountTagPrefix(accountId: String): String = "$TAG_PREFIX${accountId}_"

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
