package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ComposerSendUncertainException
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.OutgoingMedia

/**
 * Everything one tap on Send commits to. It is built synchronously from the composer at tap time, and every batch of
 * that send reads only this object: a later edit, reply or topic change in the composer cannot move the rest of it.
 * [items] keeps the staged order; each pair is (staged item, the exact media that is sent, quality already applied).
 */
internal data class AttachmentSendSnapshot(
    val chatId: Long,
    val forumTopicId: Long,
    val replyToMessageId: Long?,
    val caption: String,
    val captionEntities: List<MessageTextEntity>,
    val options: MessageSendOptions,
    val items: List<Pair<OutgoingMedia, OutgoingMedia>>
)

internal sealed interface AttachmentSendOutcome {
    data class Completed(val sentCount: Int) : AttachmentSendOutcome

    /**
     * [sentCount] and [total] count items, not batches. [remaining] is every item that was not confirmed, including the
     * batch that failed. [captionConsumed] is false only while the caption has not gone out yet.
     * [uncertain] means TDLib may have accepted the failed batch, so nothing after it may be sent automatically.
     */
    data class Failed(
        val sentCount: Int,
        val total: Int,
        val error: Throwable?,
        val uncertain: Boolean,
        val remaining: List<Pair<OutgoingMedia, OutgoingMedia>>,
        val captionConsumed: Boolean
    ) : AttachmentSendOutcome
}

/**
 * Sends [items] batch by batch, in order, and stops at the first failure. The caption and its entities go only with the
 * first batch; once a batch is confirmed the caption counts as consumed and later batches get an empty caption.
 * [onBatchConfirmed] receives the staged items of each confirmed batch, and [onCaptionConsumed] receives the caption text
 * once it has been confirmed (it is not called for an empty caption). Exceptions other than [Result] failures propagate.
 */
internal suspend fun runAttachmentSend(
    items: List<Pair<OutgoingMedia, OutgoingMedia>>,
    caption: String,
    captionEntities: List<MessageTextEntity>,
    send: suspend (media: List<OutgoingMedia>, caption: String, entities: List<MessageTextEntity>) -> Result<Unit>,
    onBatchConfirmed: (List<OutgoingMedia>) -> Unit,
    onCaptionConsumed: (String) -> Unit
): AttachmentSendOutcome {
    var remaining = items
    var sentCount = 0
    var captionConsumed = false
    for (batch in attachmentSendBatches(items)) {
        val batchCaption = if (captionConsumed) "" else caption
        val batchEntities = if (captionConsumed) emptyList() else captionEntities
        val result = send(batch.map { it.second }, batchCaption, batchEntities)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            return AttachmentSendOutcome.Failed(
                sentCount = sentCount,
                total = items.size,
                error = error,
                uncertain = error is ComposerSendUncertainException,
                remaining = remaining,
                captionConsumed = captionConsumed
            )
        }
        onBatchConfirmed(batch.map { it.first })
        sentCount += batch.size
        remaining = remaining.drop(batch.size)
        if (!captionConsumed) {
            captionConsumed = true
            if (caption.isNotEmpty()) onCaptionConsumed(caption)
        }
    }
    return AttachmentSendOutcome.Completed(sentCount)
}

/** The unsent rest of [this] after a failed run. The caption is kept only while it has not been consumed. */
internal fun AttachmentSendSnapshot.remainderAfter(
    outcome: AttachmentSendOutcome.Failed,
    options: MessageSendOptions
): AttachmentSendSnapshot = copy(
    items = outcome.remaining,
    caption = if (outcome.captionConsumed) "" else caption,
    captionEntities = if (outcome.captionConsumed) emptyList() else captionEntities,
    options = options
)

/** Clears the composer text only while it still equals the caption that was sent; text typed meanwhile stays. */
internal fun MessageDraft.withAttachmentCaptionConsumed(sentCaption: String): MessageDraft =
    if (text == sentCaption) copy(text = "", entities = emptyList()) else this

/** Clears the reply only while it is still the message this send answered. */
internal fun MessageDraft.withAttachmentReplyConsumed(sentReplyId: Long): MessageDraft =
    if (replyToMessageId == sentReplyId) copy(replyToMessageId = null) else this
