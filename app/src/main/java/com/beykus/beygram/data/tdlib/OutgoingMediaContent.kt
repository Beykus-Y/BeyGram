package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.OutgoingMedia
import org.drinkless.tdlib.TdApi

internal fun OutgoingMedia.toInputContent(
    caption: String,
    entities: List<MessageTextEntity> = emptyList(),
    selfDestructType: TdApi.MessageSelfDestructType? = null
): TdApi.InputMessageContent {
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
            this.selfDestructType = selfDestructType
        }
        MediaKind.Video -> TdApi.InputMessageVideo().apply {
            video = TdApi.InputVideo().apply {
                video = local
                thumbnail = thumb
                width = this@toInputContent.width
                height = this@toInputContent.height
                duration = durationSeconds
                // The original container may have its index at the end of the file.
                supportsStreaming = false
                addedStickerFileIds = intArrayOf()
            }
            this.caption = formattedCaption
            this.selfDestructType = selfDestructType
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
