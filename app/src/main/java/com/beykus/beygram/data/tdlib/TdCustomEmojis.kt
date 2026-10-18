package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CustomEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom emoji stickers used as reactions, by their id. Each id is asked from TDLib once, and an id that TDLib cannot answer
 * for is asked again the next time it is observed.
 */
internal class TdCustomEmojis(client: () -> Client) {
    private val requests = TdRequests(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flows = ConcurrentHashMap<Long, MutableStateFlow<CustomEmoji?>>()

    /** The sticker of [customEmojiId]; null until TDLib has answered. */
    fun observe(customEmojiId: Long): StateFlow<CustomEmoji?> {
        flows[customEmojiId]?.let { return it }
        val flow = MutableStateFlow<CustomEmoji?>(null)
        flows.putIfAbsent(customEmojiId, flow)?.let { return it }
        scope.launch {
            val emoji = lookup(customEmojiId)
            if (emoji != null) {
                flow.value = emoji
            } else {
                flows.remove(customEmojiId, flow)
            }
        }
        return flow
    }

    private suspend fun lookup(customEmojiId: Long): CustomEmoji? {
        val stickers = runCatching { requests(TdApi.GetCustomEmojiStickers(longArrayOf(customEmojiId))) }.getOrNull()?.stickers.orEmpty()
        // One id is asked for, so the sticker that comes back is its own, whatever id TDLib gives the sticker itself.
        val sticker = stickers.firstOrNull() ?: return null
        return CustomEmoji(customEmojiId, sticker.sticker.id, sticker.emoji, stickerMimeType(sticker.format))
    }
}
