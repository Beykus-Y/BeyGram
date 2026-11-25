package com.beykus.beygram.data.media

/**
 * Three capabilities that used to be one condition. They are deliberately separate, because a video can be worth
 * sending even when this device cannot re-encode it, and it can be streamable even when its codec is not the one the
 * compressor prefers.
 *
 * 1. [videoCanBeSentAsVideoMessage] — can the file become a Telegram video message at all?
 * 2. [videoCanBeCompressedByThisDevice] — may the composer offer/perform a local transcode?
 * 3. [Mp4StreamingProbe] — can the player start before the whole file arrives?
 */

/** Containers Telegram stores as a video message. MKV/WebM/MOV are not confirmed and stay documents. */
private val VIDEO_MESSAGE_CONTAINERS = setOf("video/mp4")

/**
 * A Telegram video message is a file plus a video track. TDLib's `inputVideo` puts no codec restriction on it:
 * "The video is expected to be re-encoded to MPEG4 format with H.264 codec by the sender" is a compatibility
 * recommendation for senders, not a requirement of the API, and Telegram's own clients send HEVC video messages.
 *
 * Therefore the codec decides only whether this device will *re-encode* the file, never whether the message is a
 * video. Re-encoding is offered separately by the composer and never changes the type of the message.
 */
internal fun videoCanBeSentAsVideoMessage(mimeType: String, videoCodecs: List<String>): Boolean =
    mimeType in VIDEO_MESSAGE_CONTAINERS && videoCodecs.isNotEmpty()

/**
 * Whether the source is already the codec this pipeline targets, so re-encoding would only lose quality. Any other
 * video codec is still *eligible* for compression (the device may decode HEVC/AV1 and encode H.264), but nothing is
 * promised: a failed transcode keeps the original file and the video message type.
 */
internal fun videoIsAlreadyTargetCodec(videoCodecs: List<String>): Boolean =
    videoCodecs.isNotEmpty() && videoCodecs.all { it == TARGET_VIDEO_CODEC }

internal const val TARGET_VIDEO_CODEC = "video/avc"