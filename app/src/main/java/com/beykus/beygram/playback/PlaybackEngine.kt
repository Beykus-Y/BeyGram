package com.beykus.beygram.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.runtime.compositionLocalOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.beykus.beygram.data.media.Mp4StreamingProbe
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Account identity for media opened anywhere in the shared BeyGram UI. */
val LocalPlaybackAccountId = compositionLocalOf { "legacy" }

/** Место файла в чате: по нему мини-плеер открывает сообщение, из которого файл запущен. */
data class PlaybackOrigin(
    val chatId: Long,
    val messageId: Long,
    /** Source identity must accompany any bare Telegram numeric ID. */
    val accountId: String = "legacy"
)

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

/** Файл для общего плеера. [key] отличает файлы друг от друга: повторный тап по тому же файлу ставит его на паузу. [origin] пустой, если файл запущен не из ленты чата. */
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
    val growing: GrowingPlaybackSource? = null
)

/** Состояние общего плеера для интерфейса. Без [item] плеер пуст, и мини-плеер не показывается. */
data class PlaybackState(
    val item: PlaybackItem? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val failed: Boolean = false,
    /** Плеер хотя бы раз сообщил, что файл реально играет. Отметка «прослушано» ставится по этому флагу, потому что [playing] выставляется уже при нажатии. */
    val started: Boolean = false,
    /** Файл доиграл до конца. Кружок в ленте сворачивается, а новый запуск начинает файл сначала. */
    val ended: Boolean = false
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
}

/** Музыка, которую прервало голосовое или кружок. Продолжается с [positionMs], когда прерывающий файл доиграет. */
internal data class InterruptedMusic(val item: PlaybackItem, val positionMs: Long)

/** A seek requested on an unseekable growing Ogg/Opus source; applied after completion. */
private data class DeferredGrowingSeek(val key: String, val positionMs: Long, val resume: Boolean)

/**
 * Что ждёт музыку после запуска [next]. Играющая музыка ждёт, пока голосовое или кружок доиграет. Новая музыка заменяет
 * ждущую, а голосовое или кружок поверх другого голосового или кружка ждущую не трогают.
 */
internal fun interruptedAfterStart(current: PlaybackState, waiting: InterruptedMusic?, next: PlaybackItem): InterruptedMusic? {
    val playingMusic = current.item?.takeIf { it.kind == MediaKind.Audio && current.playing }
    return when {
        next.kind == MediaKind.Audio -> null
        playingMusic != null -> InterruptedMusic(playingMusic, current.positionMs)
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

/** Позиция, с которой продолжается файл [key]: прерванная музыка возвращается к своему месту, остальные файлы идут с начала. */
internal fun resumePositionMs(waiting: InterruptedMusic?, key: String): Long = waiting?.takeIf { it.item.key == key }?.positionMs ?: 0L

/** Key файла в общем плеере: один на весь файл, поэтому повторный запуск не начинает его сначала. */
internal fun MessageMedia.playbackKey(): String = "$kind:$fileId"

/** Containers that can be parsed from the beginning; MP4/M4A additionally needs a fast-start index. */
private val DIRECT_AUDIO_MIME = setOf(
    "audio/mpeg", "audio/mp3", "audio/ogg", "audio/opus", "audio/aac",
    "audio/flac", "audio/x-flac", "audio/webm", "audio/3gpp", "audio/amr"
)
private val MP4_AUDIO_MIME = setOf("audio/mp4", "audio/m4a", "audio/x-m4a")
/** Ogg/Opus seek tables live at the end; an advertised total length makes Media3 read that page first. */
private val UNKNOWN_LENGTH_AUDIO_MIME = setOf("audio/ogg", "audio/opus")

/**
 * Whether an audio file can be played before TDLib finishes it. Formats with leading metadata
 * (MP3/OGG/Opus/AAC/FLAC/WebM) stream directly; MP4 audio requires a complete `moov` before `mdat`
 * and a supported audio codec, otherwise the viewer keeps the full-download fallback.
 */
internal fun canPlayAudioProgressively(mimeType: String, path: String?, availableBytes: Long): Boolean {
    val normalized = mimeType.substringBefore(';').trim().lowercase()
    return when (normalized) {
        in DIRECT_AUDIO_MIME -> true
        in MP4_AUDIO_MIME -> path != null && availableBytes > 0L &&
            runCatching { Mp4StreamingProbe.supportsAudioStreaming(File(path), availableBytes) }.getOrDefault(false)
        else -> false
    }
}

internal fun isDirectProgressiveAudio(mimeType: String): Boolean =
    mimeType.substringBefore(';').trim().lowercase() in DIRECT_AUDIO_MIME

internal fun isMp4AudioMime(mimeType: String): Boolean =
    mimeType.substringBefore(';').trim().lowercase() in MP4_AUDIO_MIME

/** The path to start from: completed file, or a readable partial prefix for a supported audio container. */
internal fun partialAudioPlaybackPath(file: MediaFileState, mimeType: String): String? =
    file.localPath ?: file.downloadPath?.takeIf {
        file.size > 0L && file.downloadedPrefixBytes > 0L &&
            canPlayAudioProgressively(mimeType, it, file.downloadedPrefixBytes)
    }

/** Ogg/Opus cannot seek before completion; defer the requested position until a seekable source is available. */
internal fun shouldDeferGrowingSeek(growing: GrowingPlaybackSource?, positionMs: Long): Boolean =
    growing != null && positionMs > 0L && !growing.isComplete() && !growing.advertiseLength

/** Builds live source callbacks for an in-progress audio/voice file. */
internal fun growingAudioSource(
    state: MediaFileState,
    fileId: Int,
    mimeType: String,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onNeedMore: () -> Unit,
    assumeSupported: Boolean = false
): GrowingPlaybackSource? {
    val path = state.downloadPath ?: return null
    if (state.localPath != null || state.size <= 0L || state.downloadedPrefixBytes <= 0L) return null
    if (!assumeSupported && !canPlayAudioProgressively(mimeType, path, state.downloadedPrefixBytes)) return null
    val normalized = mimeType.substringBefore(';').trim().lowercase()
    return GrowingPlaybackSource(
        totalSize = state.size,
        readablePrefix = { observeFile(fileId).value.downloadedPrefixBytes },
        isComplete = { observeFile(fileId).value.localPath != null },
        completedPath = { observeFile(fileId).value.localPath },
        isDownloading = { observeFile(fileId).value.isDownloading },
        onNeedMore = onNeedMore,
        advertiseLength = normalized !in UNKNOWN_LENGTH_AUDIO_MIME
    )
}

/** Файл из сообщения как элемент общего плеера. [origin] указывает сообщение, в котором лежит файл. */
fun MessageMedia.playbackItem(path: String, fallbackTitle: String, origin: PlaybackOrigin? = null, artworkPath: String? = null, growing: GrowingPlaybackSource? = null): PlaybackItem = PlaybackItem(
    key = playbackKey(),
    kind = kind,
    path = path,
    // У музыки в плеере показываем теги: «Исполнитель — Название», а не имя файла.
    title = if (kind == MediaKind.Audio && (title.isNotBlank() || performer.isNotBlank())) listOf(performer, title.ifBlank { fileName }).filter { it.isNotBlank() }.joinToString(" — ") else fileName.ifBlank { fallbackTitle },
    durationSeconds = durationSeconds,
    origin = origin,
    artworkPath = if (kind == MediaKind.Audio) artworkPath else null,
    performer = if (kind == MediaKind.Audio) performer else "",
    secret = isSecret,
    growing = growing
)

/** Скорость без лишнего нуля: «1» и «2», но «0.5» и «1.5». */
internal fun speedLabel(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

/**
 * Общий плеер приложения. Музыка, голосовые и кружки играют через один ExoPlayer, поэтому звук не обрывается
 * при переходе между экранами. Плеер живёт в процессе, а [PlaybackService] держит его на переднем плане,
 * пока он играет: уведомление, экран блокировки и гарнитура работают через систему.
 */
object PlaybackEngine {
    private const val PROGRESS_INTERVAL_MS = 250L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** A cover thumbnail may finish downloading after playback has already started. */
    fun updateArtwork(key: String, path: String) {
        _state.update { playback ->
            val item = playback.item
            if (item == null || item.key != key || item.artworkPath == path) playback
            else playback.copy(item = item.copy(artworkPath = path))
        }
    }
    private val _showInChat = MutableSharedFlow<PlaybackOrigin>(extraBufferCapacity = 1)
    /** Запросы открыть чат с сообщением текущего файла. Их разбирает корневой экран приложения. */
    val showInChat: SharedFlow<PlaybackOrigin> = _showInChat.asSharedFlow()
    private var exo: ExoPlayer? = null
    private var controls: Player? = null
    private var appContext: Context? = null
    private var progress: Job? = null
    private var pendingStartFraction = 0f
    private var deferredSeek: DeferredGrowingSeek? = null
    private var deferredSeekJob: Job? = null
    private var videoOwner: Any? = null
    private var videoView: TextureView? = null
    /** Музыка, которая ждёт, пока голосовое или кружок доиграет. Очищается при остановке и при новом запуске музыки. */
    private var interruptedMusic: InterruptedMusic? = null
    /** Очередь музыки в порядке показа. Когда файл очереди доиграл, следующий начинает играть сам. Сбрасывается при запуске файла вне очереди и при остановке. */
    private var queue: List<PlaybackItem> = emptyList()
    /** Место играющего файла в [queue]. По номеру место находится однозначно, даже если один файл в очереди стоит дважды. */
    private var queueIndex = -1
    /** Отметка «прослушано», которую плеер вызовет, когда файл начнёт играть. Работает, даже если пузырь уже ушёл с экрана. */
    private var startedCallback: Pair<String, () -> Unit>? = null
    /** Приложение на переднем плане. Отметку репозиторий принимает только отсюда, поэтому файл, начавший играть в фоне, ждёт возврата. */
    private var foreground = false

    /**
     * Общий плеер для системного управления: уведомление, экран блокировки и гарнитура. «Играть» после конца файла начинает
     * его сначала, а после ошибки загружает заново, как кнопка в приложении.
     */
    fun player(context: Context): Player = controls ?: createPlayer(context).also { controls = it }

    /** Единственный ExoPlayer процесса. Создаётся на главном потоке при первом обращении. */
    private fun createPlayer(context: Context): Player {
        val created = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.addListener(listener)
                // The user can choose speed on a bubble before the player is created.
                it.setPlaybackSpeed(_state.value.speed)
            }
        exo = created
        appContext = context.applicationContext
        return object : ForwardingPlayer(created) {
            override fun play() {
                if (deferredSeek != null) {
                    deferredSeek = deferredSeek?.copy(resume = true)
                    return
                }
                if (playbackState == Player.STATE_IDLE) prepare()
                if (playbackState == Player.STATE_ENDED) {
                    // Секретный файл после конца не повторяется ни кнопкой уведомления, ни мини-плеером.
                    if (_state.value.item?.secret == true) return
                    seekToDefaultPosition()
                }
                // Повтор доигравшего файла снимает отметку конца сразу, чтобы возврат музыки не сработал до первого события плеера.
                _state.update { it.copy(ended = false) }
                super.play()
            }

            override fun pause() {
                // System/lock-screen/headset pause must cancel a pending deferred resume as well.
                deferredSeek = deferredSeek?.copy(resume = false)
                super.pause()
            }
        }
    }

    /**
     * Тот же файл ставит на паузу или продолжает, другой файл начинает играть сначала. [onStarted] вызывается один раз, когда
     * файл начнёт играть, даже если пузырь, который его запустил, уже ушёл с экрана. Если это случилось в фоне, вызов ждёт [setForeground].
     */
    fun toggle(context: Context, item: PlaybackItem, onStarted: (() -> Unit)? = null) = startItem(context, item, emptyList(), -1, onStarted)

    /** Запускает [index]-й файл очереди [items]. Когда он доиграет, следующий файл очереди начинает играть сам. Тап по играющему файлу ставит его на паузу. */
    fun playQueue(context: Context, items: List<PlaybackItem>, index: Int, onStarted: (() -> Unit)? = null) {
        val item = items.getOrNull(index) ?: return
        startItem(context, item, items, index, onStarted)
    }

    /** Общая часть [toggle] и [playQueue]: тот же файл ставит на паузу или продолжает, другой запускает с очередью [items]. */
    private fun startItem(context: Context, item: PlaybackItem, items: List<PlaybackItem>, index: Int, onStarted: (() -> Unit)?) {
        if (onStarted != null) startedCallback = item.key to onStarted
        if (exo != null && _state.value.item?.key == item.key) {
            // Файл, который играет без очереди, например из ленты чата, берёт очередь вкладки: после паузы и продолжения она пойдёт дальше.
            // Тот же музыкальный файл из другого сообщения или чата сбрасывает очередь прошлого места, иначе следующий трек пошёл бы из чужого чата.
            val given = if (items.isNotEmpty()) PlaybackQueue(items, index) else null
            val next = queueAfterReselect(PlaybackQueue(queue, queueIndex), item, given, currentOrigin = _state.value.item?.origin)
            queue = next.items
            queueIndex = next.index
            // «Показать в чате» ведёт к сообщению, с которого файл запустили последним.
            _state.update { current ->
                val previous = current.item
                current.copy(item = item.copy(
                    artworkPath = item.artworkPath ?: previous?.artworkPath,
                    performer = item.performer.ifBlank { previous?.performer.orEmpty() }
                ))
            }
            togglePlayPause()
        } else {
            // Голосовое или кружок поверх музыки очередь не сбрасывают: музыка продолжится и дальше пойдёт по очереди.
            if (item.kind == MediaKind.Audio) {
                queue = items
                queueIndex = index
            }
            play(context, item)
        }
    }

    /** Приложение ушло в фон или вернулось на передний план. Отметка, которую файл начал в фоне, уходит на возврате. */
    fun setForeground(active: Boolean) {
        foreground = active
        if (active) fireStartedCallback()
    }

    /**
     * Запускает файл с начала или с доли [startFraction]. Скорость сохраняется между файлами. Если запустили музыку, которую
     * прервало голосовое или кружок, она продолжается с того же места.
     */
    @OptIn(UnstableApi::class)
    fun play(
        context: Context,
        item: PlaybackItem,
        startFraction: Float = 0f,
        onStarted: (() -> Unit)? = null,
        startPositionMs: Long? = null
    ) {
        val target = player(context)
        if (onStarted != null) startedCallback = item.key to onStarted
        syncPosition()
        clearDeferredSeek()
        // Отметка ждёт только свой файл: запуск другого файла её сбрасывает.
        startedCallback = startedCallback?.takeIf { it.first == item.key }
        val waiting = interruptedMusic
        val requestedStart = startPositionMs ?: resumePositionMs(waiting, item.key)
        val growing = item.growing
        val deferStart = shouldDeferGrowingSeek(growing, requestedStart)
        val resumeMs = if (deferStart) 0L else requestedStart
        if (deferStart) {
            // playWhenReady may still be true from the previous item; pause before replacing the source.
            target.pause()
        }
        interruptedMusic = interruptedAfterStart(_state.value, waiting, item)
        val mediaItem = MediaItem.Builder()
            .setMediaId(item.key)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
        if (growing == null) {
            target.setMediaItem(mediaItem.setUri(Uri.fromFile(File(item.path))).build(), resumeMs)
        } else {
            val factory = object : DataSource.Factory {
                override fun createDataSource(): DataSource = GrowingFileDataSource(item.path, growing)
            }
            val source = ProgressiveMediaSource.Factory(factory).createMediaSource(
                mediaItem.setUri("beygram-growing://${item.key}").build()
            )
            exo?.setMediaSource(source, resumeMs)
        }
        target.prepare()
        // Длина из сообщения округлена до секунды, поэтому долю применяем, когда плеер узнает настоящую длину файла.
        pendingStartFraction = if (startPositionMs == null && !deferStart) startFraction.coerceIn(0f, 1f) else 0f
        if (deferStart) {
            // Ogg/Opus cannot seek before completion; wait for a seekable full file instead of
            // letting ProgressiveMediaPeriod silently reset the seek to zero.
            deferredSeek = DeferredGrowingSeek(item.key, requestedStart, resume = true)
            _state.value = PlaybackState(item = item, playing = false, positionMs = requestedStart, speed = _state.value.speed)
            startDeferredSeekWatcher()
            return
        }
        target.play()
        _state.value = PlaybackState(item = item, playing = true, positionMs = resumeMs, speed = _state.value.speed)
        startPlaybackService()
        startProgress()
    }

    /** Перемотка по абсолютной позиции; для растущего Ogg запрос откладывается до полной загрузки. */
    fun seekToPosition(positionMs: Long) {
        val target = exo ?: return
        val position = positionMs.coerceAtLeast(0L)
        val item = _state.value.item
        val growing = item?.growing
        if (item != null && shouldDeferGrowingSeek(growing, position)) {
            val resume = _state.value.playing
            target.pause()
            deferredSeek = DeferredGrowingSeek(item.key, position, resume)
            _state.update { it.copy(positionMs = position, playing = false) }
            startDeferredSeekWatcher()
            return
        }
        if (item != null && growing != null && !growing.advertiseLength && growing.isComplete() && growing.completedPath() != null) {
            rebuildSeekableAt(item, position, _state.value.playing)
            return
        }
        clearDeferredSeek()
        target.seekTo(position)
        _state.update { it.copy(positionMs = position) }
    }

    /** Rebuilds the player on the completed file, preserving the requested position and play state. */
    private fun rebuildSeekableAt(item: PlaybackItem, positionMs: Long, resume: Boolean) {
        val path = item.growing?.completedPath() ?: return
        val target = exo ?: return
        val completed = item.copy(path = path, growing = null)
        pendingStartFraction = 0f
        target.setMediaItem(
            MediaItem.Builder()
                .setMediaId(completed.key)
                .setUri(Uri.fromFile(File(path)))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(completed.title).build())
                .build(),
            positionMs
        )
        target.prepare()
        if (resume) {
            target.play()
            startPlaybackService()
            startProgress()
        } else {
            target.pause()
            progress?.cancel()
            progress = null
        }
        _state.update { it.copy(item = completed, positionMs = positionMs, playing = resume) }
    }

    private fun startDeferredSeekWatcher() {
        deferredSeekJob?.cancel()
        deferredSeekJob = scope.launch {
            while (true) {
                val pending = deferredSeek ?: break
                val item = _state.value.item
                if (item?.key != pending.key) {
                    deferredSeek = null
                    break
                }
                val growing = item.growing
                if (growing?.isComplete() == true && growing.completedPath() != null) {
                    deferredSeek = null
                    rebuildSeekableAt(item, pending.positionMs, pending.resume)
                    break
                }
                delay(200)
            }
        }
    }

    private fun clearDeferredSeek() {
        deferredSeek = null
        deferredSeekJob?.cancel()
        deferredSeekJob = null
    }

    /** Отметка для файла, который продолжают после паузы: вызывается, когда он начнёт играть, даже если пузырь уже ушёл с экрана. */
    fun resume(key: String, onStarted: () -> Unit) {
        startedCallback = key to onStarted
        if (deferredSeek?.key == key) {
            deferredSeek = deferredSeek?.copy(resume = true)
            return
        }
        togglePlayPause()
    }

    /** Пауза или продолжение. Конец файла и ошибку обрабатывает обёртка плеера, поэтому системные кнопки ведут себя так же. */
    fun togglePlayPause() {
        val target = controls ?: return
        // A deferred Ogg seek is waiting for the completed file; starting at zero here would undo it.
        if (deferredSeek != null) {
            deferredSeek = deferredSeek?.copy(resume = true)
            return
        }
        // Решает то же состояние, что видит интерфейс: файл, который уже буферизуется, пауза отменяет, а не запускает снова.
        if (_state.value.playing) {
            pause()
            return
        }
        _state.update { it.copy(failed = false) }
        startPlaybackService()
        target.play()
    }

    /** Пауза без сброса файла: так голосовое или кружок могут остановить музыку, не теряя её места. Состояние меняем сразу: если файл ещё буферизуется, плеер не пришлёт событие о паузе. */
    fun pause() {
        exo?.pause()
        deferredSeek = deferredSeek?.copy(resume = false)
        _state.update { it.copy(playing = false) }
    }

    /** Перемотка по доле длины. [fallbackDurationMs] используется, если контейнер ещё не сообщил длину. */
    fun seekTo(fraction: Float, fallbackDurationMs: Long = 0L) {
        val target = exo ?: return
        val duration = target.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: fallbackDurationMs
        if (duration <= 0L) return
        val position = (fraction.coerceIn(0f, 1f) * duration.toFloat()).toLong()
        seekToPosition(position)
    }

    fun setSpeed(speed: Float) {
        if (speed !in PLAYBACK_SPEED_OPTIONS) return
        exo?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** Просит открыть чат с сообщением, из которого запущен файл. Экран приложения переходит к этому сообщению. */
    fun requestShowInChat(origin: PlaybackOrigin) {
        _showInChat.tryEmit(origin)
    }

    /** Выводит видео текущего файла на [view]. Пузырь кружка вызывает это, пока кружок показан: без экрана звук продолжает играть. */
    fun attachVideo(owner: Any, view: TextureView) {
        val target = exo ?: return
        if (videoOwner === owner && videoView === view) return
        videoView?.let { target.clearVideoTextureView(it) }
        videoOwner = owner
        videoView = view
        target.setVideoTextureView(view)
    }

    /** Снимает видео, если его держит [owner]. Чужой пузырь ничего не снимает, поэтому порядок ухода с экрана не важен. */
    fun detachVideo(owner: Any) {
        if (videoOwner !== owner) return
        videoView?.let { exo?.clearVideoTextureView(it) }
        videoOwner = null
        videoView = null
    }

    /** Останавливает секретный файл, когда пользователь ушёл из чата или свернул приложение. Остальные файлы играют дальше. */
    fun stopSecret() {
        if (_state.value.item?.secret != true) return
        val music = interruptedMusic
        val context = appContext
        if (music == null || context == null) {
            stop()
            return
        }
        // Музыка, которую секретный файл прервал, продолжается с того же места и из той же очереди, как после его конца.
        halt()
        play(context, music.item)
    }

    /** Останавливает звук, убирает файл из мини-плеера и снимает уведомление, которое держал сервис. Забывает музыку и очередь. */
    fun stop() {
        interruptedMusic = null
        queue = emptyList()
        queueIndex = -1
        halt()
    }

    /** Останавливает плеер и сервис, но оставляет ждущую музыку и её очередь: их продолжает [play]. */
    private fun halt() {
        progress?.cancel()
        progress = null
        clearDeferredSeek()
        pendingStartFraction = 0f
        startedCallback = null
        exo?.let {
            it.stop()
            it.clearMediaItems()
        }
        _state.value = PlaybackState(speed = _state.value.speed)
        appContext?.let { it.stopService(Intent(it, PlaybackService::class.java)) }
    }

    /** Сервис нужен для уведомления и экрана блокировки. Фоновый запуск может не пройти, тогда звук всё равно играет. */
    private fun startPlaybackService() {
        val context = appContext ?: return
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying, durationMs = durationNow(), started = it.started || isPlaying) }
            if (isPlaying) startProgress() else syncPosition()
            if (isPlaying) fireStartedCallback()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) applyPendingStartFraction()
            _state.update { it.copy(durationMs = durationNow(), ended = playbackState == Player.STATE_ENDED) }
            // Следующий файл ставим после того, как плеер закончит обрабатывать конец файла, а не внутри его обработчика.
            if (playbackState == Player.STATE_ENDED) scope.launch { advanceAfterEnd() }
        }

        // Перемотку с уведомления или экрана блокировки делает сам плеер, минуя PlaybackEngine: позицию обновляем и на паузе.
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            syncPosition()
        }

        override fun onPlayerError(error: PlaybackException) {
            progress?.cancel()
            clearDeferredSeek()
            _state.update { it.copy(playing = false, failed = true) }
            // Голосовое или кружок, которые не открылись, не должны оставить прерванную музыку навсегда на паузе.
            val music = interruptedMusic ?: return
            val context = appContext ?: return
            if (_state.value.item?.key == music.item.key) return
            scope.launch { play(context, music.item) }
        }
    }

    private fun durationNow(): Long = exo?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

    /** Доля начала становится позицией, когда плеер знает настоящую длину файла. Пока длина неизвестна, доля ждёт. */
    private fun applyPendingStartFraction() {
        val target = exo ?: return
        val duration = target.duration
        if (pendingStartFraction <= 0f || duration == C.TIME_UNSET || duration <= 0L) return
        target.seekTo((pendingStartFraction * duration).toLong())
        pendingStartFraction = 0f
    }

    /** Голосовое или кружок доиграл: музыка, которую он прервал, продолжается с того же места. Если файл уже снова запущен, возврат отменяется. */
    private fun resumeInterruptedMusic() {
        val music = interruptedMusic ?: return
        if (!_state.value.ended) return
        val context = appContext ?: return
        play(context, music.item)
    }

    /** Файл доиграл: сначала возвращаем музыку, которую прервало голосовое или кружок, иначе запускаем следующий файл очереди. */
    private fun advanceAfterEnd() {
        if (interruptedMusic != null) resumeInterruptedMusic()
        // Секретный файл доиграл: его нельзя перемотать и запустить снова, поэтому он уходит из плеера.
        else if (_state.value.item?.secret == true) stop()
        else playNextInQueue()
    }

    /** Следующий файл очереди. Если текущий файл не из очереди или отметка конца уже снята, ничего не делает. */
    private fun playNextInQueue() {
        if (!_state.value.ended) return
        val current = _state.value.item?.key ?: return
        val next = nextInQueue(queue, queueIndex, current) ?: return
        val context = appContext ?: return
        queueIndex += 1
        play(context, next)
    }

    /** Отдаёт отметку текущему файлу один раз, когда он уже играет и приложение на переднем плане. Если файл сменился, отметка уже сброшена в [play]. */
    private fun fireStartedCallback() {
        val pending = startedCallback ?: return
        if (!foreground || !_state.value.started || pending.first != _state.value.item?.key) return
        startedCallback = null
        pending.second()
    }

    private fun syncPosition() {
        val position = exo?.currentPosition ?: return
        _state.update { it.copy(positionMs = position) }
    }

    /** Обновляет позицию, пока файл играет. Пауза и конец файла останавливают цикл. */
    private fun startProgress() {
        progress?.cancel()
        progress = scope.launch {
            while (true) {
                syncPosition()
                val target = exo ?: break
                if (!target.isPlaying) break
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }
}

/**
 * Reads a growing TDLib file. Only [GrowingPlaybackSource.readablePrefix] is guaranteed readable
 * before completion; file.length() may include unwritten/garbage regions. A stalled transfer fails
 * after a short grace period so a paused/cancelled download cannot block the player forever.
 */
@OptIn(UnstableApi::class)
private class GrowingFileDataSource(
    private val filePath: String,
    private val source: GrowingPlaybackSource
) : DataSource {
    private var file: RandomAccessFile? = null
    private var position = 0L
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        close()
        val opened = RandomAccessFile(filePath, "r")
        opened.seek(dataSpec.position)
        file = opened
        position = dataSpec.position
        uri = dataSpec.uri
        if (!source.isComplete() && !source.advertiseLength) return C.LENGTH_UNSET.toLong()
        val length = if (source.isComplete()) opened.length() else source.totalSize
        val remaining = (length - position).coerceAtLeast(0L)
        return if (dataSpec.length == C.LENGTH_UNSET.toLong()) remaining
        else minOf(remaining, dataSpec.length)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val opened = file ?: return C.RESULT_END_OF_INPUT
        val startedAt = SystemClock.elapsedRealtime()
        var requestedAt = 0L
        while (true) {
            val readable = if (source.isComplete()) opened.length()
            else minOf(source.readablePrefix(), opened.length())
            val available = readable - position
            if (available > 0L) {
                val toRead = minOf(available, length.toLong()).toInt()
                val read = opened.read(buffer, offset, toRead)
                if (read > 0) {
                    position += read
                    return read
                }
            }
            if (source.isComplete()) return C.RESULT_END_OF_INPUT
            val now = SystemClock.elapsedRealtime()
            if (!source.isDownloading() && now - startedAt >= NO_TRANSFER_TIMEOUT_MS) {
                throw IOException("Audio download was cancelled")
            }
            if (now - requestedAt >= REQUEST_INTERVAL_MS) {
                requestedAt = now
                source.onNeedMore()
            }
            if (now - startedAt >= STALL_TIMEOUT_MS) {
                throw IOException("Timed out waiting for the next audio chunk")
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting for audio", interrupted)
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { file?.close() }
        file = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
        const val REQUEST_INTERVAL_MS = 1_000L
        const val NO_TRANSFER_TIMEOUT_MS = 10_000L
        const val STALL_TIMEOUT_MS = 45_000L
    }
}
