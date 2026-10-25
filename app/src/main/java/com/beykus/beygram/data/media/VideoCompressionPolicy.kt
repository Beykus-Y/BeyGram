package com.beykus.beygram.data.media

/** Composer video compression preset. It only affects newly imported videos, never an already staged file. */
internal enum class VideoQualityOption { Compact, Balanced, Original }

internal data class VideoSourceInfo(
    val width: Int,
    val height: Int,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val videoCodecH264: Boolean,
    val hasAudio: Boolean
) {
    val shortSide: Int get() = minOf(width, height).coerceAtLeast(0)

    /** Source bitrate in bits per second; 0 when metadata is missing. */
    val sourceBitrate: Int
        get() = if (durationSeconds > 0) ((sizeBytes * 8L) / durationSeconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
}

internal enum class VideoLimitIssue { TooLong, TooLarge }

internal fun videoLimitIssue(info: VideoSourceInfo): VideoLimitIssue? = when {
    info.durationSeconds > MediaLimits.MAX_VIDEO_DURATION_SECONDS -> VideoLimitIssue.TooLong
    info.sizeBytes > MediaLimits.MAX_VIDEO_BYTES -> VideoLimitIssue.TooLarge
    else -> null
}

internal enum class VideoPlanReason {
    OriginalRequested,
    UnknownMetadata,
    AlreadyEfficient,
    NotWorthIt,
    Transcode
}

internal data class VideoCompressionPlan(
    val transcode: Boolean,
    val targetHeight: Int,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val expectedBytes: Long,
    val reason: VideoPlanReason
)

internal fun videoQualityShortSide(option: VideoQualityOption): Int = when (option) {
    VideoQualityOption.Compact -> 480
    VideoQualityOption.Balanced -> 720
    VideoQualityOption.Original -> 0
}

internal fun videoQualityVideoBitrate(option: VideoQualityOption): Int = when (option) {
    VideoQualityOption.Compact -> 1_200_000
    VideoQualityOption.Balanced -> 2_500_000
    VideoQualityOption.Original -> 0
}

internal fun videoQualityAudioBitrate(option: VideoQualityOption): Int = when (option) {
    VideoQualityOption.Compact -> 96_000
    VideoQualityOption.Balanced -> 128_000
    VideoQualityOption.Original -> 0
}

/**
 * Height passed to Media3's Presentation effect. `targetShortSide` is the short edge (what users
 * call 480p/720p); portrait videos need the proportional long edge as the output height.
 */
internal fun videoTargetOutputHeight(info: VideoSourceInfo, option: VideoQualityOption): Int {
    val targetShort = videoQualityShortSide(option)
    if (targetShort <= 0 || info.width <= 0 || info.height <= 0) return 0
    val actualShort = info.shortSide
    if (actualShort <= targetShort) return info.height
    val height = (info.height.toLong() * targetShort / actualShort).toInt().coerceAtLeast(2)
    return height - (height % 2)
}

internal fun estimatedVideoBytes(durationSeconds: Int, videoBitrate: Int, audioBitrate: Int): Long {
    if (durationSeconds <= 0) return 0L
    val bitsPerSecond = videoBitrate.toLong() + audioBitrate.toLong()
    return bitsPerSecond * durationSeconds / 8L
}

/**
 * Re-encoding is skipped when the file is already small, already H.264/MP4 at or below the chosen
 * short edge and bitrate, or when the estimated result would not be meaningfully smaller. This is
 * what prevents a second generation of quality loss on every send.
 */
internal fun videoCompressionPlan(info: VideoSourceInfo, option: VideoQualityOption): VideoCompressionPlan {
    val videoBitrate = videoQualityVideoBitrate(option)
    val audioBitrate = videoQualityAudioBitrate(option)
    val targetHeight = videoTargetOutputHeight(info, option)
    val expected = estimatedVideoBytes(info.durationSeconds, videoBitrate, audioBitrate)
    fun skip(reason: VideoPlanReason) = VideoCompressionPlan(false, targetHeight, videoBitrate, audioBitrate, expected, reason)

    if (option == VideoQualityOption.Original) return skip(VideoPlanReason.OriginalRequested)
    if (info.width <= 0 || info.height <= 0 || info.durationSeconds <= 0) return skip(VideoPlanReason.UnknownMetadata)
    // Tiny clips are not worth a transcode.
    if (info.sizeBytes <= TINY_VIDEO_BYTES) return skip(VideoPlanReason.AlreadyEfficient)
    val efficient = info.videoCodecH264 &&
        info.mimeType == "video/mp4" &&
        info.shortSide <= videoQualityShortSide(option) &&
        targetHeight == info.height &&
        info.sourceBitrate <= (videoBitrate * EFFICIENT_BITRATE_FACTOR).toLong()
    if (efficient) return skip(VideoPlanReason.AlreadyEfficient)
    if (expected <= 0L || expected >= (info.sizeBytes * NOT_WORTH_IT_FACTOR).toLong()) return skip(VideoPlanReason.NotWorthIt)
    return VideoCompressionPlan(true, targetHeight, videoBitrate, audioBitrate, expected, VideoPlanReason.Transcode)
}

private const val TINY_VIDEO_BYTES = 1_500_000L
private const val EFFICIENT_BITRATE_FACTOR = 1.15f
private const val NOT_WORTH_IT_FACTOR = 0.95f
