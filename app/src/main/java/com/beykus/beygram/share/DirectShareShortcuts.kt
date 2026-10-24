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
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Sharing Shortcuts (Android 10+), not the obsolete ChooserTargetService.
 * These are suggestions only. ShareTargetScreen always requires a separate Send confirmation.
 */
internal object DirectShareShortcuts {
    private const val TAG = "BeyGramDirectShare"
    private const val CATEGORY = "com.beykus.beygram.share.direct"
    private const val PREFIX = "beygram-share-chat-"
    private const val MAX_VISIBLE = 4
    internal const val LAUNCH_CHAT = "beygram.share.target"

    fun idForChat(chatId: Long): String = "$PREFIX$chatId"

    fun chatIdForShortcut(id: String?): Long =
        id?.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)?.toLongOrNull()?.takeIf { it != 0L } ?: 0L

    /** A launcher tap sends only the chat id: open it only while that chat is still a published shortcut. */
    fun isPublished(context: Context, chatId: Long): Boolean =
        runCatching { ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id == idForChat(chatId) } }
            .getOrDefault(false)

    /** Main list order comes from Telegram, unlike alphabetical order in the regular picker. */
    fun pickTargets(chats: List<ChatPreview>): List<ChatPreview> =
        chats.asSequence().filter { it.canPost && it.order > 0L && it.title.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.order }
            .take(MAX_VISIBLE).toList()

    /** Process-wide observer: TDLib updates keep shortcuts fresh without an open Activity. */
    fun observe(context: Context, repository: TelegramRepository, scope: CoroutineScope): Job =
        scope.launch {
            // Compared as sets: a reorder inside the top four must not republish, since ShortcutManager rate-limits that.
            combine(repository.authState, repository.chats) { auth, chats ->
                when {
                    // Ready can arrive before the chat list loads; an empty set then means "still loading", so it keeps the last one.
                    auth == AuthState.Ready -> pickTargets(chats).map { it.id to it.title }.toSet().takeIf { it.isNotEmpty() }
                    // Only a sign-out (or an in-progress QR login) clears shortcuts. Idle, loading, timeouts and errors keep the last published set.
                    auth is AuthState.WaitingForPhone || auth is AuthState.WaitingForQr ||
                        auth is AuthState.WaitingForCode ||
                        auth is AuthState.WaitingForPassword -> emptySet()
                    else -> null
                }
            }.filterNotNull().distinctUntilChanged().collectLatest { identity ->
                withContext(Dispatchers.IO) {
                    if (identity.isEmpty()) clear(context)
                    else {
                        val targets = pickTargets(repository.chats.value)
                        // The compat API signals rate limiting with false rather than an exception.
                        // Retry modestly while this publication remains the latest desired state.
                        for (attempt in 0..2) {
                            if (publish(context, targets)) break
                            if (attempt < 2) delay(5000L * (attempt + 1))
                        }
                    }
                }
            }
        }

    fun publish(context: Context, targets: List<ChatPreview>): Boolean {
        return runCatching {
            val cap = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(0)
            val dynamic = targets.take(cap).mapIndexed { rank, chat ->
                val intent = Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(LAUNCH_CHAT, chat.id)
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
            .getOrDefault(false).also { if (!it) Log.w(TAG, "Sharing shortcuts rejected or rate limited") }
    }

    fun clear(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
            .onFailure { Log.w(TAG, "Cannot clear sharing shortcuts: ${it.javaClass.simpleName}") }
    }
}
