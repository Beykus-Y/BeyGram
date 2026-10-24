package com.beykus.beygram.data.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Global decorative animation switches. They are intentionally account-independent. */
data class ChatAnimationSettings(
    val animatedEmoji: Boolean = true,
    val messageEffects: Boolean = true
)

internal interface ChatAnimationSettingsStorage {
    fun readBoolean(key: String, defaultValue: Boolean): Boolean
    fun writeBoolean(key: String, value: Boolean)
}

private class SharedPreferencesChatAnimationSettingsStorage(context: Context) : ChatAnimationSettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences("chat-animations", Context.MODE_PRIVATE)
    override fun readBoolean(key: String, defaultValue: Boolean): Boolean = preferences.getBoolean(key, defaultValue)
    override fun writeBoolean(key: String, value: Boolean) { preferences.edit().putBoolean(key, value).apply() }
}

/**
 * Persists the two animation switches and exposes them as a hot [StateFlow]. A toggle is visible to an already open
 * chat on the next frame; no restart or reopen is needed.
 */
class ChatAnimationSettingsStore internal constructor(private val storage: ChatAnimationSettingsStorage) {
    constructor(context: Context) : this(SharedPreferencesChatAnimationSettingsStorage(context))

    private val _state = MutableStateFlow(read())
    val state: StateFlow<ChatAnimationSettings> = _state.asStateFlow()

    @Synchronized
    fun setAnimatedEmoji(enabled: Boolean) = update(_state.value.copy(animatedEmoji = enabled))

    @Synchronized
    fun setMessageEffects(enabled: Boolean) = update(_state.value.copy(messageEffects = enabled))

    @Synchronized
    internal fun update(settings: ChatAnimationSettings) {
        storage.writeBoolean(ANIMATED_EMOJI_KEY, settings.animatedEmoji)
        storage.writeBoolean(MESSAGE_EFFECTS_KEY, settings.messageEffects)
        _state.value = settings
    }

    private fun read() = ChatAnimationSettings(
        animatedEmoji = storage.readBoolean(ANIMATED_EMOJI_KEY, true),
        messageEffects = storage.readBoolean(MESSAGE_EFFECTS_KEY, true)
    )

    internal companion object {
        @VisibleForTesting
        const val ANIMATED_EMOJI_KEY = "animated_emoji"
        @VisibleForTesting
        const val MESSAGE_EFFECTS_KEY = "message_effects"
    }
}
