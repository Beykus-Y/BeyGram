package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CustomEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * Custom emoji stickers used as reactions and dock items, by their id. Concurrent requests within a short window are
 * coalesced into bounded TDLib `GetCustomEmojiStickers(ids)` batches instead of firing one call per cell.
 */
internal class TdCustomEmojis(
    client: () -> Client,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val lookup: (suspend (Long) -> CustomEmojiAnswer)? = null,
    private val batchLookup: suspend (LongArray) -> Map<Long, CustomEmojiAnswer> = { ids ->
        if (lookup != null) {
            ids.associateWith { id -> lookup.invoke(id) }
        } else {
            lookupCustomEmojisBatch(TdRequests(client), ids)
        }
    }
) {
    private val flows = ConcurrentHashMap<Long, MutableStateFlow<CustomEmoji?>>()
    private val attempts = ConcurrentHashMap<Long, Int>()
    private val batchLock = Any()
    private val pendingBatch = LinkedHashSet<Long>()
    private val inFlightIds = HashSet<Long>()
    private var flushJob: Job? = null

    /** The sticker of [customEmojiId]; null until TDLib has answered. */
    fun observe(customEmojiId: Long): StateFlow<CustomEmoji?> {
        flows[customEmojiId]?.let { return it }
        val flow = MutableStateFlow<CustomEmoji?>(null)
        flows.putIfAbsent(customEmojiId, flow)?.let { return it }
        enqueue(customEmojiId)
        return flow
    }

    private fun enqueue(id: Long) {
        if (flows[id]?.value != null) return
        synchronized(batchLock) {
            if (flows[id]?.value != null || id in inFlightIds) return
            pendingBatch.add(id)
            if (flushJob == null) {
                flushJob = scope.launch { flushPending() }
            }
        }
    }

    private suspend fun flushPending() {
        delay(BATCH_WINDOW_MS)
        while (true) {
            val chunk: LongArray = synchronized(batchLock) {
                if (pendingBatch.isEmpty()) {
                    flushJob = null
                    return
                }
                val taken = pendingBatch.take(MAX_BATCH_SIZE)
                taken.forEach { id ->
                    pendingBatch.remove(id)
                    inFlightIds.add(id)
                }
                taken.toLongArray()
            }
            processBatch(chunk)
        }
    }

    private suspend fun processBatch(chunk: LongArray) {
        val answers = try {
            batchLookup(chunk)
        } finally {
            synchronized(batchLock) {
                chunk.forEach { inFlightIds.remove(it) }
            }
        }
        for (id in chunk) {
            when (val answer = answers[id] ?: CustomEmojiAnswer.Unanswered) {
                is CustomEmojiAnswer.Found -> {
                    flows[id]?.value = answer.emoji
                    attempts.remove(id)
                }
                CustomEmojiAnswer.Missing -> {
                    val attempt = (attempts[id] ?: 0) + 1
                    attempts[id] = attempt
                    if (attempt <= RETRY_PAUSES_MILLIS.size) {
                        val pause = RETRY_PAUSES_MILLIS[attempt - 1]
                        scope.launch {
                            delay(pause)
                            enqueue(id)
                        }
                    }
                }
                CustomEmojiAnswer.Unanswered -> {
                    val attempt = (attempts[id] ?: 0) + 1
                    attempts[id] = attempt
                    val pause = RETRY_PAUSES_MILLIS[minOf(attempt - 1, RETRY_PAUSES_MILLIS.lastIndex)]
                    scope.launch {
                        delay(pause)
                        enqueue(id)
                    }
                }
            }
        }
    }

    private companion object {
        const val BATCH_WINDOW_MS = 24L
        const val MAX_BATCH_SIZE = 100
        val RETRY_PAUSES_MILLIS = longArrayOf(2_000L, 5_000L, 15_000L, 30_000L)
    }
}

private suspend fun lookupCustomEmojisBatch(requests: TdRequests, customEmojiIds: LongArray): Map<Long, CustomEmojiAnswer> {
    if (customEmojiIds.isEmpty()) return emptyMap()
    val stickers = runCatching { requests(TdApi.GetCustomEmojiStickers(customEmojiIds)) }.getOrNull()?.stickers
        ?: return customEmojiIds.associateWith { CustomEmojiAnswer.Unanswered }
    val byId = HashMap<Long, TdApi.Sticker>(stickers.size)
    stickers.filterNotNull().forEach { sticker ->
        val customId = (sticker.fullType as? TdApi.StickerFullTypeCustomEmoji)?.customEmojiId
        if (customId != null && customId != 0L) {
            byId[customId] = sticker
        }
    }
    if (byId.isEmpty() && customEmojiIds.size == 1 && stickers.size == 1) {
        val single = stickers.firstOrNull()
        if (single != null) byId[customEmojiIds[0]] = single
    }
    val result = HashMap<Long, CustomEmojiAnswer>(customEmojiIds.size)
    for (id in customEmojiIds) {
        val sticker = byId[id]
        result[id] = if (sticker != null) {
            CustomEmojiAnswer.Found(CustomEmoji(id, sticker.sticker.id, sticker.emoji, stickerMimeType(sticker.format)))
        } else {
            CustomEmojiAnswer.Missing
        }
    }
    return result
}
