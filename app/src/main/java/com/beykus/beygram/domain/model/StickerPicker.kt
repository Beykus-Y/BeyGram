package com.beykus.beygram.domain.model

/**
 * Stickers and GIFs shown by the attachment pickers. Only metadata is kept here: the bytes stay in TDLib's file
 * cache and are downloaded by the visible grid cells, so opening a large sticker set does not fetch every preview.
 */

enum class StickerFileFormat(val mimeType: String, val extension: String) {
    Webp("image/webp", "webp"),
    Tgs("application/x-tgsticker", "tgs"),
    Webm("video/webm", "webm")
}

data class StickerItem(
    val setId: Long,
    /** Identifier of the sticker inside its set; zero when TDLib has none. */
    val stickerId: Long,
    val fileId: Int,
    val thumbnailFileId: Int? = null,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
    /** True when the thumbnail can be rendered directly as an image; WebM/MPEG4 previews cannot. */
    val thumbnailIsStatic: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val emoji: String = "",
    val format: StickerFileFormat = StickerFileFormat.Webp,
    /** Premium sticker: only accounts with Telegram Premium may send it. */
    val premium: Boolean = false
) {
    /** Stable identity across search pages: a sticker id is unique inside its set and survives a refreshed file id. */
    val key: String get() = if (stickerId != 0L) "$setId:$stickerId" else "$setId:$fileId"

    fun toOutgoingMedia(): OutgoingMedia = OutgoingMedia(
        path = "",
        fileName = "sticker.${format.extension}",
        mimeType = format.mimeType,
        kind = MediaKind.Sticker,
        width = width,
        height = height,
        remoteFileId = fileId,
        // Telegram accepts WEBP/JPEG thumbnails for an input sticker and keeps the server-side one when omitted.
        thumbnailRemoteFileId = if (thumbnailIsStatic && thumbnailWidth > 0 && thumbnailHeight > 0) thumbnailFileId ?: 0 else 0,
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight,
        emoji = emoji
    )
}

data class StickerSetSummary(
    val id: Long,
    val title: String,
    val name: String = "",
    val size: Int = 0,
    val isInstalled: Boolean = false,
    val isOfficial: Boolean = false,
    /** First cover sticker, used as the compact pack preview. */
    val cover: StickerItem? = null
)

data class StickerSetContent(
    val id: Long,
    val title: String,
    val name: String = "",
    val isInstalled: Boolean = false,
    val stickers: List<StickerItem> = emptyList()
)

data class StickerSearchPage(
    val stickers: List<StickerItem> = emptyList(),
    /** Offset for the next page, or null when the query has no more results. */
    val nextOffset: Int? = null
)

data class AnimationItem(
    val fileId: Int,
    /** TDLib's cross-device identity, used to drop the same GIF sent in several messages. */
    val uniqueId: String = "",
    val thumbnailFileId: Int? = null,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationSeconds: Int = 0,
    val fileName: String = "",
    val mimeType: String = "video/mp4",
    val size: Long = 0,
    /** Tiny JPEG shown when no thumbnail is available; still much cheaper than downloading the GIF. */
    val minithumbnail: ByteArray? = null
) {
    val key: String get() = uniqueId.ifBlank { fileId.toString() }

    fun toOutgoingMedia(): OutgoingMedia = OutgoingMedia(
        path = "",
        fileName = fileName.ifBlank { "animation.gif" },
        mimeType = mimeType,
        kind = MediaKind.Animation,
        width = width,
        height = height,
        durationSeconds = durationSeconds,
        size = size,
        remoteFileId = fileId,
        thumbnailRemoteFileId = thumbnailFileId ?: 0,
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight
    )
}

data class AnimationPage(
    val animations: List<AnimationItem> = emptyList(),
    /** Cursor for the next page; empty when there are no more results. */
    val nextOffset: String = ""
)
