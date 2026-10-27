package com.beykus.beygram.playback

import androidx.compose.runtime.compositionLocalOf
import androidx.media3.common.Player
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import java.io.File

/** BeyGram's first, original account has no secondary-account id; it still gets its own identity namespace. */
const val LEGACY_PLAYBACK_ACCOUNT_ID = "legacy"

/** Account identity for media opened anywhere in the shared BeyGram UI. */
val LocalPlaybackAccountId = compositionLocalOf { LEGACY_PLAYBACK_ACCOUNT_ID }

/**
 * Telegram identity of a media item. A numeric TDLib id is only unique inside one account and one Telegram deal,
 * so every equality check must carry the account and the message that produced it.
 */
data class PlaybackOrigin(
    val chatId: Long,
    val messageId: Long,
    /** Source identity must accompany any bare Telegram numeric ID. */
    val accountId: String = LEGACY_PLAYBACK_ACCOUNT_ID
)

/** Viewer pages carry the same chat/message/account origin as the bubble that started playback. */
internal fun messagePlaybackOrigin(chatId: Long?, messageId: Long, accountId: String): PlaybackOrigin? =
    chatId?.let { PlaybackOrigin(it, messageId, accountId) }

/**
 * A file that TDLib is still downloading. Reads are bounded by [readablePrefix]; the player waits
 * for the next chunk (or fails after a timeout) instead of treating the current EOF as the end.
 * The lambdas read live state, so completion is observed without replacing the player.
 */
data class GrowingPlaybackSource(
    val totalSize: Long,
    val readablePrefix: () -> Long,
    val isComplete: () -> Boolean,
    /** Final local path once TDLib completes; may differ from the growing path. */
    val completedPath: () -> String?,
    val isDownloading: () -> Boolean,
    val onNeedMore: () -> Unit,
    /**
     * Ogg/Opus seekers read the last page when the length is known, which is unavailable before the
     * download completes. Such sources advertise an unknown length until then.
     */
    val advertiseLength: Boolean = true
)

enum class PlaybackRepeatMode {
    Off,
    One,
    All;

    /** Off -> All -> One -> Off, the order used by the player controls. */
    fun next(): PlaybackRepeatMode = when (this) {
        Off -> All
        All -> One
        One -> Off
    }
}

/** How End/Next/Previous behave. Kept separate from the UI so lockscreen and app controls share one policy. */
internal const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

/**
 * Файл для общего плеера. [key] отличает файлы друг от друга: повторный тап по тому же файлу в том же сообщении
 * ставит его на паузу, а тот же fileId из другого сообщения или аккаунта — новый контекст.
 *
 * [path] может быть пустым у трека, который ещё не скачан: [PlaybackQueueSource] разрешит путь лениво.
 * [accountId], [origin] и [fileId] вместе образуют стабильный [occurrenceId]; один TDLib fileId без аккаунта
 * не является глобальной идентичностью.
 */
data class PlaybackItem(
    val key: String,
    val kind: MediaKind,
    val path: String,
    val title: String,
    val durationSeconds: Int,
    val origin: PlaybackOrigin? = null,
    /** Local TDLib album cover path; no remote lookup or third-party service. */
    val artworkPath: String? = null,
    val performer: String = "",
    /** Секретное вложение: играет только пока открыт его чат и не повторяется после конца. */
    val secret: Boolean = false,
    /** Set only while the local TDLib file is still growing; null uses the completed file. */
    val growing: GrowingPlaybackSource? = null,
    val accountId: String = origin?.accountId?.takeIf { it.isNotBlank() } ?: LEGACY_PLAYBACK_ACCOUNT_ID,
    /** Original TDLib file id, kept for account-scoped lazy lookup. Null for a standalone local file. */
    val fileId: Int? = null,
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val isSecretChat: Boolean = false,
    val hasSpoiler: Boolean = false,
    /** View-once media must not enter queues/system metadata even outside a secret chat. */
    val viewOnce: Boolean = false,
    /** Real track title without the display-only "Artist — Title" join. */
    val trackTitle: String = title,
    val album: String = "",
    /** Stable identity of this queue occurrence. Equal to [key] unless a caller has a better occurrence id. */
    val occurrenceId: String = key
) {
    val isMusic: Boolean get() = kind == MediaKind.Audio
    val hasLocalSource: Boolean get() = path.isNotBlank() || growing != null

    /** Neither secret attachments, secret-chat media nor view-once content may reach queues or system metadata. */
    val systemPrivate: Boolean get() = secret || isSecretChat || viewOnce

    /** System-visible id for private content must not expose the Telegram file id or message origin. */
    val systemMediaId: String
        get() = if (systemPrivate) "beygram-secret-${Integer.toHexString(key.hashCode())}" else key
}

/** Separate metadata fields for Media3; secret content never reaches system metadata. */
data class PlaybackMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkPath: String? = null,
    val durationMs: Long = 0L
)

/** Состояние общего плеера для интерфейса. Без [item] плеер пуст, и мини-плеер не показывается. */
data class PlaybackState(
    val item: PlaybackItem? = null,
    /** Actually audible right now; buffering is false even while [intentPlaying] is true. */
    val playing: Boolean = false,
    /** User intent, independent of temporary audio-focus loss and buffering. */
    val intentPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val failed: Boolean = false,
    /** Плеер хотя бы раз сообщил, что файл реально играет. Отметка «прослушано» ставится по этому флагу, а не по нажатию. */
    val started: Boolean = false,
    /** Файл доиграл до конца. Кружок в ленте сворачивается, а новый запуск начинает файл сначала. */
    val ended: Boolean = false,
    /** The only queue snapshot in the app. Empty for voice/video notes and standalone files. */
    val queue: List<PlaybackItem> = emptyList(),
    /** Index of [item] in [queue] in the original source order, even when shuffle changes playback order. */
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.Off,
    val isBuffering: Boolean = false,
    /** 0..1 of the current media item that ExoPlayer has buffered. */
    val bufferedFraction: Float = 0f,
    /** Music and voice/video notes keep independent speeds; music defaults to 1x. */
    val musicSpeed: Float = 1f,
    val voiceSpeed: Float = 1f,
    /** Actual Media3 timeline availability; null falls back to the pure original-order policy. */
    val timelineHasNext: Boolean? = null,
    val timelineHasPrevious: Boolean? = null
) {
    /** Доля проигранного от 0 до 1. Пока длина неизвестна, берём длину из сообщения, если она есть. */
    val fraction: Float
        get() {
            val total = when {
                durationMs > 0L -> durationMs
                item != null && item.durationSeconds > 0 -> item.durationSeconds * 1000L
                else -> 0L
            }
            return if (total > 0L) (positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
        }

    val canNext: Boolean
        get() = timelineHasNext ?: canGoNext(queue.size, queueIndex, repeatMode, shuffleEnabled)

    val canPrevious: Boolean
        get() = item != null && (positionMs >= PREVIOUS_RESTART_THRESHOLD_MS ||
            (timelineHasPrevious ?: canGoPrevious(queue.size, queueIndex, repeatMode, shuffleEnabled)))

    val hasQueue: Boolean get() = queue.isNotEmpty()
}

internal fun canGoNext(
    queueSize: Int,
    index: Int,
    repeatMode: PlaybackRepeatMode,
    shuffleEnabled: Boolean
): Boolean {
    if (queueSize <= 1 || index !in 0 until queueSize) return false
    if (shuffleEnabled) return true
    return repeatMode == PlaybackRepeatMode.All || index < queueSize - 1
}

internal fun canGoPrevious(
    queueSize: Int,
    index: Int,
    repeatMode: PlaybackRepeatMode,
    shuffleEnabled: Boolean
): Boolean {
    if (queueSize <= 1 || index !in 0 until queueSize) return false
    if (shuffleEnabled) return true
    return repeatMode == PlaybackRepeatMode.All || index > 0
}

/** Музыка, которую прервало голосовое или кружок. Продолжается с [positionMs], когда прерывающий файл доиграет. */
internal data class InterruptedMusic(
    val item: PlaybackItem,
    val positionMs: Long,
    val queue: List<PlaybackItem> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.Off,
    val source: PlaybackQueueSource? = null
)

/**
 * Что ждёт музыку после запуска [next]. Играющая музыка ждёт, пока голосовое или кружок доиграет. Новая музыка заменяет
 * ждущую, а голосовое или кружок поверх другого голосового или кружка ждущую не трогают.
 */
internal fun interruptedAfterStart(
    current: PlaybackState,
    waiting: InterruptedMusic?,
    next: PlaybackItem,
    source: PlaybackQueueSource? = null
): InterruptedMusic? {
    val playingMusic = current.item?.takeIf {
        it.kind == MediaKind.Audio && !it.systemPrivate && !current.ended && !current.failed &&
            (current.playing || current.intentPlaying)
    }
    return when {
        next.kind == MediaKind.Audio && !next.systemPrivate -> null
        playingMusic != null -> InterruptedMusic(
            item = playingMusic,
            positionMs = current.positionMs,
            queue = current.queue,
            queueIndex = current.queueIndex,
            shuffleEnabled = current.shuffleEnabled,
            repeatMode = current.repeatMode,
            source = source
        )
        else -> waiting
    }
}

/**
 * Файл очереди, который идёт сразу за местом [index], если на этом месте играет [currentKey]. Место ищем по номеру, а не по ключу:
 * один файл может стоять в очереди дважды. Последний файл и место, где играет другой файл, дают null.
 */
internal fun nextInQueue(queue: List<PlaybackItem>, index: Int, currentKey: String): PlaybackItem? {
    if (queue.getOrNull(index)?.key != currentKey) return null
    return queue.getOrNull(index + 1)
}

/** Очередь музыки: файлы по порядку и место играющего файла в них. Пустая очередь не имеет места. */
internal data class PlaybackQueue(val items: List<PlaybackItem> = emptyList(), val index: Int = -1)

/**
 * Очередь после повторного выбора файла, который уже играет. Голосовое и кружок очередь не трогают. Музыка: переданная очередь
 * заменяет прежнюю. Без неё прежняя остаётся, только если файл выбран из того же места: очередь другого чата не должна играть дальше.
 */
internal fun queueAfterReselect(previous: PlaybackQueue, next: PlaybackItem, given: PlaybackQueue?, currentOrigin: PlaybackOrigin?): PlaybackQueue {
    if (next.kind != MediaKind.Audio) return previous
    return given ?: previous.takeIf { currentOrigin == next.origin } ?: PlaybackQueue()
}

/** A queue built for playback never contains secret, secret-chat or view-once items and keeps the selected index. */
internal fun queueForPlayback(items: List<PlaybackItem>, index: Int): PlaybackQueue {
    val selected = items.getOrNull(index) ?: return PlaybackQueue()
    if (selected.systemPrivate) return PlaybackQueue()
    val visible = items.filterNot { it.systemPrivate }
    val visibleIndex = visible.indexOfFirst { it.key == selected.key }
    return if (visibleIndex >= 0) PlaybackQueue(visible, visibleIndex) else PlaybackQueue()
}

/** Sleep is based on elapsed real time (not wall clock), so changing the phone clock cannot extend playback. */
val SLEEP_TIMER_MINUTES: Set<Int> = setOf(15, 30, 60)

data class SleepTimerState(val accountId: String, val deadlineElapsedMs: Long)

fun sleepTimerRemainingMinutes(deadlineElapsedMs: Long, nowElapsedMs: Long): Int =
    ((deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0L) + 59_999L).div(60_000L).toInt()

/** Only reachable, public tracks of the *currently playing* account may be selected from a queue row. */
internal fun canSelectQueueIndex(state: PlaybackState, index: Int): Boolean {
    val current = state.item ?: return false
    val target = state.queue.getOrNull(index) ?: return false
    return current.isMusic && !current.systemPrivate && target.isMusic && !target.systemPrivate &&
        target.accountId == current.accountId && target.key != current.key
}

/** A page only extends the active account's public music timeline; duplicates and protected media are dropped. */
internal fun appendMusicPage(existing: List<PlaybackItem>, page: List<PlaybackItem>, accountId: String): List<PlaybackItem> {
    val known = existing.mapTo(HashSet(existing.size + page.size)) { it.key }
    return page.filter { candidate ->
        candidate.isMusic && !candidate.systemPrivate && candidate.accountId == accountId && known.add(candidate.key)
    }
}

/** Publishes a lazily resolved path/growing source without changing item identity, queue order or index. */
internal fun PlaybackState.withResolvedSource(resolved: PlaybackItem): PlaybackState {
    fun merge(current: PlaybackItem): PlaybackItem =
        if (current.key == resolved.key) current.copy(path = resolved.path, growing = resolved.growing) else current
    val updatedItem = item?.let { merge(it) }
    val updatedQueue = queue.map { merge(it) }
    if (updatedItem == item && updatedQueue == queue) return this
    return copy(item = updatedItem, queue = updatedQueue)
}

/** New original queue and the actual shuffled playback order after inserting an item for Play Next. */
internal data class PlayNextQueueResult(
    val queue: List<PlaybackItem>,
    val queueIndex: Int,
    val playbackOrder: List<Int>
)

/**
 * Inserts [item] directly after the current item in both the visible [queue] and the actual shuffled playback order.
 * [shuffleOrder] contains original queue indices in playback order; [existingIndex] is removed first when the item is
 * already queued. The result's [playbackOrder] is a permutation of the new queue indices.
 */
internal fun queueAfterPlayNext(
    queue: List<PlaybackItem>,
    currentIndex: Int,
    item: PlaybackItem,
    existingIndex: Int,
    shuffleOrder: List<Int>
): PlayNextQueueResult {
    val current = queue.getOrNull(currentIndex)
        ?: return PlayNextQueueResult(queue, currentIndex, queue.indices.toList())
    val removedIndex = existingIndex.takeIf { it in queue.indices && it != currentIndex }
    val withoutItem = queue.filterIndexed { index, _ -> index != removedIndex }
    val currentAfter = withoutItem.indexOfFirst { it.key == current.key }.coerceAtLeast(0)
    val insertAt = (currentAfter + 1).coerceIn(0, withoutItem.size)
    val newQueue = withoutItem.toMutableList().apply { add(insertAt, item) }
    val newItemIndex = insertAt

    val oldToNew = HashMap<Int, Int>(queue.size)
    for (oldIndex in queue.indices) {
        if (oldIndex == removedIndex) continue
        val newIndex = newQueue.indexOfFirst { it.key == queue[oldIndex].key }
        if (newIndex >= 0) oldToNew[oldIndex] = newIndex
    }
    var order = shuffleOrder
        .filter { it in queue.indices && it != removedIndex }
        .mapNotNull { oldToNew[it] }
        .distinct()
    val newCurrentIndex = oldToNew[currentIndex] ?: currentAfter
    if (order.size != newQueue.size - 1 || newCurrentIndex !in order) {
        order = newQueue.indices.filter { it != newItemIndex }
    }
    val currentOrderIndex = order.indexOf(newCurrentIndex)
    val insertOrderAt = if (currentOrderIndex >= 0) currentOrderIndex + 1 else order.size
    val playbackOrder = order.toMutableList().apply { add(insertOrderAt, newItemIndex) }
    return PlayNextQueueResult(newQueue, newCurrentIndex, playbackOrder)
}

/** Позиция, с которой продолжается файл [key]: прерванная музыка возвращается к своему месту, остальные файлы идут с начала. */
internal fun resumePositionMs(waiting: InterruptedMusic?, key: String): Long = waiting?.takeIf { it.item.key == key }?.positionMs ?: 0L

/** Saved playback context restored when the user reselects the track that a voice/video note interrupted. */
internal data class InterruptedMusicRestore(
    val queue: PlaybackQueue,
    val positionMs: Long,
    val source: PlaybackQueueSource?,
    val shuffleEnabled: Boolean,
    val repeatMode: PlaybackRepeatMode
)

/**
 * Reselecting the interrupted track must resume it instead of starting over. The explicit queue/source from the
 * reselect (for example the Music tab) wins, but the saved position and modes still come from [waiting].
 */
internal fun interruptedMusicRestore(
    waiting: InterruptedMusic?,
    key: String,
    givenQueue: PlaybackQueue?,
    givenSource: PlaybackQueueSource?
): InterruptedMusicRestore? {
    val restore = waiting?.takeIf { it.item.key == key } ?: return null
    val savedQueue = if (restore.queueIndex in restore.queue.indices) {
        PlaybackQueue(restore.queue, restore.queueIndex)
    } else {
        PlaybackQueue()
    }
    return InterruptedMusicRestore(
        queue = givenQueue ?: savedQueue,
        positionMs = restore.positionMs,
        source = givenSource ?: restore.source,
        shuffleEnabled = restore.shuffleEnabled,
        repeatMode = restore.repeatMode
    )
}

/**
 * Key файла в общем плеере. Включает аккаунт, сообщение-источник, вид и fileId: голый TDLib fileId не глобальный.
 * Файл без Telegram origin получает отдельное пространство имён через [localPlaybackKey].
 */
fun MessageMedia.playbackKey(accountId: String, origin: PlaybackOrigin? = null): String {
    val owner = origin?.accountId?.takeIf { it.isNotBlank() } ?: accountId.ifBlank { LEGACY_PLAYBACK_ACCOUNT_ID }
    val occurrence = origin?.let { "chat:${it.chatId}:message:${it.messageId}" } ?: "unbound"
    return "tg|$owner|$occurrence|${kind.name}|$fileId"
}

/** Local files are not Telegram media and must never collide with a TDLib file id from any account. */
fun localPlaybackKey(accountId: String, path: String): String =
    "local|${accountId.ifBlank { LEGACY_PLAYBACK_ACCOUNT_ID }}|${File(path).absoluteFile.normalize().path}"

/** Файл из сообщения как элемент общего плеера. [origin] указывает сообщение, в котором лежит файл. */
fun MessageMedia.playbackItem(
    path: String,
    fallbackTitle: String,
    origin: PlaybackOrigin? = null,
    artworkPath: String? = null,
    growing: GrowingPlaybackSource? = null,
    accountId: String = origin?.accountId ?: LEGACY_PLAYBACK_ACCOUNT_ID,
    sizeBytes: Long = 0L,
    album: String = "",
    occurrenceId: String? = null,
    viewOnce: Boolean = false
): PlaybackItem {
    val owner = origin?.accountId?.takeIf { it.isNotBlank() } ?: accountId
    val fileTitle = fileName.ifBlank { fallbackTitle }
    val trackTitle = if (kind == MediaKind.Audio && title.isNotBlank()) title else fileTitle
    // У музыки в плеере показываем теги: «Исполнитель — Название», а не имя файла.
    val displayTitle = if (kind == MediaKind.Audio && (title.isNotBlank() || performer.isNotBlank())) {
        listOf(performer, if (title.isBlank()) fileTitle else title).filter { it.isNotBlank() }.joinToString(" — ")
    } else {
        fileTitle
    }
    val key = playbackKey(owner, origin)
    return PlaybackItem(
        key = key,
        kind = kind,
        path = path,
        title = displayTitle,
        durationSeconds = durationSeconds,
        origin = origin,
        artworkPath = if (kind == MediaKind.Audio) artworkPath else null,
        performer = if (kind == MediaKind.Audio) performer else "",
        secret = isSecret,
        growing = growing,
        accountId = owner,
        fileId = fileId,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        isSecretChat = isSecretChat,
        hasSpoiler = hasSpoiler,
        viewOnce = viewOnce,
        trackTitle = trackTitle,
        album = if (kind == MediaKind.Audio) album else "",
        occurrenceId = occurrenceId ?: key
    )
}

/** Скорость без лишнего нуля: «1» и «2», но «0.5» и «1.5». */
internal fun speedLabel(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

/** Whether restarting the current item with Previous should resume playback after the seek to zero. */
internal fun PlaybackState.restartResumeIntent(): Boolean = playing || intentPlaying

/** Music and voice/video note speeds never affect each other. */
internal fun playbackSpeedFor(kind: MediaKind, musicSpeed: Float, voiceSpeed: Float): Float =
    if (kind == MediaKind.Audio) musicSpeed else voiceSpeed

/** The inline voice/video-note menu is bound to the voice preference even when unrelated music is current. */
internal fun PlaybackState.inlineVoiceSpeed(): Float = voiceSpeed

/** Metadata for Media3/lockscreen. Secret content has no title, artist, album, artwork or duration. */
internal fun PlaybackItem.systemMetadata(): PlaybackMetadata =
    if (systemPrivate) {
        PlaybackMetadata()
    } else {
        PlaybackMetadata(
            title = trackTitle.ifBlank { title },
            artist = performer,
            album = album,
            artworkPath = if (kind == MediaKind.Audio) artworkPath else null,
            durationMs = durationSeconds.coerceAtLeast(0) * 1000L
        )
    }

/**
 * Only 1–2 following, still unresolved public tracks are prefetched; never a whole channel and never private items.
 * [playbackOrder] is the actual Media3 order (shuffled when enabled), not the visible original queue. Repeat All scans
 * past the end of that order; Repeat One and Repeat Off do not wrap.
 */
internal fun prefetchCandidates(
    state: PlaybackState,
    playbackOrder: List<Int> = state.queue.indices.toList(),
    max: Int = 2
): List<PlaybackItem> {
    if (max <= 0 || state.item?.kind != MediaKind.Audio) return emptyList()
    if (state.queue.isEmpty() || state.queueIndex !in state.queue.indices) return emptyList()
    val order = playbackOrder.filter { it in state.queue.indices }
    val currentOrderIndex = order.indexOf(state.queueIndex)
    val following = when {
        currentOrderIndex < 0 -> state.queue.indices.drop(state.queueIndex + 1)
        state.repeatMode == PlaybackRepeatMode.All -> order.drop(currentOrderIndex + 1) + order.take(currentOrderIndex)
        else -> order.drop(currentOrderIndex + 1)
    }
    val result = ArrayList<PlaybackItem>(max)
    for (queueIndex in following) {
        val item = state.queue[queueIndex]
        if (item.systemPrivate || item.fileId == null) continue
        if (item.path.isNotBlank() || item.growing != null) continue
        result += item
        if (result.size == max) break
    }
    return result
}

/** A prefetch key is remembered only when policy allowed the request and the repository accepted it. */
internal fun shouldRememberPrefetch(allowed: Boolean, accepted: Boolean): Boolean = allowed && accepted

/**
 * ENDED is delivered asynchronously. A manual Next, a new source or a stop bumps the transition token, so only the
 * ENDED that still belongs to the current item may advance/restore anything.
 */
internal fun shouldHandleEnded(endedKey: String?, currentKey: String?, endedToken: Long, currentToken: Long): Boolean =
    endedKey != null && endedKey == currentKey && endedToken == currentToken

/**
 * User pause and a noisy route must clear intent. A temporary audio-focus loss keeps the user's intent so the system
 * can resume after a call, but a permanent loss makes ExoPlayer report playWhenReady=false: clear the intent so music
 * never starts by itself after the user regains the device.
 */
internal fun intentAfterPlayWhenReadyChange(
    previousIntent: Boolean,
    playWhenReady: Boolean,
    reason: Int,
    pendingDeferredResume: Boolean = false
): Boolean =
    when {
        // The player internally pauses while waiting for a completed growing file; that must not erase intent.
        pendingDeferredResume -> true
        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> if (playWhenReady) previousIntent else false
        else -> playWhenReady
    }
