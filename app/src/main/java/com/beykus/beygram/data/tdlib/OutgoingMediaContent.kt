package com.beykus.beygram.data.tdlib

import com.beykus.beygram.data.media.Mp4StreamingProbe
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.OutgoingMedia
import org.drinkless.tdlib.TdApi
import java.io.File

internal fun OutgoingMedia.toInputContent(caption: String, entities: List<MessageTextEntity> = emptyList()): TdApi.InputMessageContent {
    val local = TdApi.InputFileLocal(path)
    val formattedCaption = formattedText(caption, entities)
    val thumb = thumbnailPath?.let { TdApi.InputThumbnail(TdApi.InputFileLocal(it), thumbnailWidth, thumbnailHeight) }
    return when (kind) {
        MediaKind.Photo -> TdApi.InputMessagePhoto().apply {
            photo = TdApi.InputPhoto().apply {
                photo = local
                thumbnail = thumb
                width = this@toInputContent.width
                height = this@toInputContent.height
                addedStickerFileIds = intArrayOf()
            }
            this.caption = formattedCaption
        }
        MediaKind.Video -> TdApi.InputMessageVideo().apply {
            video = TdApi.InputVideo().apply {
                video = local
                thumbnail = thumb
                width = this@toInputContent.width
                height = this@toInputContent.height
                duration = durationSeconds
                // Only a fast-start MP4 can be played before the recipient downloads the whole file.
                // A container with moov at the end keeps the flag false instead of promising streaming.
                supportsStreaming = runCatching { Mp4StreamingProbe.supportsStreaming(File(path)) }.getOrDefault(false)
                addedStickerFileIds = intArrayOf()
            }
            this.caption = formattedCaption
        }
        MediaKind.VideoNote -> TdApi.InputMessageVideoNote().apply {
            videoNote = local
            thumbnail = thumb
            duration = durationSeconds
            length = this@toInputContent.width
        }
        MediaKind.Voice -> TdApi.InputMessageVoiceNote().apply {
            voiceNote = local
            duration = durationSeconds
            waveform = this@toInputContent.waveform
            this.caption = formattedCaption
        }
        else -> TdApi.InputMessageDocument().apply {
            document = TdApi.InputDocument(local, thumb, true)
            this.caption = formattedCaption
        }
    }
}
