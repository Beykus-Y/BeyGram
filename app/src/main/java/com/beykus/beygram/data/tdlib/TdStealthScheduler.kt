package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.StealthScheduleUnavailableException
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
     * Options for one send to [chatId]. [uploadBytes] is the size of the files still to upload: the date is fixed when the
     * request is made, but the server sees it only after the upload, so a larger upload gets a later date.
     */
    suspend fun resolve(chatId: Long, options: MessageSendOptions, uploadBytes: Long = 0L, liveLocation: Boolean = false): MessageSendOptions {
        val delay = delaySeconds() ?: return options
        if (options.scheduledAtSeconds != null || options.sendNowConsent) return options
        if (options.viewOnce || liveLocation) throw unavailable(R.string.stealth_schedule_unavailable_content)
        chatRestriction(chatId)?.let { throw unavailable(it) }
        return options.copy(scheduledAtSeconds = stealthScheduleAt(serverUnixTime(), delay, uploadBytes))
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

/** A slow mobile upload that the date allows for, in bytes per second. */
internal const val STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND = 64L * 1024

/** The longest extra lead for an upload. A larger upload still gets this, and Telegram may then send it at once. */
internal const val STEALTH_MAX_UPLOAD_ALLOWANCE_SECONDS = 6L * 60 * 60

/**
 * The scheduled date for a send made at server time [nowSeconds]: the chosen lead plus time for the upload at a slow
 * mobile speed. Telegram sends a message whose date has already passed at once, so the upload allowance errs long.
 */
internal fun stealthScheduleAt(nowSeconds: Long, delaySeconds: Int, uploadBytes: Long): Long {
    val upload = if (uploadBytes <= 0L) 0L else {
        ((uploadBytes + STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND - 1) / STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND)
            .coerceAtMost(STEALTH_MAX_UPLOAD_ALLOWANCE_SECONDS)
    }
    return nowSeconds + delaySeconds + upload
}

/** Bytes this media still has to upload: nothing for a file already on the server, such as a sticker or a picked GIF. */
internal fun OutgoingMedia.uploadBytes(): Long = when {
    remoteFileId > 0 -> 0L
    size > 0L -> size
    path.isNotBlank() -> File(path).length()
    else -> 0L
}
