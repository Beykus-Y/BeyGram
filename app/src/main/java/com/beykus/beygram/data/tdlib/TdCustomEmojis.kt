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

/** Ответ TDLib на один custom emoji: найден, пустой ответ или запрос не прошёл. */
internal sealed interface CustomEmojiAnswer {
    class Found(val emoji: CustomEmoji) : CustomEmojiAnswer

    /** TDLib ответил без стикера: такого id нет или он удалён. Повторы ограничены бюджетом пауз. */
    object Missing : CustomEmojiAnswer

    /** TDLib не ответил, например нет сети. Повторы идут, пока ответа нет. */
    object Unanswered : CustomEmojiAnswer
}

/**
 * Custom emoji stickers used as reactions, by their id. Each id is asked from TDLib once. An id that TDLib cannot answer
 * for is asked again until it answers, and observers already waiting on it get the sticker as soon as it does. An id that
 * TDLib answers without a sticker is asked only within the retry budget.
 */
internal class TdCustomEmojis(
    client: () -> Client,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val lookup: suspend (Long) -> CustomEmojiAnswer = { customEmojiId -> lookupCustomEmoji(TdRequests(client), customEmojiId) },
) {
    private val flows = ConcurrentHashMap<Long, MutableStateFlow<CustomEmoji?>>()

    /** The sticker of [customEmojiId]; null until TDLib has answered. */
    fun observe(customEmojiId: Long): StateFlow<CustomEmoji?> {
        flows[customEmojiId]?.let { return it }
        val flow = MutableStateFlow<CustomEmoji?>(null)
        flows.putIfAbsent(customEmojiId, flow)?.let { return it }
        scope.launch {
            // Наблюдатели держат этот поток, поэтому он не должен остаться пустым: при сбое спрашиваем с последней паузой до ответа.
            var answer = lookup(customEmojiId)
            var attempt = 0
            while (answer !is CustomEmojiAnswer.Found) {
                if (answer == CustomEmojiAnswer.Missing && attempt >= RETRY_PAUSES_MILLIS.size) break
                delay(RETRY_PAUSES_MILLIS[minOf(attempt, RETRY_PAUSES_MILLIS.lastIndex)])
                attempt++
                answer = lookup(customEmojiId)
            }
            (answer as? CustomEmojiAnswer.Found)?.let { flow.value = it.emoji }
        }
        return flow
    }

    private companion object {
        val RETRY_PAUSES_MILLIS = longArrayOf(2_000L, 5_000L, 15_000L, 30_000L)
    }
}

private suspend fun lookupCustomEmoji(requests: TdRequests, customEmojiId: Long): CustomEmojiAnswer {
    val stickers = runCatching { requests(TdApi.GetCustomEmojiStickers(longArrayOf(customEmojiId))) }.getOrNull()?.stickers
        ?: return CustomEmojiAnswer.Unanswered
    // One id is asked for, so the sticker that comes back is its own, whatever id TDLib gives the sticker itself.
    val sticker = stickers.firstOrNull() ?: return CustomEmojiAnswer.Missing
    return CustomEmojiAnswer.Found(CustomEmoji(customEmojiId, sticker.sticker.id, sticker.emoji, stickerMimeType(sticker.format)))
}
