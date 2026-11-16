package com.beykus.beygram.media.bgbeat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import java.io.File

/**
 * The one place that decides whether a playback item can be analysed for music.
 * BG Beat and BG Motion must agree: a private voice note or a still-downloading
 * file must not be decoded into a shared analysis cache.
 */
object BgMediaEligibility {
    fun reasonFor(item: PlaybackItem): BgBeatIneligibleReason? {
        if (item.kind != MediaKind.Audio) return BgBeatIneligibleReason.NotMusic
        if (item.secret || item.systemPrivate) return BgBeatIneligibleReason.PrivateMedia
        if (item.growing != null) return BgBeatIneligibleReason.StillDownloading

        val file = File(item.path)
        if (!file.exists() || !file.isFile || file.length() <= 0L || file.name.endsWith(".temp")) {
            return BgBeatIneligibleReason.NotDownloaded
        }
        return null
    }
}
