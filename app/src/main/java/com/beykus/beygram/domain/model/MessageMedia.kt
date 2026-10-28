package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import java.io.Serializable

enum class MediaKind { Photo, Video, Animation, Sticker, Audio, Document, Voice, VideoNote }

/** Место медиа среди вложений того же вида в чате: [number] отсчитывается от самого раннего, с 1. */
data class MediaPosition(val number: Int, val total: Int)

data class MessageMedia(
    val kind: MediaKind,
    val fileId: Int,
    val thumbnailFileId: Int? = null,
    val fileName: String = "",
    val mimeType: String = "application/octet-stream",
    val width: Int = 0,
    val height: Int = 0,
    val durationSeconds: Int = 0,
    val hasSpoiler: Boolean = false,
    /** TDLib marks media that must stay hidden until opened (secret-chat or self-destructing content). */
    val isSecret: Boolean = false,
    /** Отсчёты волны голосового сообщения, каждый 0…31; для остальных типов пусто. */
    val waveform: List<Int> = emptyList(),
    /** Кружок ещё не открывали; для остальных типов всегда true. */
    val isViewed: Boolean = true,
    /** Минипревью TDLib: крошечный JPEG, который показывают размытым, пока не загружены миниатюра и файл. */
    val minithumbnail: ByteArray? = null,
    /** Название и исполнитель из тегов аудиофайла; пусто, если тегов нет или это не музыка. */
    val title: String = "",
    val performer: String = "",
    /** Server speech recognition result. Empty while TDLib has not produced any text. */
    val transcriptionText: String = "",
    /** Non-null while recognition is running; may hold the partial text TDLib has already produced. */
    val transcriptionPending: String? = null,
    /** Structured reason for the last recognition attempt; null when no error is known. */
    val transcriptionError: SpeechRecognitionFailure? = null,
    /** The sender marked the video as fast-start streamable; playback may begin from a partial local file. */
    val supportsStreaming: Boolean = false,
    /** The message belongs to a secret chat; such content is never auto-downloaded off the visible feed. */
    val isSecretChat: Boolean = false,
    /** TDLib's sticker-set id from MessageSticker, used to open the pack instead of a generic media viewer. */
    val stickerSetId: Long = 0,
    /** Identifier of this sticker inside [stickerSetId]; zero when TDLib did not provide one. */
    val stickerId: Long = 0,
    /** Emoji TDLib associated with the sticker; empty for non-sticker media and some old stickers. */
    val stickerEmoji: String = "",
    /** The sticker may only be sent by an account with Telegram Premium. */
    val isPremiumSticker: Boolean = false
) {
    /** True while TDLib is still recognizing this voice/video note. */
    val isTranscribing: Boolean get() = transcriptionPending != null

    /** True once recognition produced text that can be shown under the bubble. */
    val hasTranscription: Boolean get() = transcriptionText.isNotBlank()
}

data class MediaFileState(
    val id: Int,
    val size: Long = 0,
    val localPath: String? = null,
    val downloadedBytes: Long = 0,
    val isDownloading: Boolean = false,
    val uploadedBytes: Long = 0,
    val isUploading: Boolean = false,
    val error: UiText? = null,
    /** TDLib's path while the download is still running; may contain only a prefix of the file. */
    val downloadPath: String? = null,
    /** Contiguous bytes from the beginning of [downloadPath]. Used by the progressive player. */
    val downloadedPrefixBytes: Long = 0
) {
    val downloadProgress: Float get() = if (size > 0) (downloadedBytes.toFloat() / size).coerceIn(0f, 1f) else 0f
    val uploadProgress: Float get() = if (size > 0) (uploadedBytes.toFloat() / size).coerceIn(0f, 1f) else 0f
}

/** A single automatic download candidate. File IDs are scoped to the repository/account that produced them. */
data class AutoDownloadRequest(
    val fileId: Int,
    val kind: MediaKind,
    val sizeBytes: Long,
    val isSecret: Boolean = false,
    val isSecretChat: Boolean = false,
    val hasSpoiler: Boolean = false
)

/** Metadata only; the selected bytes live in the application's private upload directory. */
data class OutgoingMedia(
    val path: String,
    val fileName: String,
    val mimeType: String,
    val kind: MediaKind,
    val width: Int = 0,
    val height: Int = 0,
    val durationSeconds: Int = 0,
    val thumbnailPath: String? = null,
    val size: Long = 0,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
    val waveform: ByteArray = byteArrayOf(),
    /** A file already stored on Telegram's servers; when set, TDLib sends it without downloading the bytes. */
    val remoteFileId: Int = 0,
    /** Server-side thumbnail for [remoteFileId]; zero when only a local [thumbnailPath] exists. */
    val thumbnailRemoteFileId: Int = 0,
    /** Emoji of a sticker, required by InputMessageSticker. */
    val emoji: String = "",
    /** Track tags of a [MediaKind.Audio] file sent by [remoteFileId]; empty for every other kind. */
    val title: String = "",
    val performer: String = ""
) : Serializable
