package com.beykus.beygram.ui.screens.chat

import androidx.media3.common.Player

/** Состояние просмотра видео: что показать поверх кадра, что делает кнопка и где стоит ползунок. */
internal data class VideoPlayerState(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val failed: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    /** Доля проигранного для ползунка. Пока длина неизвестна, ползунок стоит в начале. */
    fun fraction(): Float = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Что делает кнопка: после конца файла она начинает его сначала, а не ставит на паузу. */
    fun primaryAction(): VideoAction = when {
        playing -> VideoAction.Pause
        ended -> VideoAction.Replay
        else -> VideoAction.Play
    }

    /** Плеер сообщил новое состояние. Буферизация и конец взаимно сбрасывают друг друга. */
    fun afterPlaybackState(playbackState: Int): VideoPlayerState = when (playbackState) {
        Player.STATE_BUFFERING -> copy(buffering = true, ended = false)
        Player.STATE_READY -> copy(buffering = false, ended = false)
        Player.STATE_ENDED -> copy(buffering = false, ended = true, playing = false)
        else -> this
    }

    /** Ошибка файла: спиннер и воспроизведение снимаются, вместо кадра показывается сообщение. */
    fun afterFailure(): VideoPlayerState = copy(playing = false, buffering = false, failed = true)
}

internal enum class VideoAction { Play, Pause, Replay }

/** Позиция для доли ползунка. Пока длина неизвестна, перематывать некуда: возвращает null. */
internal fun seekPositionMs(fraction: Float, durationMs: Long): Long? =
    if (durationMs > 0L) (fraction.coerceIn(0f, 1f) * durationMs).toLong() else null
