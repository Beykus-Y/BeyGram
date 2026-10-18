package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.drinkless.tdlib.TdApi

internal fun TdApi.File.toMediaFileState() = MediaFileState(
    id = id,
    size = maxOf(size, expectedSize),
    localPath = local?.takeIf { it.isDownloadingCompleted }?.path?.takeIf { it.isNotBlank() },
    downloadedBytes = local?.downloadedSize ?: 0,
    isDownloading = local?.isDownloadingActive == true,
    uploadedBytes = remote?.uploadedSize ?: 0,
    isUploading = remote?.isUploadingActive == true
)

/** TDLib присылает волну 5-битными значениями (0…31), записанными подряд, младшие биты первыми. */
internal fun decodeVoiceWaveform(bytes: ByteArray): List<Int> = List(bytes.size * 8 / 5) { index ->
    val bit = index * 5
    val low = bytes[bit / 8].toInt() and 0xFF
    val high = bytes.getOrElse(bit / 8 + 1) { 0.toByte() }.toInt() and 0xFF
    ((low or (high shl 8)) shr (bit % 8)) and 0x1F
}

/** MIME type of a sticker's file: TGS is gzipped Lottie JSON, WebM is video, and anything else is WebP. */
internal fun stickerMimeType(format: TdApi.StickerFormat): String = when (format) {
    is TdApi.StickerFormatWebm -> "video/webm"
    is TdApi.StickerFormatTgs -> "application/x-tgsticker"
    else -> "image/webp"
}

internal class TdMediaMapper(private val files: MediaFileStore) {
    private fun register(file: TdApi.File): Int {
        files.register(file.toMediaFileState())
        return file.id
    }
    private fun thumbnail(thumb: TdApi.Thumbnail?): Int? = thumb?.file?.let(::register)

    fun media(content: TdApi.MessageContent): MessageMedia? = when (content) {
        is TdApi.MessagePhoto -> {
            val sizes = content.photo.sizes.orEmpty().filter { it.photo != null }
            val full = sizes.maxByOrNull { it.width.toLong() * it.height }
            val preview = sizes.filter { maxOf(it.width, it.height) >= 240 }.minByOrNull { it.width.toLong() * it.height }
                ?: sizes.minByOrNull { it.width.toLong() * it.height }
            full?.let { MessageMedia(MediaKind.Photo, register(it.photo), preview?.photo?.let(::register), "", "image/jpeg", it.width, it.height, hasSpoiler = content.hasSpoiler, isSecret = content.isSecret, minithumbnail = content.photo.minithumbnail?.data) }
        }
        is TdApi.MessageVideo -> content.video.let {
            MessageMedia(MediaKind.Video, register(it.video), thumbnail(it.thumbnail), it.fileName.orEmpty(), it.mimeType.orEmpty().ifBlank { "video/mp4" }, it.width, it.height, it.duration, content.hasSpoiler, content.isSecret, minithumbnail = it.minithumbnail?.data)
        }
        is TdApi.MessageAnimation -> content.animation.let {
            MessageMedia(MediaKind.Animation, register(it.animation), thumbnail(it.thumbnail), it.fileName.orEmpty(), it.mimeType.orEmpty().ifBlank { "video/mp4" }, it.width, it.height, it.duration, content.hasSpoiler, minithumbnail = it.minithumbnail?.data)
        }
        is TdApi.MessageDocument -> content.document.let {
            MessageMedia(MediaKind.Document, register(it.document), thumbnail(it.thumbnail), it.fileName.orEmpty(), it.mimeType.orEmpty().ifBlank { "application/octet-stream" })
        }
        is TdApi.MessageAudio -> content.audio.let {
            MessageMedia(MediaKind.Audio, register(it.audio), thumbnail(it.albumCoverThumbnail), it.fileName.orEmpty().ifBlank { it.title.orEmpty() }, it.mimeType.orEmpty().ifBlank { "audio/mpeg" }, durationSeconds = it.duration)
        }
        is TdApi.MessageVoiceNote -> content.voiceNote.let {
            MessageMedia(MediaKind.Voice, register(it.voice), fileName = "", mimeType = it.mimeType.orEmpty().ifBlank { "audio/ogg" }, durationSeconds = it.duration, waveform = decodeVoiceWaveform(it.waveform ?: ByteArray(0)))
        }
        is TdApi.MessageVideoNote -> content.videoNote.let {
            MessageMedia(MediaKind.VideoNote, register(it.video), thumbnail(it.thumbnail), "", "video/mp4", it.length, it.length, it.duration, isSecret = content.isSecret, isViewed = content.isViewed, minithumbnail = it.minithumbnail?.data)
        }
        is TdApi.MessageSticker -> content.sticker.let {
            MessageMedia(MediaKind.Sticker, register(it.sticker), thumbnail(it.thumbnail), "", stickerMimeType(it.format), it.width, it.height)
        }
        else -> null
    }

    fun caption(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessagePhoto -> content.caption?.text.orEmpty()
        is TdApi.MessageVideo -> content.caption?.text.orEmpty()
        is TdApi.MessageAnimation -> content.caption?.text.orEmpty()
        is TdApi.MessageDocument -> content.caption?.text.orEmpty()
        is TdApi.MessageAudio -> content.caption?.text.orEmpty()
        is TdApi.MessageVoiceNote -> content.caption?.text.orEmpty()
        else -> ""
    }
}
