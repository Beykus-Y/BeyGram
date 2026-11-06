package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageDraft

/**
 * The composer draft once a delayed message has been stored. It is cleared only while it still holds the text that was
 * stored: text typed while the write was running is not part of that message, so it stays in the composer.
 */
internal fun draftAfterDelayedQueue(current: MessageDraft, storedText: String): MessageDraft =
    if (current.text == storedText) MessageDraft() else current
