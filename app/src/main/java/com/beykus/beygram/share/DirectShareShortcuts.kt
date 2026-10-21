package com.beykus.beygram.share

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.beykus.beygram.MainActivity
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview

/**
 * Android Sharing Shortcuts (Android 10+), not the obsolete ChooserTargetService.
 * These are suggestions only. ShareTargetScreen always requires a separate Send confirmation.
 */
internal object DirectShareShortcuts {
    private const val TAG = "BeyGramDirectShare"
    private const val CATEGORY = "com.beykus.beygram.share.direct"
    private const val PREFIX = "beygram-share-chat-"
    private const val MAX_VISIBLE = 4

    fun idForChat(chatId: Long): String = "$PREFIX$chatId"

    fun chatIdForShortcut(id: String?): Long =
        id?.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)?.toLongOrNull()?.takeIf { it != 0L } ?: 0L

    /** Main list order comes from Telegram, unlike alphabetical order in the regular picker. */
    fun pickTargets(chats: List<ChatPreview>): List<ChatPreview> =
        chats.asSequence().filter { it.canPost && it.order > 0L && it.title.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.order }
            .take(MAX_VISIBLE).toList()

    fun publish(context: Context, targets: List<ChatPreview>) {
        runCatching {
            val cap = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(0)
            val dynamic = targets.take(cap).mapIndexed { rank, chat ->
                val intent = Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra("beygram.share.target", chat.id)
                ShortcutInfoCompat.Builder(context, idForChat(chat.id))
                    .setShortLabel(chat.title.take(36))
                    .setLongLabel(chat.title.take(80))
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setCategories(setOf(CATEGORY))
                    .setIntent(intent)
                    .setRank(rank)
                    .setLongLived(false)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, dynamic)
        }.onFailure { Log.w(TAG, "Sharing shortcuts unavailable: ${it.javaClass.simpleName}") }
    }

    fun clear(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
            .onFailure { Log.w(TAG, "Cannot clear sharing shortcuts: ${it.javaClass.simpleName}") }
    }
}
