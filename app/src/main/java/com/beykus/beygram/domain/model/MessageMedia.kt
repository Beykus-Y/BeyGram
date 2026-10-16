package com.beykus.beygram.domain.model

import java.io.Serializable

enum class MediaKind { Photo, Video, Animation, Sticker, Audio, Document, Voice, VideoNote }

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
    val isSecret: Boolean = false,
    /** Отсчёты волны голосового сообщения, каждый 0…31; для остальных типов пусто. */
    val waveform: List<Int> = emptyList(),
    /** Кружок ещё не открывали; для остальных типов всегда true. */
    val isViewed: Boolean = true
)

data class MediaFileState(
    val id: Int,
    val size: Long = 0,
    val localPath: String? = null,
    val downloadedBytes: Long = 0,
    val isDownloading: Boolean = false,
    val uploadedBytes: Long = 0,
    val isUploading: Boolean = false,
    val error: String? = null
) {
    val downloadProgress: Float get() = if (size > 0) (downloadedBytes.toFloat() / size).coerceIn(0f, 1f) else 0f
    val uploadProgress: Float get() = if (size > 0) (uploadedBytes.toFloat() / size).coerceIn(0f, 1f) else 0f
}

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
    val thumbnailHeight: Int = 0
) : Serializable
