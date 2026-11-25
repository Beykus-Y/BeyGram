package com.beykus.beygram.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Стартовый буфер видео в чате (`bufferForPlaybackMs` у [DefaultLoadControl]).
 *
 * [DEFAULT] не задаёт LoadControl вовсе: плеер работает со значениями Media3, как и раньше.
 * Остальные варианты существуют только для замеров на устройстве (Issue #1121) и выбираются в
 * «Диагностике». Пока замеры не сделаны, в обычном режиме значение по умолчанию не меняется.
 */
enum class VideoStartBuffer(val wire: String, val bufferForPlaybackMs: Int?) {
    DEFAULT("default", null),
    MS_100("100", 100),
    MS_250("250", 250),
    MS_500("500", 500),
    MS_1000("1000", 1000);

    companion object {
        /** Неизвестное или отсутствующее значение даёт [DEFAULT]. */
        fun fromWire(value: String?): VideoStartBuffer = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

/** LoadControl для плеера или null, если вариант [VideoStartBuffer.DEFAULT] оставляет настройки Media3. */
fun VideoStartBuffer.loadControlOrNull(): LoadControl? {
    val startMs = bufferForPlaybackMs ?: return null
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            startMs,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
        .build()
}

/**
 * Хранит выбранный вариант в обычных настройках приложения. Ошибки доступа к хранилищу не должны
 * ронять просмотр, поэтому любой сбой даёт [VideoStartBuffer.DEFAULT].
 */
object VideoStartBufferSettings {
    private const val PREFS = "video_playback_diagnostics"
    private const val KEY_START_BUFFER = "start_buffer"

    fun load(context: Context): VideoStartBuffer = try {
        VideoStartBuffer.fromWire(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_START_BUFFER, null))
    } catch (_: RuntimeException) {
        VideoStartBuffer.DEFAULT
    }

    fun save(context: Context, value: VideoStartBuffer) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_START_BUFFER, value.wire).apply()
        } catch (_: RuntimeException) {
            // Настройка не сохранилась: до перезапуска приложение всё равно работает с выбранным вариантом.
        }
    }
}
