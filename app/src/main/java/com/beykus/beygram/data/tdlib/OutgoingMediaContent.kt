package com.beykus.beygram.data.tdlib

import com.beykus.beygram.data.media.Mp4StreamingProbe
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.OutgoingMedia
import org.drinkless.tdlib.TdApi
import java.io.File

internal fun OutgoingMedia.toInputContent(
    caption: String,
    entities: List<MessageTextEntity> = emptyList(),
    selfDestructType: TdApi.MessageSelfDestructType? = null
): TdApi.InputMessageContent {
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
            this.selfDestructType = selfDestructType
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
