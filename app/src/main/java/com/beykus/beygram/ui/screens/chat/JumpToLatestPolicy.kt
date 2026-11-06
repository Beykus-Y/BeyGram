package com.beykus.beygram.ui.screens.chat

/** Explicitly honor reduced motion and avoid animating a jump across unloaded history. */
internal fun shouldAnimateJumpToLatest(motionInstant: Boolean, hasNewerMessages: Boolean): Boolean =
    !motionInstant && !hasNewerMessages
