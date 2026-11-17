package com.beykus.beygram.data.settings

import android.content.Context
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How a forum's topics are reached from an open chat. [Classic] is the topic list that has always been there. [Tabs] and
 * [Rail] keep the topics beside the open topic, so switching topics is one tap.
 */
enum class ForumNavigationMode { Classic, Tabs, Rail }

internal interface ForumNavigationStorage {
    fun value(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

private class SharedPreferencesForumNavigationStorage(context: Context, accountId: String) : ForumNavigationStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        forumNavigationPreferencesName(accountId), Context.MODE_PRIVATE
    )

    override fun value(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String) { preferences.edit().putString(key, value).apply() }
    override fun remove(key: String) { preferences.edit().remove(key).apply() }
    override fun keys(): Set<String> = preferences.all.keys.toSet()
}

/** Names are account-scoped; the original account keeps the historical un-suffixed name. */
internal fun forumNavigationPreferencesName(accountId: String): String {
    require(TdAccountStorageLayout.isValidAccountId(accountId)) { "Invalid account id for forum navigation" }
    return if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) "forum-navigation" else "forum-navigation_$accountId"
}

private const val ModeKeyPrefix = "mode:"
private const val LastTopicKeyPrefix = "last:"

internal fun forumModeKey(chatId: Long): String {
    require(chatId != 0L) { "A forum navigation mode needs a chat id" }
    return ModeKeyPrefix + chatId
}

internal fun forumLastTopicKey(chatId: Long): String {
    require(chatId != 0L) { "A forum's last topic needs a chat id" }
    return LastTopicKeyPrefix + chatId
}

/** A stored name that is not a mode is ignored; the caller then reads the chat as Classic. */
internal fun forumNavigationModeFromStored(value: String?): ForumNavigationMode? =
    ForumNavigationMode.entries.firstOrNull { it.name == value }

/** Local view choices per account and forum chat. TDLib has no per-user topic layout, so nothing here reaches the server. */
class ForumNavigationStore internal constructor(private val storage: ForumNavigationStorage) {
    constructor(context: Context, accountId: String = TdAccountStorageLayout.LEGACY_ACCOUNT_ID) : this(
        SharedPreferencesForumNavigationStorage(context, accountId)
    )

    private val _modes = MutableStateFlow(readModes())

    /** The chats that open in Tabs or Rail. A chat that is absent keeps the Classic topic list. */
    val modes: StateFlow<Map<Long, ForumNavigationMode>> = _modes.asStateFlow()

    /** Classic removes the choice, so the default needs no stored value. */
    @Synchronized
    fun setMode(chatId: Long, mode: ForumNavigationMode) {
        val key = forumModeKey(chatId)
        if (mode == ForumNavigationMode.Classic) {
            storage.remove(key)
            _modes.value = _modes.value - chatId
            return
        }
        storage.put(key, mode.name)
        _modes.value = _modes.value + (chatId to mode)
    }

    /** The topic that was open last in this chat; 0 when none is remembered. */
    fun lastTopic(chatId: Long): Long = storage.value(forumLastTopicKey(chatId))?.toLongOrNull() ?: 0L

    @Synchronized
    fun setLastTopic(chatId: Long, topicId: Long) {
        val key = forumLastTopicKey(chatId)
        if (topicId > 0L) storage.put(key, topicId.toString()) else storage.remove(key)
    }

    private fun readModes(): Map<Long, ForumNavigationMode> = storage.keys().mapNotNull { key ->
        if (!key.startsWith(ModeKeyPrefix)) return@mapNotNull null
        val chatId = key.removePrefix(ModeKeyPrefix).toLongOrNull() ?: return@mapNotNull null
        val mode = forumNavigationModeFromStored(storage.value(key)) ?: return@mapNotNull null
        if (mode == ForumNavigationMode.Classic) null else chatId to mode
    }.toMap()
}
