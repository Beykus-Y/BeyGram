package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
    @Volatile private var cachedAnimationBot: AnimationSearchBot? = null

    private fun register(file: TdApi.File): Int {
        files.register(file.toMediaFileState())
        return file.id
    }

    suspend fun recentStickers(): Result<List<StickerItem>> = requestResult {
        request(TdApi.GetRecentStickers(false)).stickers.orEmpty().mapNotNull { it?.toStickerItem(::register) }
    }

    suspend fun favoriteStickers(): Result<List<StickerItem>> = requestResult {
        request(TdApi.GetFavoriteStickers()).stickers.orEmpty().mapNotNull { it?.toStickerItem(::register) }
    }

    suspend fun setStickerFavorite(sticker: StickerItem, favorite: Boolean): Result<Unit> = requestResult {
        val input = TdApi.InputFileId(sticker.fileId)
        if (favorite) request(TdApi.AddFavoriteSticker(input)) else request(TdApi.RemoveFavoriteSticker(input))
        Unit
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

    /**
     * Adds or removes a set. The cached copy is dropped before the request: the caller may be cancelled while TDLib
     * still commits the change, so keeping the old installed flag would make the next opening show a stale button.
     */
    suspend fun changeStickerSet(setId: Long, installed: Boolean): Result<Unit> = requestResult {
        synchronized(cacheLock) { setCache.remove(setId) }
        request(TdApi.ChangeStickerSet(setId, installed, false))
        // A concurrent GetStickerSet may have populated the cache while the mutation was in flight.
        synchronized(cacheLock) {
            setCache[setId]?.let { setCache[setId] = it.copy(isInstalled = installed) }
        }
        Unit
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
    suspend fun recentAnimations(offset: String): Result<AnimationPage> = requestResult { historyAnimations(query = "", offset = offset) }

    /**
     * Global GIF search through the animation search bot, as the Telegram client does. The first page falls back to the
     * account's message history when the bot cannot be used, so search still works offline. Later pages follow the
     * path that produced their cursor: a bot cursor is passed to the bot, and a history cursor carries
     * [HISTORY_CURSOR_PREFIX] so it is never sent to the bot. A failing later page is reported, not replaced.
     */
    suspend fun searchAnimations(query: String, offset: String): Result<AnimationPage> = requestResult {
        when {
            offset.startsWith(HISTORY_CURSOR_PREFIX) ->
                historyAnimationPage(query, offset.removePrefix(HISTORY_CURSOR_PREFIX))
            offset.isNotEmpty() -> botAnimations(query, offset)
            else -> orNullOnFailure { botAnimations(query, offset) } ?: historyAnimationPage(query, offset)
        }
    }

    /** One page of the inline bot's animation results. Non-animation results are skipped; an empty offset means no more pages. */
    private suspend fun botAnimations(query: String, offset: String): AnimationPage {
        val bot = animationSearchBot()
        val answer = request(TdApi.GetInlineQueryResults(bot.userId, bot.chatId, null, query, offset))
        val merged = LinkedHashMap<String, AnimationItem>()
        answer.results.orEmpty().forEach { result ->
            val animation = (result as? TdApi.InlineQueryResultAnimation)?.animation?.toAnimationItem(::register) ?: return@forEach
            merged.putIfAbsent(animation.key, animation)
        }
        return AnimationPage(merged.values.toList(), answer.nextOffset.orEmpty())
    }

    /**
     * The bot's user id and private chat id, resolved once and kept. The username comes from TDLib's option; when the
     * option is missing or unreadable, the default public animation bot is used. Any failure throws and is not cached.
     */
    private suspend fun animationSearchBot(): AnimationSearchBot {
        cachedAnimationBot?.let { return it }
        val configured = orNullOnFailure { (request(TdApi.GetOption(ANIMATION_BOT_OPTION)) as? TdApi.OptionValueString)?.value }
        val username = configured.orEmpty().trim().removePrefix("@").ifBlank { DEFAULT_ANIMATION_BOT }
        val chat = request(TdApi.SearchPublicChat(username))
        val userId = (chat.type as? TdApi.ChatTypePrivate)?.userId
            ?: throw IllegalStateException("Animation search bot is not a user: $username")
        return AnimationSearchBot(userId, chat.id).also { cachedAnimationBot = it }
    }

    /** A page from the message history. Its cursor is tagged so the next call knows to keep using this path. */
    private suspend fun historyAnimationPage(query: String, offset: String): AnimationPage {
        val page = historyAnimations(query, offset)
        return page.copy(nextOffset = if (page.nextOffset.isEmpty()) "" else HISTORY_CURSOR_PREFIX + page.nextOffset)
    }

    /** Null when [block] fails for a reason other than cancellation; cancellation always propagates. */
    private suspend fun <T> orNullOnFailure(block: suspend () -> T): T? = try {
        block()
    } catch (timeout: TimeoutCancellationException) {
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null
    }

    /** Message-history search: walks a few pages of animation messages until something new appears. */
    private suspend fun historyAnimations(query: String, offset: String): AnimationPage {
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
        return AnimationPage(merged.values.toList(), if (hasMore) cursor else "")
    }

    suspend fun isPremiumAccount(): Result<Boolean> = requestResult { request(TdApi.GetMe()).isPremium }

    /** Resolved once per catalog; both fields are needed for GetInlineQueryResults. */
    private class AnimationSearchBot(val userId: Long, val chatId: Long)

    private companion object {
        const val SET_CACHE_SIZE = 12
        const val STICKER_PAGE_SIZE = 60
        const val ANIMATION_PAGE_SIZE = 40
        const val MAX_ANIMATION_PAGES_PER_LOAD = 3
        const val ANIMATION_BOT_OPTION = "animation_search_bot_username"
        const val DEFAULT_ANIMATION_BOT = "gif"
        const val HISTORY_CURSOR_PREFIX = "history:"
    }
}

/** TDLib sticker metadata, including the fields required by InputMessageSticker. */
internal fun TdApi.Sticker.toStickerItem(register: (TdApi.File) -> Int): StickerItem? {
    val fullType = fullType
    if (fullType is TdApi.StickerFullTypeCustomEmoji || fullType is TdApi.StickerFullTypeMask) return null
    val file = sticker ?: return null
    if (file.id <= 0) return null
    val fileId = register(file)
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
        fileId = fileId,
        thumbnailFileId = thumbnailFile?.let(register),
        thumbnailWidth = thumbnail?.width ?: 0,
        thumbnailHeight = thumbnail?.height ?: 0,
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
    val fileId = register(file)
    val thumb = thumbnail
    val thumbFile = thumb?.file
    return AnimationItem(
        fileId = fileId,
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
