package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.StealthScheduleUnavailableException
import kotlinx.coroutines.delay
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * Stealth sending through Telegram's own scheduled messages (#541). With the switch on, a send is turned into a
 * server-side scheduled message right before its request: Telegram keeps it in the chat's scheduled queue and publishes
 * it itself, so the app may be closed by then. Nothing falls back to an ordinary send. A chat or content that cannot be
 * scheduled, or a refused date, fails with [StealthScheduleUnavailableException] unless the user agreed to send now.
 *
 * [delaySeconds] is read on every send, so a changed switch applies to the next one.
 */
internal class TdStealthScheduler(private val request: TdRequests, private val delaySeconds: () -> Int?) {
    /** True when the next send is scheduled on the server unless it carries its own date or the user's consent. */
    fun active(): Boolean = delaySeconds() != null

    /**
     * Options for one send to [chatId]. Local [media] is uploaded first and the date is fixed only afterwards: Telegram
     * sees the date when the upload ends, and a date that has passed by then means an immediate send. The returned media
     * point at the uploaded files; only the final part and the thumbnail are left, and the date allows for them.
     */
    suspend fun resolve(
        chatId: Long, options: MessageSendOptions, media: List<OutgoingMedia> = emptyList(), liveLocation: Boolean = false
    ): StealthSend {
        val lead = delaySeconds() ?: return StealthSend(options, media, scheduled = false)
        if (options.scheduledAtSeconds != null || options.sendNowConsent) return StealthSend(options, media, scheduled = false)
        if (options.viewOnce || liveLocation) throw unavailable(R.string.stealth_schedule_unavailable_content)
        chatRestriction(chatId)?.let { throw unavailable(it) }
        val uploaded = media.map { preupload(it) }
        val remaining = uploaded.sumOf { it.second } + media.sumOf { it.thumbnailBytes() }
        val at = stealthScheduleAt(serverUnixTime(), lead, remaining)
        return StealthSend(options.copy(scheduledAtSeconds = at), uploaded.map { it.first }, scheduled = true)
    }

    /**
     * A failed message is resent as it was. With stealth sending on, that is allowed only for a message still waiting in the
     * scheduled queue far enough ahead; anything else would go out at once, so it needs the user's consent.
     */
    suspend fun checkResend(chatId: Long, messageIds: List<Long>, sendNowConsent: Boolean) {
        if (delaySeconds() == null || sendNowConsent) return
        val now = serverUnixTime()
        val allScheduled = messageIds.all { id ->
            val state = request(TdApi.GetMessage(chatId, id)).schedulingState as? TdApi.MessageSchedulingStateSendAtDate
            state != null && state.sendDate > now + MessageSendOptions.MIN_SCHEDULE_LEAD_SECONDS
        }
        if (!allScheduled) throw unavailable(R.string.stealth_schedule_retry)
    }

    /**
     * Uploads one local file ahead of the send and waits until TDLib holds it (it keeps the last part until the message is
     * sent). Returns the media sent by the file's id and the bytes still left to upload. A stalled upload fails, so the
     * user decides instead of the date running out.
     */
    private suspend fun preupload(media: OutgoingMedia): Pair<OutgoingMedia, Long> {
        if (media.remoteFileId > 0 || media.path.isBlank()) return media to 0L
        var file = request(TdApi.PreliminaryUploadFile(TdApi.InputFileLocal(media.path), uploadFileType(media.kind), PRELIMINARY_UPLOAD_PRIORITY))
        var lastProgress = System.nanoTime()
        var lastUploaded = -1L
        while (!preuploadDone(file)) {
            if (file.remote.uploadedSize != lastUploaded) {
                lastUploaded = file.remote.uploadedSize
                lastProgress = System.nanoTime()
            } else if (System.nanoTime() - lastProgress > PRELIMINARY_UPLOAD_STALL_NANOS) {
                runCatching { request(TdApi.CancelPreliminaryUploadFile(file.id)) }
                throw unavailable(R.string.stealth_schedule_upload_failed)
            }
            delay(PRELIMINARY_UPLOAD_POLL_MILLIS)
            file = request(TdApi.GetFile(file.id))
        }
        val total = maxOf(file.size, file.expectedSize, media.size)
        return media.copy(remoteFileId = file.id) to (total - file.remote.uploadedSize).coerceAtLeast(0L)
    }

    /**
     * Runs a send that [resolve] scheduled. A refusal that names scheduling becomes [StealthScheduleUnavailableException],
     * so the caller can ask the user instead of reporting a plain failure.
     */
    suspend fun <T> scheduledRequest(scheduled: Boolean, block: suspend () -> T): T {
        if (!scheduled) return block()
        return try {
            block()
        } catch (error: TdRequestError) {
            if (error.message?.contains("schedul", ignoreCase = true) == true) {
                throw StealthScheduleUnavailableException(UiText.Res(R.string.stealth_schedule_failed), error)
            }
            throw error
        }
    }

    /**
     * Telegram cannot schedule in secret chats, in chats where messages cost Stars, or in channel direct messages.
     * Checked before the request, so such a send never reaches the server as an ordinary message.
     */
    private suspend fun chatRestriction(chatId: Long): Int? = when (val type = request(TdApi.GetChat(chatId)).type) {
        is TdApi.ChatTypeSecret -> R.string.stealth_schedule_unavailable_secret
        is TdApi.ChatTypeSupergroup -> {
            val supergroup = request(TdApi.GetSupergroup(type.supergroupId))
            when {
                supergroup.isDirectMessagesGroup -> R.string.stealth_schedule_unavailable_chat
                supergroup.paidMessageStarCount > 0 -> R.string.stealth_schedule_unavailable_paid
                else -> null
            }
        }
        is TdApi.ChatTypePrivate ->
            if (request(TdApi.GetUserFullInfo(type.userId)).outgoingPaidMessageStarCount > 0) R.string.stealth_schedule_unavailable_paid else null
        else -> null
    }

    /** Telegram's clock as TDLib knows it; a device clock that is behind would otherwise produce a date in the past. */
    private suspend fun serverUnixTime(): Long {
        val value = request(TdApi.GetOption("unix_time")) as? TdApi.OptionValueInteger
            ?: throw unavailable(R.string.stealth_schedule_failed)
        return value.value
    }

    private fun unavailable(text: Int) = StealthScheduleUnavailableException(UiText.Res(text))
}

/** A send after [TdStealthScheduler.resolve]: [scheduled] is true when stealth mode added the server-side date. */
internal class StealthSend(val options: MessageSendOptions, val media: List<OutgoingMedia>, val scheduled: Boolean)

private const val PRELIMINARY_UPLOAD_PRIORITY = 16
private const val PRELIMINARY_UPLOAD_POLL_MILLIS = 500L
/** An upload that makes no progress for this long has failed. */
private const val PRELIMINARY_UPLOAD_STALL_NANOS = 60_000_000_000L

/**
 * TDLib finishes a preliminary upload only when the file is sent, so an upload that stopped after sending bytes, or has
 * them all, is as far as it goes ahead of the send.
 */
internal fun preuploadDone(file: TdApi.File): Boolean {
    val remote = file.remote ?: return false
    if (remote.isUploadingCompleted) return true
    val total = maxOf(file.size, file.expectedSize)
    return (total > 0L && remote.uploadedSize >= total) || (remote.uploadedSize > 0L && !remote.isUploadingActive)
}

internal fun uploadFileType(kind: MediaKind): TdApi.FileType = when (kind) {
    MediaKind.Photo -> TdApi.FileTypePhoto()
    MediaKind.Video -> TdApi.FileTypeVideo()
    MediaKind.Animation -> TdApi.FileTypeAnimation()
    MediaKind.Sticker -> TdApi.FileTypeSticker()
    MediaKind.Audio -> TdApi.FileTypeAudio()
    MediaKind.Document -> TdApi.FileTypeDocument()
    MediaKind.Voice -> TdApi.FileTypeVoiceNote()
    MediaKind.VideoNote -> TdApi.FileTypeVideoNote()
}

/** A local thumbnail is uploaded with the message, after the date is fixed. */
private fun OutgoingMedia.thumbnailBytes(): Long =
    if (thumbnailRemoteFileId > 0) 0L else thumbnailPath?.let { File(it).length() } ?: 0L

/** A slow mobile upload that the date allows for, in bytes per second. */
internal const val STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND = 64L * 1024

/** The longest extra lead for what is left of an upload after the preliminary upload. */
internal const val STEALTH_MAX_UPLOAD_ALLOWANCE_SECONDS = 6L * 60 * 60

/**
 * The scheduled date for a send made at server time [nowSeconds]: the chosen lead plus time for what is left to upload
 * at a slow mobile speed. Telegram sends a message whose date has already passed at once, so the allowance errs long.
 */
internal fun stealthScheduleAt(nowSeconds: Long, delaySeconds: Int, uploadBytes: Long): Long {
    val upload = if (uploadBytes <= 0L) 0L else {
        ((uploadBytes + STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND - 1) / STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND)
            .coerceAtMost(STEALTH_MAX_UPLOAD_ALLOWANCE_SECONDS)
    }
    return nowSeconds + delaySeconds + upload
}

