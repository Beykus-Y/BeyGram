package com.beykus.beygram.ui.screens.playback

/**
 * Detached player bars do not compete for vertical space with the on-screen keyboard.
 * Bars hosted inside the chat or tab scaffold keep their own inset/layout policy.
 * Hiding the bar never pauses playback or dismisses an already open full-player sheet.
 */
internal fun shouldShowMiniPlayer(
    hasItem: Boolean,
    bottomInset: Boolean,
    keyboardVisible: Boolean
): Boolean = hasItem && !(bottomInset && keyboardVisible)
