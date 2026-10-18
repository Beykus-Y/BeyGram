package com.beykus.beygram.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Файл для общего плеера. [key] отличает файлы друг от друга: повторный тап по тому же файлу ставит его на паузу. */
data class PlaybackItem(
    val key: String,
    val kind: MediaKind,
    val path: String,
    val title: String,
    val durationSeconds: Int
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
    /** Доля проигранного от 0 до 1. Пока длина неизвестна, она равна 0. */
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Музыка, которую прервало голосовое или кружок. Продолжается с [positionMs], когда прерывающий файл доиграет. */
internal data class InterruptedMusic(val item: PlaybackItem, val positionMs: Long)

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

/** Ключ файла в общем плеере: один на весь файл, поэтому повторный запуск не начинает его сначала. */
internal fun MessageMedia.playbackKey(): String = "$kind:$fileId"

/** Файл из сообщения как элемент общего плеера. */
fun MessageMedia.playbackItem(path: String, fallbackTitle: String): PlaybackItem = PlaybackItem(
    key = playbackKey(),
    kind = kind,
    path = path,
    title = fileName.ifBlank { fallbackTitle },
    durationSeconds = durationSeconds
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
    private var exo: ExoPlayer? = null
    private var controls: Player? = null
    private var appContext: Context? = null
    private var progress: Job? = null
    private var pendingStartFraction = 0f
    private var videoOwner: Any? = null
    private var videoView: TextureView? = null
    /** Музыка, которая ждёт, пока голосовое или кружок доиграет. Очищается при остановке и при новом запуске музыки. */
    private var interruptedMusic: InterruptedMusic? = null

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
            .also { it.addListener(listener) }
        exo = created
        appContext = context.applicationContext
        return object : ForwardingPlayer(created) {
            override fun play() {
                if (playbackState == Player.STATE_IDLE) prepare()
                if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
                super.play()
            }
        }
    }

    /** Тот же файл ставит на паузу или продолжает, другой файл начинает играть сначала. */
    fun toggle(context: Context, item: PlaybackItem) {
        if (exo != null && _state.value.item?.key == item.key) togglePlayPause() else play(context, item)
    }

    /**
     * Запускает файл с начала или с доли [startFraction]. Скорость сохраняется между файлами. Если запустили музыку, которую
     * прервало голосовое или кружок, она продолжается с того же места.
     */
    fun play(context: Context, item: PlaybackItem, startFraction: Float = 0f) {
        val target = player(context)
        syncPosition()
        val waiting = interruptedMusic
        val resumeMs = waiting?.takeIf { it.item.key == item.key }?.positionMs ?: 0L
        interruptedMusic = interruptedAfterStart(_state.value, waiting, item)
        target.setMediaItem(
            MediaItem.Builder()
                .setMediaId(item.key)
                .setUri(Uri.fromFile(File(item.path)))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                .build(),
            resumeMs
        )
        target.prepare()
        // Длина из сообщения округлена до секунды, поэтому долю применяем, когда плеер узнает настоящую длину файла.
        pendingStartFraction = startFraction.coerceIn(0f, 1f)
        target.play()
        _state.value = PlaybackState(item = item, playing = true, positionMs = resumeMs, speed = _state.value.speed)
        startPlaybackService()
        startProgress()
    }

    /** Пауза или продолжение. Конец файла и ошибку обрабатывает обёртка плеера, поэтому системные кнопки ведут себя так же. */
    fun togglePlayPause() {
        val target = controls ?: return
        // Решает то же состояние, что видит интерфейс: файл, который уже буферизуется, пауза отменяет, а не запускает снова.
        if (_state.value.playing) {
            pause()
            return
        }
        // Повтор доигравшего файла снимает отметку конца сразу, чтобы возврат музыки не сработал до первого события плеера.
        _state.update { it.copy(failed = false, ended = false) }
        startPlaybackService()
        target.play()
    }

    /** Пауза без сброса файла: так голосовое или кружок могут остановить музыку, не теряя её места. Состояние меняем сразу: если файл ещё буферизуется, плеер не пришлёт событие о паузе. */
    fun pause() {
        exo?.pause()
        _state.update { it.copy(playing = false) }
    }

    /** Перемотка по доле длины. Пока длина неизвестна, ничего не делает. */
    fun seekTo(fraction: Float) {
        val target = exo ?: return
        val duration = target.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val position = (fraction.coerceIn(0f, 1f) * duration).toLong()
        target.seekTo(position)
        _state.update { it.copy(positionMs = position) }
    }

    fun setSpeed(speed: Float) {
        exo?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
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

    /** Останавливает звук, убирает файл из мини-плеера и снимает уведомление, которое держал сервис. */
    fun stop() {
        progress?.cancel()
        progress = null
        pendingStartFraction = 0f
        interruptedMusic = null
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
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) applyPendingStartFraction()
            _state.update { it.copy(durationMs = durationNow(), ended = playbackState == Player.STATE_ENDED) }
            // Музыку возвращаем после того, как плеер закончит обрабатывать конец файла, а не внутри его обработчика.
            if (playbackState == Player.STATE_ENDED) scope.launch { resumeInterruptedMusic() }
        }

        // Перемотку с уведомления или экрана блокировки делает сам плеер, минуя PlaybackEngine: позицию обновляем и на паузе.
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            syncPosition()
        }

        override fun onPlayerError(error: PlaybackException) {
            progress?.cancel()
            _state.update { it.copy(playing = false, failed = true) }
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
