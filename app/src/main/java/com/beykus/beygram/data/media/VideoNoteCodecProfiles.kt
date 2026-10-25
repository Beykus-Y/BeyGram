package com.beykus.beygram.data.media

import android.media.MediaCodecInfo

/**
 * Human-readable H.264 profile/level names for reports. Unknown or non-AVC codes stay numeric,
 * so a report never turns an unrecognised value into a confident claim (issue #234, §8).
 */
internal fun avcProfileLabel(profile: Int?): String? = when (profile) {
    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"
    MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"
    MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "Extended"
    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"
    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High 10"
    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
    MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline -> "Constrained Baseline"
    MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh -> "Constrained High"
    else -> null
}

internal fun avcLevelLabel(level: Int?): String? = when (level) {
    MediaCodecInfo.CodecProfileLevel.AVCLevel1 -> "1"
    MediaCodecInfo.CodecProfileLevel.AVCLevel1b -> "1b"
    MediaCodecInfo.CodecProfileLevel.AVCLevel11 -> "1.1"
    MediaCodecInfo.CodecProfileLevel.AVCLevel12 -> "1.2"
    MediaCodecInfo.CodecProfileLevel.AVCLevel13 -> "1.3"
    MediaCodecInfo.CodecProfileLevel.AVCLevel2 -> "2"
    MediaCodecInfo.CodecProfileLevel.AVCLevel21 -> "2.1"
    MediaCodecInfo.CodecProfileLevel.AVCLevel22 -> "2.2"
    MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> "3"
    MediaCodecInfo.CodecProfileLevel.AVCLevel31 -> "3.1"
    MediaCodecInfo.CodecProfileLevel.AVCLevel32 -> "3.2"
    MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> "4"
    MediaCodecInfo.CodecProfileLevel.AVCLevel41 -> "4.1"
    MediaCodecInfo.CodecProfileLevel.AVCLevel42 -> "4.2"
    MediaCodecInfo.CodecProfileLevel.AVCLevel5 -> "5"
    MediaCodecInfo.CodecProfileLevel.AVCLevel51 -> "5.1"
    MediaCodecInfo.CodecProfileLevel.AVCLevel52 -> "5.2"
    else -> null
}

/** Names an AVC code; other MIME types or unknown values remain raw numbers. */
internal fun codecProfileLabel(mime: String?, profile: Int?): String? =
    if (mime == null || mime.equals("video/avc", ignoreCase = true)) {
        avcProfileLabel(profile) ?: profile?.toString()
    } else profile?.toString()

internal fun codecLevelLabel(mime: String?, level: Int?): String? =
    if (mime == null || mime.equals("video/avc", ignoreCase = true)) {
        avcLevelLabel(level) ?: level?.toString()
    } else level?.toString()
