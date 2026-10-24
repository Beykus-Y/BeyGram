package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import org.drinkless.tdlib.TdApi

/**
 * Stickers and animations for the attachment pickers. TDLib owns the bytes; this catalog only maps metadata and
 * registers the files so the visible grid cells can download their own preview.
 */
internal class TdStickerCatalog(
    private val request: TdRequests,
    private val files: MediaFileStore
) {
    private val setCache = object : LinkedHashMap<Long, StickerSetContent>(SET_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, StickerSetContent>?): Boolean = size > SET_CACHE_SIZE
    }
    private val cacheLock = Any()

    private fun register(file: TdApi.File): Int {
        files.register(file.toMediaFileState())
        return file.id
    }

    suspend fun recentStickers(): Result<List<StickerItem>> = requestResult {
        request(TdApi.GetRecentStickers(false)).stickers.orEmpty().mapNotNull { it?.toStickerItem(::register) }
    }

    suspend fun installedStickerSets(): Result<List<StickerSetSummary>> = requestResult {
        request(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())).sets.orEmpty()
            .mapNotNull { it?.toStickerSetSummary(::register) }
    }

    /** Full sticker metadata comes in one TDLib response; the preview bytes are still downloaded per visible cell. */
    suspend fun stickerSet(setId: Long): Result<StickerSetContent> = requestResult {
        synchronized(cacheLock) { setCache[setId] }?.let { return@requestResult it }
        val content = request(TdApi.GetStickerSet(setId)).toStickerSetContent(::register)
        synchronized(cacheLock) { setCache[setId] = content }
        content
    }

    suspend fun searchStickers(query: String, offset: Int): Result<StickerSearchPage> = requestResult {
        val page = request(TdApi.SearchStickers(TdApi.StickerTypeRegular(), "", query, emptyArray<String>(), offset, STICKER_PAGE_SIZE))
        val found = page.stickers.orEmpty()
        StickerSearchPage(
            stickers = found.mapNotNull { it?.toStickerItem(::register) },
            nextOffset = if (found.size < STICKER_PAGE_SIZE) null else offset + found.size
        )
    }

    suspend fun savedAnimations(): Result<List<AnimationItem>> = requestResult {
        request(TdApi.GetSavedAnimations()).animations.orEmpty().mapNotNull { it?.toAnimationItem(::register) }
    }

    /** Recently used GIFs come from the account's message history; TDLib has no separate recent-animation list. */
    suspend fun recentAnimations(offset: String): Result<AnimationPage> = animations(query = "", offset = offset)

    suspend fun searchAnimations(query: String, offset: String): Result<AnimationPage> = animations(query = query, offset = offset)

    private suspend fun animations(query: String, offset: String): Result<AnimationPage> = requestResult {
        var cursor = offset
        val merged = LinkedHashMap<String, AnimationItem>()
        var hasMore = false
        var page = 0
        while (page < MAX_ANIMATION_PAGES_PER_LOAD) {
            val found = request(
                TdApi.SearchMessages(
                    null,
                    query,
                    cursor,
                    ANIMATION_PAGE_SIZE,
                    TdApi.SearchMessagesFilterAnimation(),
                    null,
                    0,
                    0
                )
            )
            val messages = found.messages.orEmpty().filterNotNull()
            messages.forEach { message ->
                val animation = (message.content as? TdApi.MessageAnimation)?.animation ?: return@forEach
                animation.toAnimationItem(::register)?.let { merged.putIfAbsent(it.key, it) }
            }
            cursor = found.nextOffset.orEmpty()
            hasMore = cursor.isNotBlank() && messages.isNotEmpty()
            page++
            // A page may contain only animations already shown; keep walking until there is something new or no more pages.
            if (merged.isNotEmpty() || !hasMore) break
        }
        AnimationPage(merged.values.toList(), if (hasMore) cursor else "")
    }

    suspend fun isPremiumAccount(): Result<Boolean> = requestResult { request(TdApi.GetMe()).isPremium }

    private companion object {
        const val SET_CACHE_SIZE = 12
        const val STICKER_PAGE_SIZE = 60
        const val ANIMATION_PAGE_SIZE = 40
        const val MAX_ANIMATION_PAGES_PER_LOAD = 3
    }
}

/** TDLib sticker metadata, including the fields required by InputMessageSticker. */
internal fun TdApi.Sticker.toStickerItem(register: (TdApi.File) -> Int): StickerItem? {
    val fullType = fullType
    if (fullType is TdApi.StickerFullTypeCustomEmoji || fullType is TdApi.StickerFullTypeMask) return null
    val file = sticker ?: return null
    if (file.id <= 0) return null
    val thumbnail = thumbnail
    val thumbnailFile = thumbnail?.file
    val format = when (format) {
        is TdApi.StickerFormatTgs -> StickerFileFormat.Tgs
        is TdApi.StickerFormatWebm -> StickerFileFormat.Webm
        else -> StickerFileFormat.Webp
    }
    return StickerItem(
        setId = setId,
        stickerId = id,
        fileId = file.id,
        thumbnailFileId = thumbnailFile?.let(register),
        thumbnailIsStatic = thumbnail?.format.isStaticImage(),
        width = width,
        height = height,
        emoji = emoji.orEmpty(),
        format = format,
        premium = (fullType as? TdApi.StickerFullTypeRegular)?.premiumAnimation != null
    )
}

internal fun TdApi.StickerSetInfo.toStickerSetSummary(register: (TdApi.File) -> Int): StickerSetSummary = StickerSetSummary(
    id = id,
    title = title,
    name = name.orEmpty(),
    size = size,
    isInstalled = isInstalled,
    isOfficial = isOfficial,
    cover = covers.orEmpty().firstOrNull()?.toStickerItem(register)
)

internal fun TdApi.StickerSet.toStickerSetContent(register: (TdApi.File) -> Int): StickerSetContent = StickerSetContent(
    id = id,
    title = title,
    name = name.orEmpty(),
    isInstalled = isInstalled,
    stickers = stickers.orEmpty().mapNotNull { it?.toStickerItem(register) }
)

internal fun TdApi.Animation.toAnimationItem(register: (TdApi.File) -> Int): AnimationItem? {
    val file = animation ?: return null
    if (file.id <= 0) return null
    val thumb = thumbnail
    val thumbFile = thumb?.file
    return AnimationItem(
        fileId = file.id,
        uniqueId = file.remote?.uniqueId.orEmpty(),
        thumbnailFileId = thumbFile?.let(register),
        thumbnailWidth = thumb?.width ?: 0,
        thumbnailHeight = thumb?.height ?: 0,
        width = width,
        height = height,
        durationSeconds = duration,
        fileName = fileName.orEmpty(),
        mimeType = mimeType.orEmpty().ifBlank { "video/mp4" },
        size = maxOf(file.size, file.expectedSize),
        minithumbnail = minithumbnail?.data
    )
}

/** A thumbnail that an Android ImageDecoder can open; WebM/TGS previews must fall back to the emoji or a placeholder. */
private fun TdApi.ThumbnailFormat?.isStaticImage(): Boolean =
    this is TdApi.ThumbnailFormatWebp || this is TdApi.ThumbnailFormatJpeg
