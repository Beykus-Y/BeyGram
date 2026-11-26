package com.beykus.beygram.playback

import com.beykus.beygram.data.media.Mp4StreamingInfo
import com.beykus.beygram.data.media.Mp4StreamingReason

/**
 * Decides whether a partially downloaded MP4 can be opened progressively.
 *
 * Two different situations are covered:
 * - the index (`moov`) is in front of the payload: playback can start from the prefix that TDLib already wrote;
 * - the index sits at the end: Media3's own extractor reads it by seeking to the end of the file. That only works when
 *   the data source can fetch an arbitrary range, which is what [PlaybackRanges] provides. No second MP4 parser is
 *   written here: the extractor performs the seek, the data source only supplies the bytes it asks for.
 */
internal fun canPlayProgressively(info: Mp4StreamingInfo?, rangesAvailable: Boolean): Boolean {
    if (info == null) return false
    if (info.streamable) return true
    // The index is behind the payload: the extractor can reach it with a range request.
    return rangesAvailable && info.reason == Mp4StreamingReason.MoovAfterMediaData
}

/**
 * True when the file will never become playable and the page should stop probing. With a range-capable source an index
 * at the end is no longer a rejection; everything else stays final.
 */
internal fun isPermanentProgressiveRejection(info: Mp4StreamingInfo?, rangesAvailable: Boolean): Boolean {
    val reason = info?.reason ?: return false
    return when (reason) {
        Mp4StreamingReason.NotMp4,
        Mp4StreamingReason.MoovTooLarge,
        Mp4StreamingReason.NoVideoTrack,
        Mp4StreamingReason.UnsupportedVideoCodec -> true
        Mp4StreamingReason.MoovAfterMediaData -> !rangesAvailable
        // MissingMoov/TruncatedMoov mean "not yet": more of the file may still arrive, so the probe is retried.
        Mp4StreamingReason.MissingMoov,
        Mp4StreamingReason.TruncatedMoov,
        Mp4StreamingReason.Streamable,
        Mp4StreamingReason.NoAudioTrack,
        Mp4StreamingReason.UnsupportedAudioCodec,
        Mp4StreamingReason.ReadFailed -> false
    }
}