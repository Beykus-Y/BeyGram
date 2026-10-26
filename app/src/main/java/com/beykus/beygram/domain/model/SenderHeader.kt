package com.beykus.beygram.domain.model

/**
 * Whether an incoming bubble should render the sender's name above its content.
 *
 * A private chat, a secret chat and Saved Messages already identify their only peer in the chat header, so repeating
 * the name over every bubble adds no information. A broadcast channel keeps its author label because every post there
 * is signed by the channel. In groups and supergroups the name is needed, but consecutive messages from the same
 * author form one visual block and share a single header; a change of author or an outgoing message starts a new one.
 *
 * [previous] is the chronologically preceding message in the same feed, or null when this message starts a block.
 * The message's own [ChatMessage.senderName] is never cleared: only this presentation flag depends on it.
 */
fun showSenderHeader(chat: ChatPreview, message: ChatMessage, previous: ChatMessage?): Boolean {
    if (message.isOutgoing || message.senderName.isBlank()) return false
    if (chat.isSavedMessages || !chat.isGroup) return false
    if (chat.isChannel) return true
    if (previous == null || previous.isOutgoing || previous.serviceText != null) return true
    return !previous.hasSameSenderAs(message)
}

/**
 * The same author in the same role. A user is identified by id; anonymous administrators and channel posts have no
 * user id, so their visible sender name (the group or channel title) keeps distinct senders apart.
 */
private fun ChatMessage.hasSameSenderAs(other: ChatMessage): Boolean =
    senderId == other.senderId && senderName == other.senderName
