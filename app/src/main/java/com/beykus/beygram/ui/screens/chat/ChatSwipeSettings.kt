package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Secondary opposite-direction swipe action on a message bubble (primary swipe always replies). */
internal enum class MessageSecondarySwipeAction { QuickReaction, SaveToSaved, Off }

internal object ChatSwipeStore {
    private const val PREFS_NAME = "beygram_chat_swipe"
    private const val KEY_SECONDARY = "secondary_action"

    private val lock = Any()
    @Volatile
    private var loaded = false
    private val _action = MutableStateFlow(MessageSecondarySwipeAction.QuickReaction)

    fun flow(context: Context): StateFlow<MessageSecondarySwipeAction> {
        ensureLoaded(context)
        return _action.asStateFlow()
    }

    fun set(context: Context, action: MessageSecondarySwipeAction) {
        ensureLoaded(context)
        synchronized(lock) { _action.value = action }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SECONDARY, action.name)
            .apply()
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val stored = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SECONDARY, null)
                ?.let { runCatching { MessageSecondarySwipeAction.valueOf(it) }.getOrNull() }
                ?: MessageSecondarySwipeAction.QuickReaction
            _action.value = stored
            loaded = true
        }
    }
}

@Composable
internal fun rememberSecondarySwipeAction(): MessageSecondarySwipeAction {
    val context = LocalContext.current
    val flow = remember(context) { ChatSwipeStore.flow(context) }
    return flow.collectAsStateWithLifecycle().value
}
