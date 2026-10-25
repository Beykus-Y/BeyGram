package com.beykus.beygram.data.media

/**
 * Compatibility gate for sending a video as a Telegram video message. TG expects an MP4
 * container with H.264 video; MKV, HEVC and other codecs stay documents instead of being
 * re-encoded speculatively. Media3 can demux some of them, but encoder support (especially HEVC)
 * varies by device, so the safe path is explicit and unchanged.
 */
internal fun videoCanBeSentAsVideoMessage(mimeType: String, videoCodecs: List<String>): Boolean =
    mimeType == "video/mp4" && videoCodecs.isNotEmpty() && videoCodecs.all { it == "video/avc" }
