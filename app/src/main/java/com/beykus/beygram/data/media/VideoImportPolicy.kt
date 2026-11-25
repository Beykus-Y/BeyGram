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
 * The confirmed area is deliberately **H.264 only**: it is the codec TDLib documents for `inputVideo` and the one every
 * Telegram client decodes. A HEVC encoder inside one Android client and an iOS decoder do not prove that an arbitrary
 * MP4/HEVC profile plays on the recipient's client (profiles, levels, hardware decoders vary), so HEVC is *not* promised
 * as a video message: the file is imported as a document and the composer offers a transcode to H.264.
 *
 * - [Avc] — H.264: sent as a video message, never re-encoded for compatibility.
 * - [Hevc] — HEVC: needs a transcode before it becomes a video message.
 * - [Unconfirmed] — AV1, VP9, MPEG-4 Part 2 and anything unknown: same path as HEVC.
 */
internal enum class VideoCodecClass { Avc, Hevc, Unconfirmed }

internal fun videoCodecClass(videoCodecs: List<String>): VideoCodecClass = when {
    videoCodecs.isEmpty() -> VideoCodecClass.Unconfirmed
    videoCodecs.all { it == TARGET_VIDEO_CODEC } -> VideoCodecClass.Avc
    videoCodecs.all { it == HEVC_VIDEO_CODEC } -> VideoCodecClass.Hevc
    else -> VideoCodecClass.Unconfirmed
}

/**
 * Only H.264 in an MP4 becomes a video message immediately. Every other codec (HEVC included) would promise a
 * compatibility that is not proven for the recipient's client, so it stays a document with an explicit conversion path.
 */
internal fun videoCanBeSentAsVideoMessage(mimeType: String, videoCodecs: List<String>): Boolean =
    mimeType in VIDEO_MESSAGE_CONTAINERS && videoCodecClass(videoCodecs) == VideoCodecClass.Avc

/**
 * The composer may convert this MP4 to a video message by re-encoding it to H.264. It is the fallback for AV1/VP9:
 * after a successful transcode the attachment becomes a video; if the device cannot transcode, it stays a document
 * with the original bytes.
 */
internal fun videoCodecNeedsConversion(mimeType: String, videoCodecs: List<String>): Boolean =
    mimeType in VIDEO_MESSAGE_CONTAINERS && videoCodecClass(videoCodecs) != VideoCodecClass.Avc

/** H.264 sources are already the codec this pipeline targets, so re-encoding would only lose quality. */
internal fun videoIsAlreadyTargetCodec(videoCodecs: List<String>): Boolean =
    videoCodecClass(videoCodecs) == VideoCodecClass.Avc