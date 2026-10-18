package com.beykus.beygram.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
