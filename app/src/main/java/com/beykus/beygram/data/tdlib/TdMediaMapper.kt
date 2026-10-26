package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GeoPoint
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageContactInfo
import com.beykus.beygram.domain.model.MessageDiceInfo
import com.beykus.beygram.domain.model.MessageLinkPreview
import com.beykus.beygram.domain.model.MessageLiveLocationInfo
import com.beykus.beygram.domain.model.MessageLocationInfo
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessagePoll
import com.beykus.beygram.domain.model.MessagePollOption
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.domain.model.MessageVenueInfo
import com.beykus.beygram.domain.model.PollVoteRestriction
import com.beykus.beygram.domain.model.SpeechRecognitionFailure
import com.beykus.beygram.domain.model.SpeechRecognitionTrial
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

internal fun TdApi.File.toMediaFileState(): MediaFileState {
    val local = local
    return MediaFileState(
        id = id,
        size = maxOf(size, expectedSize),
        localPath = local?.takeIf { it.isDownloadingCompleted }?.path?.takeIf { it.isNotBlank() },
        downloadedBytes = local?.downloadedSize ?: 0,
        isDownloading = local?.isDownloadingActive == true,
        uploadedBytes = remote?.uploadedSize ?: 0,
        isUploading = remote?.isUploadingActive == true,
        downloadPath = local?.let { file ->
            file.path?.takeIf {
                it.isNotBlank() && (file.isDownloadingCompleted || file.downloadedPrefixSize > 0L)
            }
        },
        downloadedPrefixBytes = local?.downloadedPrefixSize ?: 0
    )
}

/** TDLib присылает волну 5-битными значениями (0…31), записанными подряд, младшие биты первыми. */
internal fun decodeVoiceWaveform(bytes: ByteArray): List<Int> = List(bytes.size * 8 / 5) { index ->
    val bit = index * 5
    val low = bytes[bit / 8].toInt() and 0xFF
    val high = bytes.getOrElse(bit / 8 + 1) { 0.toByte() }.toInt() and 0xFF
    ((low or (high shl 8)) shr (bit % 8)) and 0x1F
}

/** MIME type of a sticker's file: TGS is gzipped Lottie JSON, WebM is video, and anything else is WebP. */
internal fun stickerMimeType(format: TdApi.StickerFormat): String = when (format) {
    is TdApi.StickerFormatWebm -> "video/webm"
    is TdApi.StickerFormatTgs -> "application/x-tgsticker"
    else -> "image/webp"
}

/** Domain bridge for TDLib's speech result. Error details become a structured, user-explainable reason. */
internal data class TranscriptionSnapshot(
    val text: String = "",
    val pendingText: String? = null,
    val error: SpeechRecognitionFailure? = null
)

internal fun TdApi.SpeechRecognitionResult?.toTranscriptionSnapshot(
    trial: SpeechRecognitionTrial? = null,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
    isPremiumAccount: Boolean = false,
    snapshotEligibility: Boolean = true
): TranscriptionSnapshot = when (this) {
    is TdApi.SpeechRecognitionResultText -> TranscriptionSnapshot(text = text)
    is TdApi.SpeechRecognitionResultPending -> TranscriptionSnapshot(pendingText = partialText.orEmpty())
    is TdApi.SpeechRecognitionResultError -> TranscriptionSnapshot(
        error = classifySpeechRecognitionError(error?.code, error?.message, trial, nowSeconds, isPremiumAccount,
            snapshotEligibility)
    )
    else -> TranscriptionSnapshot()
}

internal class TdMediaMapper(
    private val files: MediaFileStore,
    /** Reads the current account's Telegram quota when an asynchronous recognition error is mapped. */
    private val speechTrial: () -> SpeechRecognitionTrial? = { null },
    /** Premium status is part of the error snapshot, so a later upgrade can invalidate an older refusal. */
    private val isPremiumAccount: () -> Boolean = { false }
) {
    private data class CachedSpeechError(
        val code: Int,
        val message: String,
        val failure: SpeechRecognitionFailure
    )

    /**
     * TDLib keeps the same SpeechRecognitionResultError across history reloads. Reusing the first mapped
     * failure preserves its eligibility snapshot, while a live update is always classified afresh.
     */
    private val speechErrors = ConcurrentHashMap<Int, CachedSpeechError>()

    private fun transcriptionSnapshot(fileId: Int, result: TdApi.SpeechRecognitionResult?, fresh: Boolean): TranscriptionSnapshot {
        val snapshot = result.toTranscriptionSnapshot(
            speechTrial(),
            isPremiumAccount = isPremiumAccount(),
            snapshotEligibility = fresh
        )
        if (result !is TdApi.SpeechRecognitionResultError) {
            speechErrors.remove(fileId)
            return snapshot
        }
        val failure = snapshot.error ?: return snapshot
        val code = result.error?.code ?: 0
        val message = result.error?.message.orEmpty()
        if (!fresh) {
            speechErrors[fileId]?.takeIf { it.code == code && it.message == message }?.let {
                return TranscriptionSnapshot(error = it.failure)
            }
        }
        speechErrors[fileId] = CachedSpeechError(code, message, failure)
        return snapshot
    }

    private fun register(file: TdApi.File): Int {
        files.register(file.toMediaFileState())
        return file.id
    }
    private fun thumbnail(thumb: TdApi.Thumbnail?): Int? = thumb?.file?.let(::register)

    fun media(content: TdApi.MessageContent, freshSpeechError: Boolean = false): MessageMedia? = when (content) {
        is TdApi.MessagePhoto -> {
            val sizes = content.photo.sizes.orEmpty().filter { it.photo != null }
            val full = sizes.maxByOrNull { it.width.toLong() * it.height }
            val preview = sizes.filter { maxOf(it.width, it.height) >= 240 }.minByOrNull { it.width.toLong() * it.height }
                ?: sizes.minByOrNull { it.width.toLong() * it.height }
            full?.let { MessageMedia(MediaKind.Photo, register(it.photo), preview?.photo?.let(::register), "", "image/jpeg", it.width, it.height, hasSpoiler = content.hasSpoiler, isSecret = content.isSecret, minithumbnail = content.photo.minithumbnail?.data) }
        }
        is TdApi.MessageVideo -> content.video.let {
            MessageMedia(MediaKind.Video, register(it.video), thumbnail(it.thumbnail), it.fileName.orEmpty(), it.mimeType.orEmpty().ifBlank { "video/mp4" }, it.width, it.height, it.duration, content.hasSpoiler, content.isSecret, minithumbnail = it.minithumbnail?.data, supportsStreaming = it.supportsStreaming)
        }
        is TdApi.MessageAnimation -> content.animation.let {
            MessageMedia(MediaKind.Animation, register(it.animation), thumbnail(it.thumbnail), it.fileName.orEmpty(), it.mimeType.orEmpty().ifBlank { "video/mp4" }, it.width, it.height, it.duration, content.hasSpoiler, minithumbnail = it.minithumbnail?.data)
        }
        is TdApi.MessageDocument -> content.document.let {
            MessageMedia(MediaKind.Document, register(it.document), thumbnail(it.thumbnail), it.fileName.orEmpty(), it.mimeType.orEmpty().ifBlank { "application/octet-stream" })
        }
        is TdApi.MessageAudio -> content.audio.let {
            MessageMedia(MediaKind.Audio, register(it.audio), thumbnail(it.albumCoverThumbnail), it.fileName.orEmpty().ifBlank { it.title.orEmpty() }, it.mimeType.orEmpty().ifBlank { "audio/mpeg" }, durationSeconds = it.duration, title = it.title.orEmpty(), performer = it.performer.orEmpty())
        }
        is TdApi.MessageVoiceNote -> content.voiceNote.let {
            val transcription = transcriptionSnapshot(it.voice.id, it.speechRecognitionResult, freshSpeechError)
            MessageMedia(MediaKind.Voice, register(it.voice), fileName = "", mimeType = it.mimeType.orEmpty().ifBlank { "audio/ogg" }, durationSeconds = it.duration, waveform = decodeVoiceWaveform(it.waveform ?: ByteArray(0)),
                transcriptionText = transcription.text, transcriptionPending = transcription.pendingText, transcriptionError = transcription.error)
        }
        is TdApi.MessageVideoNote -> content.videoNote.let {
            val transcription = transcriptionSnapshot(it.video.id, it.speechRecognitionResult, freshSpeechError)
            MessageMedia(MediaKind.VideoNote, register(it.video), thumbnail(it.thumbnail), "", "video/mp4", it.length, it.length, it.duration, isSecret = content.isSecret, isViewed = content.isViewed, minithumbnail = it.minithumbnail?.data,
                transcriptionText = transcription.text, transcriptionPending = transcription.pendingText, transcriptionError = transcription.error)
        }
        is TdApi.MessageSticker -> content.sticker.let {
            MessageMedia(MediaKind.Sticker, register(it.sticker), thumbnail(it.thumbnail), "", stickerMimeType(it.format), it.width, it.height)
        }
        else -> null
    }

    /** Registers only the selected small cover photo, so the card never downloads the page's original image. */
    fun linkPreview(content: TdApi.MessageContent, isSecretChat: Boolean): MessageLinkPreview? =
        visibleLinkPreview(content, isSecretChat)
            ?.toMessageLinkPreview(::register)
            ?.takeIf { it.hasContent }

    /**
     * Polls, locations, venues, contacts, dice and the safe fallback for everything else. Known media and text
     * return null: they have their own renderers. The caller decides whether a service event suppresses this value.
     */
    fun special(content: TdApi.MessageContent): MessageSpecialContent? = when (content) {
        is TdApi.MessagePoll -> content.toSpecialPoll(content.canAddOption)
        is TdApi.MessageLocation -> MessageSpecialContent.Location(MessageLocationInfo(content.location.toGeoPoint()))
        is TdApi.MessageLiveLocation -> MessageSpecialContent.LiveLocation(
            MessageLiveLocationInfo(
                point = content.location.location.toGeoPoint(),
                livePeriodSeconds = content.location.livePeriod,
                expiresAtSeconds = System.currentTimeMillis() / 1000L + content.expiresIn.coerceAtLeast(0)
            )
        )
        is TdApi.MessageVenue -> content.venue.toSpecialVenue()
        is TdApi.MessageContact -> MessageSpecialContent.Contact(
            MessageContactInfo(
                userId = content.contact.userId,
                firstName = content.contact.firstName.orEmpty(),
                lastName = content.contact.lastName.orEmpty(),
                phone = content.contact.phoneNumber.orEmpty()
            )
        )
        is TdApi.MessageDice -> MessageSpecialContent.Dice(MessageDiceInfo(content.emoji.orEmpty(), content.value))
        is TdApi.MessageText, is TdApi.MessagePhoto, is TdApi.MessageVideo, is TdApi.MessageAnimation,
        is TdApi.MessageDocument, is TdApi.MessageAudio, is TdApi.MessageVoiceNote, is TdApi.MessageVideoNote,
        is TdApi.MessageSticker -> null
        else -> MessageSpecialContent.Unsupported(content.javaClass.simpleName)
    }

    private fun TdApi.MessagePoll.toSpecialPoll(canAddOption: Boolean): MessageSpecialContent.Poll {
        val quiz = poll.type as? TdApi.PollTypeQuiz
        val options = poll.options.orEmpty().mapIndexed { index, option ->
            MessagePollOption(
                id = option.id.orEmpty(),
                text = option.text?.text.orEmpty(),
                voterCount = option.voterCount,
                percentage = option.votePercentage,
                isChosen = option.isChosen,
                isBeingChosen = option.isBeingChosen,
                optionIndex = index
            )
        }
        // TDLib can shuffle option display order for anti-bias; votes still use the original 0-based ids.
        val order = poll.optionOrder ?: IntArray(0)
        val displayed = if (order.size == options.size && order.distinct().size == order.size &&
            order.all { it in options.indices }) order.map { options[it] } else options
        return MessageSpecialContent.Poll(
            MessagePoll(
                id = poll.id,
                question = poll.question?.text.orEmpty(),
                description = description?.text.orEmpty(),
                options = displayed,
                totalVoterCount = poll.totalVoterCount,
                isAnonymous = poll.isAnonymous,
                allowsMultipleAnswers = poll.allowsMultipleAnswers,
                allowsRevoting = poll.allowsRevoting,
                isClosed = poll.isClosed,
                canSeeResults = poll.canSeeResults,
                canGetVoters = poll.canGetVoters,
                canAddOption = canAddOption,
                isQuiz = quiz != null,
                correctOptionIndices = quiz?.correctOptionIds?.toSet().orEmpty(),
                explanation = quiz?.explanation?.text.orEmpty(),
                voteRestriction = poll.voteRestrictionReason.toVoteRestriction()
            )
        )
    }

    private fun TdApi.Venue.toSpecialVenue() = MessageSpecialContent.Venue(
        MessageVenueInfo(
            point = location.toGeoPoint(),
            title = title.orEmpty(),
            address = address.orEmpty(),
            provider = provider.orEmpty(),
            type = type.orEmpty()
        )
    )

    private fun TdApi.Location.toGeoPoint() = GeoPoint(latitude, longitude, horizontalAccuracy)

    private fun TdApi.PollVoteRestrictionReason?.toVoteRestriction(): PollVoteRestriction = when (this) {
        is TdApi.PollVoteRestrictionReasonClosed -> PollVoteRestriction.Closed
        is TdApi.PollVoteRestrictionReasonCountryRestricted -> PollVoteRestriction.CountryRestricted
        is TdApi.PollVoteRestrictionReasonMembershipRequired -> PollVoteRestriction.MembershipRequired
        is TdApi.PollVoteRestrictionReasonScheduled -> PollVoteRestriction.Scheduled
        is TdApi.PollVoteRestrictionReasonYetUnsent -> PollVoteRestriction.YetUnsent
        is TdApi.PollVoteRestrictionReasonOther -> PollVoteRestriction.Other
        else -> PollVoteRestriction.None
    }

    fun caption(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessagePhoto -> content.caption?.text.orEmpty()
        is TdApi.MessageVideo -> content.caption?.text.orEmpty()
        is TdApi.MessageAnimation -> content.caption?.text.orEmpty()
        is TdApi.MessageDocument -> content.caption?.text.orEmpty()
        is TdApi.MessageAudio -> content.caption?.text.orEmpty()
        is TdApi.MessageVoiceNote -> content.caption?.text.orEmpty()
        else -> ""
    }
}
