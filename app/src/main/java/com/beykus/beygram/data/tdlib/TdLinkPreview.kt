package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageLinkPreview
import org.drinkless.tdlib.TdApi

/** Small enough to download with a bubble, large enough not to look blurry in the card. */
private const val LINK_PREVIEW_MIN_EDGE = 240

/** A registered-in-waiting photo: the mapper turns [file] into a media-store id. */
internal data class LinkPreviewImageSource(val file: TdApi.File, val width: Int, val height: Int)

/**
 * Secret chats must never expose server-supplied link metadata, even if an update still carries a preview.
 * TDLib normally omits [TdApi.MessageText.linkPreview] there; this is the explicit, testable policy.
 */
internal fun visibleLinkPreview(content: TdApi.MessageContent, isSecretChat: Boolean): TdApi.LinkPreview? =
    if (isSecretChat) null else (content as? TdApi.MessageText)?.linkPreview

/** The smallest photo size at least [LINK_PREVIEW_MIN_EDGE] px wide; tiny-only photos fall back to the smallest. */
internal fun TdApi.Photo.linkPreviewImageSource(): LinkPreviewImageSource? {
    val sizes = sizes.orEmpty().filter { it.photo != null && it.width > 0 && it.height > 0 }
    val selected = sizes.filter { maxOf(it.width, it.height) >= LINK_PREVIEW_MIN_EDGE }
        .minByOrNull { it.width.toLong() * it.height }
        ?: sizes.minByOrNull { it.width.toLong() * it.height }
        ?: return null
    return LinkPreviewImageSource(selected.photo ?: return null, selected.width, selected.height)
}

internal fun TdApi.Thumbnail.linkPreviewImageSource(): LinkPreviewImageSource? =
    file?.let { LinkPreviewImageSource(it, width, height) }

/**
 * Known media-bearing preview types use their cover/thumbnail. Every other type, including future TDLib variants,
 * falls back to a text-only card instead of failing to map the message.
 */
internal fun TdApi.LinkPreview.linkPreviewImageSource(): LinkPreviewImageSource? = when (val kind = type) {
    is TdApi.LinkPreviewTypeArticle -> kind.photo?.linkPreviewImageSource()
    is TdApi.LinkPreviewTypePhoto -> kind.photo?.linkPreviewImageSource()
    is TdApi.LinkPreviewTypeVideo -> kind.cover?.linkPreviewImageSource()
    is TdApi.LinkPreviewTypeAnimation -> kind.animation?.thumbnail?.linkPreviewImageSource()
    is TdApi.LinkPreviewTypeAudio -> kind.audio?.albumCoverThumbnail?.linkPreviewImageSource()
    is TdApi.LinkPreviewTypeDocument -> kind.document?.thumbnail?.linkPreviewImageSource()
    is TdApi.LinkPreviewTypeEmbeddedVideoPlayer -> kind.thumbnail?.linkPreviewImageSource()
    else -> null
}

/** Pure field mapping; [registerPhoto] lets tests check the selected bytes without a MediaFileStore. */
internal fun TdApi.LinkPreview.toMessageLinkPreview(registerPhoto: (TdApi.File) -> Int = { it.id }): MessageLinkPreview {
    val image = linkPreviewImageSource()
    return MessageLinkPreview(
        displayUrl = displayUrl.orEmpty(),
        siteName = siteName.orEmpty(),
        title = title.orEmpty(),
        description = description?.text.orEmpty(),
        photoFileId = image?.let { registerPhoto(it.file) },
        photoWidth = image?.width ?: 0,
        photoHeight = image?.height ?: 0
    )
}
