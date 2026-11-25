package com.beykus.beygram.data.media

/**
 * Three capabilities that must not be one condition. A video can be worth sending even when this device cannot
 * re-encode it, and it can be streamable even when its codec is not the one the compressor prefers.
 *
 * 1. [videoCanBeSentAsVideoMessage] — may the file become a Telegram video message right away?
 * 2. [videoIsAlreadyTargetCodec] / [videoCodecNeedsConversion] — should the composer offer a local transcode?
 * 3. [Mp4StreamingProbe] — can a player start before the whole file arrives?
 */

/** Containers Telegram stores as a video message. MKV/WebM/MOV are not confirmed and stay documents. */
private val VIDEO_MESSAGE_CONTAINERS = setOf("video/mp4")

internal const val TARGET_VIDEO_CODEC = "video/avc"
internal const val HEVC_VIDEO_CODEC = "video/hevc"

/**
 * How well a source codec is known to work in a Telegram video message.
 *
 * - [Avc] — H.264, the codec Telegram itself targets. Always sent as a video, never re-encoded for compatibility.
 * - [Hevc] — HEVC. Sent as a video: Telegram Android ships an HEVC encoder
 *   (`MediaCodec.createEncoderByType("video/hevc")`) and iOS decodes HEVC natively, so current clients play it.
 * - [Unconfirmed] — AV1, VP9, MPEG-4 Part 2 and anything unknown. TDLib will accept the upload, but no upstream
 *   Telegram client is known to decode these inside a video message. The composition is *not* promised to work, so the
 *   file is imported as a document and the composer offers a transcode to H.264 instead.
 */
internal enum class VideoCodecClass { Avc, Hevc, Unconfirmed }

internal fun videoCodecClass(videoCodecs: List<String>): VideoCodecClass = when {
    videoCodecs.isEmpty() -> VideoCodecClass.Unconfirmed
    videoCodecs.all { it == TARGET_VIDEO_CODEC } -> VideoCodecClass.Avc
    videoCodecs.all { it == HEVC_VIDEO_CODEC } -> VideoCodecClass.Hevc
    else -> VideoCodecClass.Unconfirmed
}

/**
 * Only containers and codecs whose playback is confirmed become a video message immediately. AV1/VP9 are deliberately
 * excluded: sending them as a video would promise a compatibility that neither Telegram Android nor iOS confirms.
 */
internal fun videoCanBeSentAsVideoMessage(mimeType: String, videoCodecs: List<String>): Boolean =
    mimeType in VIDEO_MESSAGE_CONTAINERS && videoCodecClass(videoCodecs) != VideoCodecClass.Unconfirmed

/**
 * The composer may convert this MP4 to a video message by re-encoding it to H.264. It is the fallback for AV1/VP9:
 * after a successful transcode the attachment becomes a video; if the device cannot transcode, it stays a document
 * with the original bytes.
 */
internal fun videoCodecNeedsConversion(mimeType: String, videoCodecs: List<String>): Boolean =
    mimeType in VIDEO_MESSAGE_CONTAINERS && videoCodecClass(videoCodecs) == VideoCodecClass.Unconfirmed

/** H.264 sources are already the codec this pipeline targets, so re-encoding would only lose quality. */
internal fun videoIsAlreadyTargetCodec(videoCodecs: List<String>): Boolean =
    videoCodecClass(videoCodecs) == VideoCodecClass.Avc