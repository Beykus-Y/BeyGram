package com.beykus.beygram.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
    val failed: Boolean = false
) {
    /** Доля проигранного от 0 до 1. Пока длина неизвестна, она равна 0. */
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Файл из сообщения как элемент общего плеера. Ключ один на весь файл, поэтому повторный запуск не начинает его сначала. */
fun MessageMedia.playbackItem(path: String, fallbackTitle: String): PlaybackItem = PlaybackItem(
    key = "$kind:$fileId",
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
    private var progress: Job? = null

    /** Единственный ExoPlayer процесса. Создаётся на главном потоке при первом обращении. */
    fun player(context: Context): ExoPlayer = exo ?: ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { created ->
            created.addListener(listener)
            exo = created
        }

    /** Тот же файл ставит на паузу или продолжает, другой файл начинает играть сначала. */
    fun toggle(context: Context, item: PlaybackItem) {
        if (exo != null && _state.value.item?.key == item.key) togglePlayPause() else play(context, item)
    }

    /** Запускает файл сначала. Скорость сохраняется между файлами. */
    fun play(context: Context, item: PlaybackItem) {
        val target = player(context)
        target.setMediaItem(
            MediaItem.Builder()
                .setMediaId(item.key)
                .setUri(Uri.fromFile(File(item.path)))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                .build()
        )
        target.prepare()
        target.play()
        _state.value = PlaybackState(item = item, playing = true, speed = _state.value.speed)
        // Сервис нужен для уведомления и экрана блокировки. Фоновый запуск может не пройти, тогда звук всё равно играет.
        runCatching { context.applicationContext.startService(Intent(context.applicationContext, PlaybackService::class.java)) }
        startProgress()
    }

    fun togglePlayPause() {
        val target = exo ?: return
        if (target.isPlaying) target.pause() else target.play()
    }

    /** Пауза без сброса файла: так голосовое или кружок могут остановить музыку, не теряя её места. */
    fun pause() {
        exo?.pause()
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

    /** Останавливает звук и убирает файл из мини-плеера. */
    fun stop() {
        progress?.cancel()
        progress = null
        exo?.let {
            it.stop()
            it.clearMediaItems()
        }
        _state.value = PlaybackState(speed = _state.value.speed)
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying, durationMs = durationNow()) }
            if (isPlaying) startProgress() else syncPosition()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(durationMs = durationNow()) }
        }

        override fun onPlayerError(error: PlaybackException) {
            progress?.cancel()
            _state.update { it.copy(playing = false, failed = true) }
        }
    }

    private fun durationNow(): Long = exo?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

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
