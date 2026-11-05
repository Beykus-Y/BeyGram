package com.beykus.beygram.data.chatmemory

/**
 * The text of a message as Chat Memory keeps it. Media is never kept: only its caption or text is, and
 * [hasAttachment] tells the reader that the message had something else attached.
 */
internal data class MemorySnapshot(val text: String, val hasAttachment: Boolean)

/** What Chat Memory learns about one message when TDLib shows it or changes it. */
internal data class MemoryObservation(
    val chatId: Long,
    val messageId: Long,
    val isOutgoing: Boolean,
    val dateSeconds: Long,
    val senderId: Long,
    val senderName: String,
    val snapshot: MemorySnapshot
)

/**
 * A message the store keeps. [deletedAtMillis] is set when the other side deleted it; a message that is not
 * deleted is only the latest text Chat Memory saw, which is what the next edit is compared with.
 */
internal data class MemorizedMessage(
    val chatId: Long,
    val messageId: Long,
    val isOutgoing: Boolean,
    val dateSeconds: Long,
    val senderId: Long,
    val senderName: String,
    val text: String,
    val hasAttachment: Boolean,
    val deletedAtMillis: Long?,
    val versionCount: Int
)

/** An earlier text of a message. [replacedAtMillis] is when Chat Memory saw the text being replaced. */
internal data class MemorizedVersion(val text: String, val hasAttachment: Boolean, val replacedAtMillis: Long)

/** The size of the stored data. [bytes] estimates the text plus a fixed cost for every row. */
internal data class ChatMemoryUsage(val messages: Int, val deletedMessages: Int, val versions: Int, val bytes: Long)
