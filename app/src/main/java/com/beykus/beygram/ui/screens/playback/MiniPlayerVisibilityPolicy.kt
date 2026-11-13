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

/** Mutually exclusive visual states for the non-rail mini-player slot. */
internal enum class NowPlayingBarSlot {
    Hidden,
    MiniPlayer,
    UndoClose
}

/**
 * Resolves the single active panel for the mini-player slot so the player bar and its undo strip
 * can never be mounted as two separate panels at the same time.
 */
internal fun resolveNowPlayingBarSlot(
    hasItem: Boolean,
    undoAvailable: Boolean,
    hideWhenImeVisible: Boolean,
    keyboardVisible: Boolean,
    /** A listener in a live BG Wave gets the bar even with no track; the wave bar shows the Stop action. */
    waveListening: Boolean = false
): NowPlayingBarSlot = when {
    hasItem || waveListening -> if (hideWhenImeVisible && keyboardVisible) NowPlayingBarSlot.Hidden else NowPlayingBarSlot.MiniPlayer
    // Undo stays reachable even if the keyboard opens during the short undo window.
    undoAvailable -> NowPlayingBarSlot.UndoClose
    else -> NowPlayingBarSlot.Hidden
}
