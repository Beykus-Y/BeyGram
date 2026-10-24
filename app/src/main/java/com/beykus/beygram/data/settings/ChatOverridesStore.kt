package com.beykus.beygram.data.settings

import android.content.Context
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.chatWallpaperFromStored
import com.beykus.beygram.data.appearance.encode
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local per-chat appearance overrides. TDLib has no per-chat wallpaper API. */
data class ChatOverride(val wallpaper: ChatWallpaper? = null)

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
            _state.value = _state.value - chatId
            return
        }
        storage.put(key, wallpaper.encode())
        _state.value = _state.value + (chatId to (readOne(chatId) ?: ChatOverride()))
    }

    /** Full reset for the settings sheet: wallpaper and any future per-chat local state. */
    @Synchronized
    fun reset(chatId: Long) {
        storage.remove(chatWallpaperKey(chatId))
        _state.value = _state.value - chatId
    }

    private fun read(): Map<Long, ChatOverride> = storage.keys().mapNotNull { key ->
        val chatId = key.removePrefix("wallpaper:").toLongOrNull()?.takeIf { key.startsWith("wallpaper:") } ?: return@mapNotNull null
        readOne(chatId)?.let { chatId to it }
    }.toMap()

    private fun readOne(chatId: Long): ChatOverride? =
        storage.value(chatWallpaperKey(chatId))?.let { ChatOverride(chatWallpaperFromStored(it)) }
}
