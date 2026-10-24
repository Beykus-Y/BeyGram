package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.OutgoingMedia
import org.drinkless.tdlib.TdApi

internal fun OutgoingMedia.toInputContent(caption: String, entities: List<MessageTextEntity> = emptyList()): TdApi.InputMessageContent {
    // A server-side file is sent by id: TDLib neither downloads it nor uploads it again.
    val local = if (remoteFileId > 0) TdApi.InputFileId(remoteFileId) else TdApi.InputFileLocal(path)
    val formattedCaption = formattedText(caption, entities)
    val thumb = when {
        thumbnailRemoteFileId > 0 -> TdApi.InputThumbnail(TdApi.InputFileId(thumbnailRemoteFileId), thumbnailWidth, thumbnailHeight)
        thumbnailPath != null -> TdApi.InputThumbnail(TdApi.InputFileLocal(thumbnailPath), thumbnailWidth, thumbnailHeight)
        else -> null
    }
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
                // The original container may have its index at the end of the file.
                supportsStreaming = false
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
        // Stickers and GIFs must keep their native bubble type instead of falling back to a document.
        MediaKind.Sticker -> TdApi.InputMessageSticker().apply {
            sticker = local
            thumbnail = thumb
            width = this@toInputContent.width
            height = this@toInputContent.height
            emoji = this@toInputContent.emoji
        }
        MediaKind.Animation -> TdApi.InputMessageAnimation().apply {
            animation = TdApi.InputAnimation(
                local,
                thumb,
                intArrayOf(),
                durationSeconds,
                this@toInputContent.width,
                this@toInputContent.height
            )
            this.caption = formattedCaption
            showCaptionAboveMedia = false
            hasSpoiler = false
        }
        else -> TdApi.InputMessageDocument().apply {
            document = TdApi.InputDocument(local, thumb, true)
            this.caption = formattedCaption
        }
    }
}
