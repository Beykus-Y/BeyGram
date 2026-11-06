package com.beykus.beygram.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A per-chat choice that overrides [ChatMemorySettings.saveAllChats]. */
enum class ChatMemoryChatMode { Save, Skip }

/**
 * Chat Memory switches of one account. Everything is off by default, so nothing is kept until the user turns the
 * feature on. [limitBytes] caps the stored data; the oldest entries go first once it is reached.
 */
data class ChatMemorySettings(
    val enabled: Boolean = false,
    /** When false, only chats with a [ChatMemoryChatMode.Save] exception are kept. */
    val saveAllChats: Boolean = true,
    val limitBytes: Long = DEFAULT_LIMIT_BYTES,
    val chats: Map<Long, ChatMemoryChatMode> = emptyMap()
) {
    companion object {
        const val MIN_LIMIT_BYTES: Long = 1024L * 1024L
        const val DEFAULT_LIMIT_BYTES: Long = 64L * 1024L * 1024L

        /** The limits offered in settings, in bytes. The UI shows them with the platform's file-size formatting. */
        val LIMIT_CHOICES: List<Long> = listOf(16L, 64L, 256L, 1024L).map { it * 1024L * 1024L }
    }
}

/**
 * Chat Memory switches for one account, held in memory and committed with one synchronous write per change, the same
 * way as the stealth switches. A stored state that cannot be read counts as off.
 */
class ChatMemorySettingsStore internal constructor(private val preferences: SharedPreferences) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ChatMemorySettings> = _state.asStateFlow()
    val current: ChatMemorySettings get() = _state.value
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Applies [transform] and commits the result. Returns false, and keeps the previous switches, when the commit
     * fails, so the screen never shows a change that was not stored.
     */
    fun update(transform: (ChatMemorySettings) -> ChatMemorySettings): Boolean {
        val (saved, changed) = synchronized(this) {
            val previous = _state.value
            val next = transform(previous).normalized()
            val written = write(next)
            if (written) _state.value = next
            Pair(written, written && next != previous)
        }
        if (changed) listeners.forEach { it() }
        return saved
    }

    /** Called after every change that was stored, outside the lock. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun load(): ChatMemorySettings = try {
        parseChatMemorySettings(preferences.all)
    } catch (_: Exception) {
        ChatMemorySettings()
    }

    private fun write(settings: ChatMemorySettings): Boolean {
        val editor = preferences.edit().clear()
        editor.putBoolean(KEY_ENABLED, settings.enabled)
        editor.putBoolean(KEY_SAVE_ALL_CHATS, settings.saveAllChats)
        editor.putLong(KEY_LIMIT_BYTES, settings.limitBytes)
        for ((chatId, mode) in settings.chats) {
            editor.putString(CHAT_KEY_PREFIX + chatId, if (mode == ChatMemoryChatMode.Save) MODE_SAVE else MODE_SKIP)
        }
        // commit() rather than apply(): the caller learns whether the choice reached the disk.
        return editor.commit()
    }

    companion object {
        private val instances = ConcurrentHashMap<String, ChatMemorySettingsStore>()

        /** One store per account, shared by the account's repository and its settings screen. */
        fun forAccount(context: Context, accountId: String): ChatMemorySettingsStore {
            require(TdAccountStorageLayout.isValidAccountId(accountId)) { "Invalid account id for chat memory settings" }
            return instances.computeIfAbsent(accountId) {
                ChatMemorySettingsStore(
                    context.applicationContext.getSharedPreferences(chatMemoryPreferencesName(accountId), Context.MODE_PRIVATE)
                )
            }
        }

        /** Forgets the in-memory store of a removed secondary account; the caller deletes its preferences. */
        fun releaseAccount(accountId: String) {
            instances.remove(accountId)
        }
    }
}

/** The legacy account keeps the plain name; each secondary account gets its own file. */
internal fun chatMemoryPreferencesName(accountId: String): String =
    if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) "chat-memory" else "chat-memory_$accountId"

internal fun parseChatMemorySettings(stored: Map<String, *>): ChatMemorySettings {
    if (stored.isEmpty()) return ChatMemorySettings()
    val chats = HashMap<Long, ChatMemoryChatMode>()
    for ((key, value) in stored) {
        if (!key.startsWith(CHAT_KEY_PREFIX)) continue
        val chatId = key.removePrefix(CHAT_KEY_PREFIX).toLongOrNull() ?: continue
        val mode = when (value) {
            MODE_SAVE -> ChatMemoryChatMode.Save
            MODE_SKIP -> ChatMemoryChatMode.Skip
            else -> continue
        }
        chats[chatId] = mode
    }
    return ChatMemorySettings(
        enabled = stored[KEY_ENABLED] as? Boolean ?: false,
        saveAllChats = stored[KEY_SAVE_ALL_CHATS] as? Boolean ?: true,
        limitBytes = stored[KEY_LIMIT_BYTES] as? Long ?: ChatMemorySettings.DEFAULT_LIMIT_BYTES,
        chats = chats
    ).normalized()
}

/** Whether the settings keep the messages of [chatId], before the facts of that chat (secret chats and the like) are checked. */
fun ChatMemorySettings.savesChat(chatId: Long): Boolean = when (chats[chatId]) {
    ChatMemoryChatMode.Save -> true
    ChatMemoryChatMode.Skip -> false
    null -> saveAllChats
}

/**
 * The settings after the user chose whether to keep the messages of [chatId]. A choice that equals [saveAllChats] removes
 * the exception instead, so [chats] only ever holds what differs from the default.
 */
fun ChatMemorySettings.withChatSaved(chatId: Long, saved: Boolean): ChatMemorySettings {
    if (saved == saveAllChats) return copy(chats = chats - chatId)
    val mode = if (saved) ChatMemoryChatMode.Save else ChatMemoryChatMode.Skip
    return copy(chats = chats + (chatId to mode))
}

private fun ChatMemorySettings.normalized(): ChatMemorySettings =
    copy(limitBytes = limitBytes.coerceAtLeast(ChatMemorySettings.MIN_LIMIT_BYTES))

private const val KEY_ENABLED = "enabled"
private const val KEY_SAVE_ALL_CHATS = "save_all_chats"
private const val KEY_LIMIT_BYTES = "limit_bytes"
private const val CHAT_KEY_PREFIX = "chat:"
private const val MODE_SAVE = "save"
private const val MODE_SKIP = "skip"
