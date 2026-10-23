package com.beykus.beygram.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TelegramNotificationService.reconcileFromAllowedContext(context, if (intent.action == Intent.ACTION_BOOT_COMPLETED) "boot" else "package_replaced")
        }
    }
}

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationIntents.DISMISS) return
        // A dismissal belongs to its original native Telegram account.
        // Never feed numeric secondary notification IDs into the legacy
        // dismiss watermark/outbox (which may have the same group ID).
        val accountId = NotificationIntents.accountForDismissIntent(intent) ?: return
        val runtime = NotificationRuntime.get(context)
        val group = intent.getIntExtra(NotificationIntents.GROUP_ID, 0)
        val newest = intent.getIntExtra(NotificationIntents.MAX_ID, 0)
        if (accountId == "legacy") {
            runtime.dismiss(group, newest)
            if (runtime.preferences.authorized) runtime.attachRealClient(startProxy = false)
        } else {
            runtime.dismissSecondary(accountId, group, newest)
        }
    }
}

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationIntents.REPLY) return
        // The session that made the action. An action from a session that has signed out since is ignored.
        val session = NotificationIntents.replySession(context, intent) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationIntents.REPLY_TEXT)?.toString() ?: ""
        // The send may outlive this broadcast; the runtime finishes it within the receiver's time limit.
        val pending = goAsync()
        NotificationRuntime.get(context).reply(
            chatId = intent.getLongExtra(NotificationIntents.CHAT_ID, 0L),
            groupId = intent.getIntExtra(NotificationIntents.GROUP_ID, 0),
            maxId = intent.getIntExtra(NotificationIntents.MAX_ID, 0),
            replyToMessageId = intent.getLongExtra(NotificationIntents.REPLY_TO, 0L),
            topicId = intent.getLongExtra(NotificationIntents.TOPIC, 0L),
            text = text,
            session = session,
            finished = { pending.finish() }
        )
    }
}
