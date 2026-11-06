package com.beykus.beygram.ui.screens.playback

/**
 * Root and tab bars do not compete for vertical space with the on-screen keyboard.
 * A chat-integrated bar keeps its own composer/inset layout.
 * Hiding the bar never pauses playback or dismisses an already open full-player sheet.
 */
internal fun shouldShowMiniPlayer(
    hasItem: Boolean,
    hideWhenImeVisible: Boolean,
    keyboardVisible: Boolean
): Boolean = hasItem && !(hideWhenImeVisible && keyboardVisible)
