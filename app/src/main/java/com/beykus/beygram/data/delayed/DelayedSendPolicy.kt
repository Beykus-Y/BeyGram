package com.beykus.beygram.data.delayed

import com.beykus.beygram.domain.model.MessageTextEntity

/** Where a delayed text message is in its life. Only [Queued] messages wait for their time; the others wait for the user. */
enum class DelayedSendStatus {
    /** Waiting for its time, and for an open chat of the account to send it. */
    Queued,

    /** The send request is in flight. Stored before the request, so a restart cannot resend it silently. */
    Dispatching,

    /** The send request failed. It is not retried by itself. */
    Failed,

    /** The app stopped while the request was in flight, so the message may already be on the server. Only the user resends it. */
    Uncertain
}

/** One delayed plain text message. [topicId] and [replyToMessageId] are 0 when there is none. */
data class DelayedSendEntry(
    val id: String,
    val accountId: String,
    val chatId: Long,
    val topicId: Long,
    val replyToMessageId: Long,
    val text: String,
    val entities: List<MessageTextEntity>,
    val disableNotification: Boolean,
    val createdAtMillis: Long,
    /** Wall-clock time in milliseconds from which the message may be sent. */
    val notBeforeMillis: Long,
    val status: DelayedSendStatus
)

/** A stored message older than this is dropped when the outbox is read, so an unsent message cannot wait forever. */
internal const val DELAYED_SEND_TTL_MILLIS = 24L * 60 * 60 * 1000

/** The most messages one account can hold at once. */
internal const val DELAYED_SEND_MAX_ENTRIES = 50

/** Only a plain text message from the composer is delayed. Attachments, stickers, edits, scheduled sends and empty text go out at once. */
internal fun delayedSendEligible(
    delaySeconds: Int,
    hasAttachments: Boolean,
    isEdit: Boolean,
    scheduled: Boolean,
    hasSticker: Boolean,
    text: String
): Boolean = delaySeconds > 0 && !hasAttachments && !isEdit && !scheduled && !hasSticker && text.isNotBlank()

/** Queued messages whose time has come, oldest first. */
internal fun dueEntries(entries: List<DelayedSendEntry>, nowMillis: Long): List<DelayedSendEntry> =
    entries.filter { it.status == DelayedSendStatus.Queued && it.notBeforeMillis <= nowMillis }
        .sortedBy { it.createdAtMillis }

/**
 * The stored messages after a load. [heldAtLoad] are queued messages whose time had already passed when the app started
 * with "hide online" on: they are not sent by themselves, only when the user chooses to send them.
 */
internal data class DelayedSendLoad(val entries: List<DelayedSendEntry>, val heldAtLoad: Set<String>)

/**
 * Applies the restart rules. A message that was in flight may already have been sent, so it becomes [DelayedSendStatus.Uncertain]
 * and is never resent by itself.
 */
internal fun reconcileOnLoad(entries: List<DelayedSendEntry>, nowMillis: Long, hideOnline: Boolean): DelayedSendLoad {
    val reconciled = entries.map { entry ->
        when (entry.status) {
            DelayedSendStatus.Dispatching -> entry.copy(status = DelayedSendStatus.Uncertain)
            DelayedSendStatus.Queued -> clampNotBefore(entry, nowMillis)
            DelayedSendStatus.Failed, DelayedSendStatus.Uncertain -> entry
        }
    }
    val held: Set<String> = if (hideOnline) dueEntries(reconciled, nowMillis).map { it.id }.toSet() else emptySet()
    return DelayedSendLoad(reconciled, held)
}

/**
 * When the wall clock has jumped back, a message due much later than its delay allows is brought forward to one delay
 * from now. The in-process wait uses the monotonic clock, so this only matters for the stored times.
 */
internal fun clampNotBefore(entry: DelayedSendEntry, nowMillis: Long): DelayedSendEntry {
    val delayMillis = (entry.notBeforeMillis - entry.createdAtMillis).coerceAtLeast(0L)
    return if (entry.notBeforeMillis > nowMillis + 2 * delayMillis) {
        entry.copy(notBeforeMillis = nowMillis + delayMillis)
    } else {
        entry
    }
}
