package com.beykus.beygram.data.media

/** Composer audio compression preset. Applies only to newly imported audio files. */
internal enum class AudioQualityOption { Compact, Balanced, Original }

internal data class AudioSourceInfo(
    val durationSeconds: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val codecAac: Boolean,
    /** Title/artist/album or embedded artwork would be lost by a re-encode, so such files are kept. */
    val hasMetadata: Boolean
) {
    /** Source bitrate in bits per second; 0 when metadata is missing. */
    val sourceBitrate: Int
        get() = if (durationSeconds > 0) {
            ((sizeBytes * 8L) / durationSeconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else 0
}

internal enum class AudioPlanReason {
    OriginalRequested,
    UnknownMetadata,
    MetadataPresent,
    AlreadyEfficient,
    NotWorthIt,
    Transcode
}

internal data class AudioCompressionPlan(
    val transcode: Boolean,
    val bitrate: Int,
    val expectedBytes: Long,
    val reason: AudioPlanReason
)

internal fun audioQualityBitrate(option: AudioQualityOption): Int = when (option) {
    AudioQualityOption.Compact -> 48_000
    AudioQualityOption.Balanced -> 96_000
    AudioQualityOption.Original -> 0
}

internal fun estimatedAudioBytes(durationSeconds: Int, bitrate: Int): Long {
    if (durationSeconds <= 0 || bitrate <= 0) return 0L
    return bitrate.toLong() * durationSeconds / 8L
}

/**
 * Audio files with tags or cover art are never re-encoded: Media3 does not guarantee their
 * preservation, and silently dropping metadata would be worse than sending the original.
 */
internal fun audioCompressionPlan(info: AudioSourceInfo, option: AudioQualityOption): AudioCompressionPlan {
    val bitrate = audioQualityBitrate(option)
    val expected = estimatedAudioBytes(info.durationSeconds, bitrate)
    fun skip(reason: AudioPlanReason) = AudioCompressionPlan(false, bitrate, expected, reason)

    if (option == AudioQualityOption.Original) return skip(AudioPlanReason.OriginalRequested)
    if (info.durationSeconds <= 0 || info.sizeBytes <= 0L) return skip(AudioPlanReason.UnknownMetadata)
    if (info.hasMetadata) return skip(AudioPlanReason.MetadataPresent)
    if (info.sizeBytes <= TINY_AUDIO_BYTES) return skip(AudioPlanReason.AlreadyEfficient)
    val efficient = info.codecAac &&
        info.mimeType == "audio/mp4" &&
        info.sourceBitrate <= (bitrate * EFFICIENT_BITRATE_FACTOR).toLong()
    if (efficient) return skip(AudioPlanReason.AlreadyEfficient)
    if (expected <= 0L || expected >= (info.sizeBytes * NOT_WORTH_IT_FACTOR).toLong()) return skip(AudioPlanReason.NotWorthIt)
    return AudioCompressionPlan(true, bitrate, expected, AudioPlanReason.Transcode)
}

private const val TINY_AUDIO_BYTES = 256_000L
private const val EFFICIENT_BITRATE_FACTOR = 1.15f
/** Re-encode only when the expected file is at least 15% smaller. */
private const val NOT_WORTH_IT_FACTOR = 0.85f
