package com.beykus.beygram.data.settings

import android.content.Context
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.chatWallpaperFromStored
import com.beykus.beygram.data.appearance.encode
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local per-chat appearance overrides. TDLib has no per-chat wallpaper API, and the locally hidden pinned bar is the
 * same kind of state: it belongs to this chat on this account and survives restarts, but it is not an unpin.
 */
data class ChatOverride(
    val wallpaper: ChatWallpaper? = null,
    /** The pinned bar is hidden locally. The messages stay pinned on the server; the bar can be brought back. */
    val pinnedBarHidden: Boolean = false
)

internal interface ChatOverrideStorage {
    fun value(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

private class SharedPreferencesChatOverrideStorage(context: Context, accountId: String) : ChatOverrideStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        chatOverridePreferencesName(accountId), Context.MODE_PRIVATE
    )

    override fun value(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String) { preferences.edit().putString(key, value).apply() }
    override fun remove(key: String) { preferences.edit().remove(key).apply() }
    override fun keys(): Set<String> = preferences.all.keys.toSet()
}

/** Names are account-scoped; the original account keeps the historical un-suffixed name. */
internal fun chatOverridePreferencesName(accountId: String): String {
    require(TdAccountStorageLayout.isValidAccountId(accountId)) { "Invalid account id for chat overrides" }
    return if (accountId == "legacy") "chat-overrides" else "chat-overrides_$accountId"
}

internal fun chatWallpaperKey(chatId: Long): String {
    require(chatId != 0L) { "A chat override needs a chat id" }
    return "wallpaper:$chatId"
}

internal fun pinnedBarHiddenKey(chatId: Long): String {
    require(chatId != 0L) { "A chat override needs a chat id" }
    return "pinned-hidden:$chatId"
}

class ChatOverridesStore internal constructor(private val storage: ChatOverrideStorage) {
    constructor(context: Context, accountId: String = "legacy") : this(
        SharedPreferencesChatOverrideStorage(context, accountId)
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Map<Long, ChatOverride>> = _state.asStateFlow()

    /** null restores the global wallpaper for this chat. */
    @Synchronized
    fun setWallpaper(chatId: Long, wallpaper: ChatWallpaper?) {
        val key = chatWallpaperKey(chatId)
        // ChatWallpaper.None is a meaningful override (no wallpaper), so only null means "use global".
        if (wallpaper == null) {
            storage.remove(key)
        } else {
            storage.put(key, wallpaper.encode())
        }
        publish(chatId)
    }

    /**
     * Hides or restores the chat's pinned bar locally. Hiding is not unpinning: the pins stay on the server, and a new
     * look at the pinned list can bring the bar back.
     */
    @Synchronized
    fun setPinnedBarHidden(chatId: Long, hidden: Boolean) {
        val key = pinnedBarHiddenKey(chatId)
        if (hidden) storage.put(key, "1") else storage.remove(key)
        publish(chatId)
    }

    /** Full reset for the settings sheet: wallpaper and any other per-chat local state. */
    @Synchronized
    fun reset(chatId: Long) {
        storage.remove(chatWallpaperKey(chatId))
        storage.remove(pinnedBarHiddenKey(chatId))
        _state.value = _state.value - chatId
    }

    /** Re-reads every key of one chat and updates the state map; a chat with no keys left drops out of it. */
    private fun publish(chatId: Long) {
        _state.value = readOne(chatId)?.let { _state.value + (chatId to it) } ?: (_state.value - chatId)
    }

    private fun read(): Map<Long, ChatOverride> = storage.keys().mapNotNull { key ->
        val chatId = key.substringAfter(':').toLongOrNull()?.takeIf { key.substringBefore(':') in storedKeyPrefixes } ?: return@mapNotNull null
        readOne(chatId)?.let { chatId to it }
    }.toMap()

    private fun readOne(chatId: Long): ChatOverride? {
        val wallpaper = storage.value(chatWallpaperKey(chatId))?.let { chatWallpaperFromStored(it) }
        val pinnedHidden = storage.value(pinnedBarHiddenKey(chatId)) != null
        if (wallpaper == null && !pinnedHidden) return null
        return ChatOverride(wallpaper = wallpaper, pinnedBarHidden = pinnedHidden)
    }

    private companion object {
        val storedKeyPrefixes = setOf("wallpaper", "pinned-hidden")
    }
}
