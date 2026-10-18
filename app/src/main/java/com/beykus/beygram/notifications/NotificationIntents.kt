package com.beykus.beygram.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.beykus.beygram.MainActivity

internal object NotificationIntents {
    const val OPEN_CHAT = "com.beykus.beygram.OPEN_NOTIFICATION_CHAT"
    const val OPEN_SETTINGS = "com.beykus.beygram.OPEN_NOTIFICATION_SETTINGS"
    const val OPEN_PROXY = "com.beykus.beygram.OPEN_NOTIFICATION_PROXY"
    const val DISMISS = "com.beykus.beygram.DISMISS_NOTIFICATION"
    const val REPLY = "com.beykus.beygram.REPLY_NOTIFICATION"
    const val CHAT_ID = "notification_chat_id"
    const val GROUP_ID = "notification_group_id"
    const val MAX_ID = "notification_max_id"
    const val REPLY_TEXT = "notification_reply_text"
    const val REPLY_TO = "notification_reply_to"
    private const val TOKEN = "notification_intent_token"
    fun valid(context: Context, intent: Intent): Boolean = intent.getStringExtra(TOKEN) == NotificationRuntime.get(context).preferences.intentToken()
    fun chat(context: Context, chatId: Long, groupId: Int, maxId: Int): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setAction(OPEN_CHAT).setData(Uri.parse("beygram://${context.packageName}/chat/$chatId/$groupId/$maxId"))
            .putExtra(CHAT_ID, chatId).putExtra(GROUP_ID, groupId).putExtra(MAX_ID, maxId).putExtra(TOKEN, NotificationRuntime.get(context).preferences.intentToken()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun settings(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setAction(OPEN_SETTINGS).setData(Uri.parse("beygram://${context.packageName}/notification-settings"))
            .putExtra(TOKEN, NotificationRuntime.get(context).preferences.intentToken()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun proxy(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setAction(OPEN_PROXY).setData(Uri.parse("beygram://${context.packageName}/proxy"))
            .putExtra(TOKEN, NotificationRuntime.get(context).preferences.intentToken()).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun dismiss(context: Context, groupId: Int, maxId: Int): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, NotificationDismissReceiver::class.java).setAction(DISMISS).setData(Uri.parse("beygram://${context.packageName}/dismiss/$groupId/$maxId"))
            .putExtra(GROUP_ID, groupId).putExtra(MAX_ID, maxId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    /**
     * Mutable because Android attaches the typed text to the intent, and one-shot so a listener that kept it can send only once.
     * One identity per group: a re-post updates the live action instead of leaving older ones behind. The receiver is not exported and still checks the token.
     */
    fun reply(context: Context, chatId: Long, groupId: Int, maxId: Int, replyToMessageId: Long): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, NotificationReplyReceiver::class.java).setAction(REPLY).setData(Uri.parse("beygram://${context.packageName}/reply/$groupId"))
            .putExtra(CHAT_ID, chatId).putExtra(GROUP_ID, groupId).putExtra(MAX_ID, maxId).putExtra(REPLY_TO, replyToMessageId).putExtra(TOKEN, NotificationRuntime.get(context).preferences.intentToken()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT or mutability())
    // FLAG_MUTABLE exists only from Android 12; older systems create mutable PendingIntents by default.
    private fun mutability(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
}
