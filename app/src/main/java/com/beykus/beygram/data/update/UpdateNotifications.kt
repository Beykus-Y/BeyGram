package com.beykus.beygram.data.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.beykus.beygram.MainActivity
import com.beykus.beygram.R
import com.beykus.beygram.notifications.AndroidNotificationRenderer
import com.beykus.beygram.data.settings.AppLocale

/**
 * The "BeyGram updates" system notification. One alert per offered build: a newer build replaces the previous alert
 * silently, the same build is never announced twice while its alert is still in the tray, and the alert is removed as
 * soon as the offer is installed or withdrawn.
 *
 * Nothing here starts a download or an install. The tap opens the existing Updates screen through an explicit,
 * immutable PendingIntent on [MainActivity]; the app lock and the privacy rules of the activity stay in force.
 */
internal object UpdateNotifications {
    /** Its own channel: the user can silence update alerts without touching message, proxy or trigger channels. */
    private const val CHANNEL_ID = "beygram_updates"

    /** Stable id, so a new offer replaces the old alert instead of stacking duplicates. */
    private const val NOTIFICATION_ID = 0x8F1

    fun offer(context: Context, release: StableRelease, store: UpdateNoticeStore, now: Long) {
        if (!AndroidNotificationRenderer.canNotify(context)) return
        if (!UpdateAutoCheckPolicy.shouldAnnounce(release.versionCode, installedCode(context))) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)
        val sameOffer = store.notifiedVersionCode() == release.versionCode
        if (sameOffer && isInTray(manager)) {
            // Already announced and still visible: a later check must not alert the user again.
            return
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.updates_notification_title))
            .setContentText(context.getString(R.string.updates_notification_text, release.versionName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.updates_notification_text, release.versionName)))
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            // Version numbers are not private, but the notification must not reveal more than the screen does.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(sameOffer)
            .setAutoCancel(true)
            .setWhen(now)
            .setShowWhen(false)
            .setContentIntent(openUpdates(context, release.versionCode))
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            store.saveNotifiedVersionCode(release.versionCode)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the post; the in-app banner still tells the user.
        }
    }

    /** Removes the alert and forgets the announced build, so a future offer is announced again. */
    fun clear(context: Context, store: UpdateNoticeStore) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        store.saveNotifiedVersionCode(null)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val localized = AppLocale.context(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localized.getString(R.string.updates_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = localized.getString(R.string.updates_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun isInTray(manager: NotificationManager): Boolean =
        manager.activeNotifications.any { it.id == NOTIFICATION_ID && it.tag == null }

    private fun installedCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /**
     * Explicit component, an immutable flag and a private data URI that only carries the offered build. No Telegram
     * notification token is involved: this alert is produced by the updater, not by a TDLib session.
     */
    private fun openUpdates(context: Context, versionCode: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_UPDATES)
            .setData(Uri.parse("beygram://${context.packageName}/updates/$versionCode"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_OPEN_UPDATES = "com.beykus.beygram.action.OPEN_UPDATES"
}