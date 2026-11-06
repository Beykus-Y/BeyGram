package com.beykus.beygram.domain.model

/**
 * What Chat Memory kept for a message on screen. [deletedAtMillis] is set when the other side deleted the message and it
 * stays on screen, marked as deleted. [attachmentNotKept] tells that the message had media, which is not kept, so only its
 * text is shown. [versionCount] is the number of earlier texts that were kept.
 */
data class ChatMemoryMark(
    val deletedAtMillis: Long? = null,
    val attachmentNotKept: Boolean = false,
    val versionCount: Int = 0
)

/** An earlier text of an edited message, shown in the versions list, oldest first. */
data class ChatMemoryVersion(
    val text: String,
    val attachmentNotKept: Boolean,
    val replacedAtMillis: Long
)

/**
 * What Chat Memory keeps for one account, shown in its settings. [bytes] is the estimate that the size limit is checked against.
 */
data class ChatMemoryStats(
    val messages: Int = 0,
    val deletedMessages: Int = 0,
    val versions: Int = 0,
    val bytes: Long = 0L
)
