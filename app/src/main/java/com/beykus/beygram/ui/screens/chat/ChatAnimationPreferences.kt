package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.compositionLocalOf

/** Chat-scoped copy of the two global animation switches; reducing motion is evaluated separately per composition. */
data class ChatAnimationPreferences(
    val animatedEmoji: Boolean = true,
    val messageEffects: Boolean = true
)

internal val LocalChatAnimationPreferences = compositionLocalOf { ChatAnimationPreferences() }
