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
        // Persist removal immediately. The shared client flushes it when authorized, without ViewMessages.
        val runtime = NotificationRuntime.get(context)
        runtime.dismiss(intent.getIntExtra(NotificationIntents.GROUP_ID, 0), intent.getIntExtra(NotificationIntents.MAX_ID, 0))
        if (runtime.preferences.authorized) runtime.attachRealClient()
    }
}

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationIntents.REPLY || !NotificationIntents.validReply(context, intent)) return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationIntents.REPLY_TEXT)?.toString() ?: ""
        // The send may outlive this broadcast; the runtime finishes it within the receiver's time limit.
        val pending = goAsync()
        NotificationRuntime.get(context).reply(
            chatId = intent.getLongExtra(NotificationIntents.CHAT_ID, 0L),
            groupId = intent.getIntExtra(NotificationIntents.GROUP_ID, 0),
            maxId = intent.getIntExtra(NotificationIntents.MAX_ID, 0),
            replyToMessageId = intent.getLongExtra(NotificationIntents.REPLY_TO, 0L),
            text = text,
            finished = { pending.finish() }
        )
    }
}
