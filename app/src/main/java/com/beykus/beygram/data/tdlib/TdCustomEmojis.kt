package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CustomEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom emoji stickers used as reactions, by their id. Each id is asked from TDLib once. An id that TDLib cannot answer
 * for is asked again until it answers, and observers already waiting on it get the sticker as soon as it does.
 */
internal class TdCustomEmojis(
    client: () -> Client,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val lookup: suspend (Long) -> CustomEmoji? = { customEmojiId -> lookupCustomEmoji(TdRequests(client), customEmojiId) },
) {
    private val flows = ConcurrentHashMap<Long, MutableStateFlow<CustomEmoji?>>()

    /** The sticker of [customEmojiId]; null until TDLib has answered. */
    fun observe(customEmojiId: Long): StateFlow<CustomEmoji?> {
        flows[customEmojiId]?.let { return it }
        val flow = MutableStateFlow<CustomEmoji?>(null)
        flows.putIfAbsent(customEmojiId, flow)?.let { return it }
        scope.launch {
            // Наблюдатели держат этот поток, поэтому он не должен остаться пустым: после бюджета пауз спрашиваем с последней паузой.
            var emoji = lookup(customEmojiId)
            var attempt = 0
            while (emoji == null) {
                delay(RETRY_PAUSES_MILLIS[minOf(attempt, RETRY_PAUSES_MILLIS.lastIndex)])
                attempt++
                emoji = lookup(customEmojiId)
            }
            flow.value = emoji
        }
        return flow
    }

    private companion object {
        val RETRY_PAUSES_MILLIS = longArrayOf(2_000L, 5_000L, 15_000L, 30_000L)
    }
}

private suspend fun lookupCustomEmoji(requests: TdRequests, customEmojiId: Long): CustomEmoji? {
    val stickers = runCatching { requests(TdApi.GetCustomEmojiStickers(longArrayOf(customEmojiId))) }.getOrNull()?.stickers.orEmpty()
    // One id is asked for, so the sticker that comes back is its own, whatever id TDLib gives the sticker itself.
    val sticker = stickers.firstOrNull() ?: return null
    return CustomEmoji(customEmojiId, sticker.sticker.id, sticker.emoji, stickerMimeType(sticker.format))
}
