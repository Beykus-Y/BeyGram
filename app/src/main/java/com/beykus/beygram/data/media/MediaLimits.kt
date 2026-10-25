package com.beykus.beygram.data.media

import com.beykus.beygram.domain.model.MediaKind

/**
 * Hard limits shared by the importer, the camera and the composer.
 * Telegram itself accepts larger files in some modes; BeyGram keeps these values explicit
 * so the composer can explain why an attachment is sent as a file instead of media.
 */
internal object MediaLimits {
    const val MAX_ATTACHMENTS = 10
    const val MAX_PHOTO_BYTES: Long = 10L * 1024 * 1024
    const val MAX_VIDEO_BYTES: Long = 2L * 1024 * 1024 * 1024
    const val MAX_VIDEO_DURATION_SECONDS = 60 * 60
    /** Telegram rejects photo aspect ratios above 20:1, so extreme panoramas are padded. */
    const val MAX_PHOTO_ASPECT = 20.0
    const val MAX_PHOTO_SIDE = 2560
    const val PHOTO_JPEG_QUALITY = 88

    val maxPhotoMegabytes: Int get() = (MAX_PHOTO_BYTES / (1024 * 1024)).toInt()
}

/** Why a media file cannot be sent as a Telegram photo/video and has to go as a document. */
internal enum class MediaLimitIssue { PhotoTooLarge, VideoTooLarge, VideoTooLong }

internal fun mediaLimitIssue(kind: MediaKind, sizeBytes: Long, durationSeconds: Int): MediaLimitIssue? = when (kind) {
    MediaKind.Photo -> if (sizeBytes > MediaLimits.MAX_PHOTO_BYTES) MediaLimitIssue.PhotoTooLarge else null
    MediaKind.Video -> when {
        sizeBytes > MediaLimits.MAX_VIDEO_BYTES -> MediaLimitIssue.VideoTooLarge
        durationSeconds > MediaLimits.MAX_VIDEO_DURATION_SECONDS -> MediaLimitIssue.VideoTooLong
        else -> null
    }
    else -> null
}
