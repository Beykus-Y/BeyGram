package com.beykus.beygram.playback

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Замеры одного просмотра видео в чате (Issue #1121). Объект живёт вместе с плеером, а сводка
 * уходит в журнал диагностики один раз при освобождении плеера. Имён файлов и содержимого в сводке нет.
 *
 * Время берётся по [now], поэтому логику можно проверить тестом без плеера.
 */
class VideoPlaybackMetrics(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val heapUsedKb: () -> Long = ::currentHeapUsedKb
) {
    private val openedAt = now()
    private var firstFrameMs: Long? = null
    private var heapAtFirstFrameKb: Long? = null
    private var bufferingSince: Long? = null
    private var rebufferCount = 0
    private var rebufferTotalMs = 0L
    private var seekCount = 0
    private var seekSentAt: Long? = null
    private var lastSeekToReadyMs: Long? = null
    private val bytesRead = AtomicLong(0L)

    /** Первый кадр после создания плеера (TTFF). Повторные вызовы ничего не меняют. */
    fun onFirstFrame() {
        if (firstFrameMs != null) return
        firstFrameMs = now() - openedAt
        heapAtFirstFrameKb = heapUsedKb()
    }

    /**
     * Состояние буферизации из Media3 (`Player.STATE_*`). Буферизация до первого кадра — это старт,
     * а не ребуферизация, поэтому в счётчик она не попадает.
     */
    fun onBufferingChanged(buffering: Boolean) {
        val t = now()
        if (buffering) {
            if (bufferingSince == null) bufferingSince = t
            // Переход в буферизацию во время перемотки относится к перемотке, а не к ребуферизации.
            if (firstFrameMs != null && seekSentAt == null) rebufferCount++
        } else {
            bufferingSince?.let { since ->
                if (firstFrameMs != null && seekSentAt == null) rebufferTotalMs += t - since
            }
            bufferingSince = null
            seekSentAt?.let { sent ->
                lastSeekToReadyMs = t - sent
                seekSentAt = null
            }
        }
    }

    /** Пользователь перематывает. Время до готовности считается по [onBufferingChanged]. */
    fun onSeek() {
        seekCount++
        seekSentAt = now()
    }

    /** Байты, которые источник отдал плееру. Вызывается из потока загрузки, поэтому счётчик атомарный. */
    fun onBytesRead(count: Int) {
        if (count > 0) bytesRead.addAndGet(count.toLong())
    }

    /** Итоговая сводка для журнала. Значения без измерения пропускаются. */
    fun summary(startBuffer: VideoStartBuffer, fileSizeBytes: Long): Map<String, String> {
        val result = linkedMapOf(
            "start_buffer" to startBuffer.wire,
            "file_size_bytes" to fileSizeBytes.toString(),
            "bytes_read" to bytesRead.get().toString(),
            "rebuffer_count" to rebufferCount.toString(),
            "rebuffer_total_ms" to rebufferTotalMs.toString(),
            "seek_count" to seekCount.toString(),
            "heap_used_kb_end" to heapUsedKb().toString()
        )
        firstFrameMs?.let { result["ttff_ms"] = it.toString() }
        heapAtFirstFrameKb?.let { result["heap_used_kb_first_frame"] = it.toString() }
        lastSeekToReadyMs?.let { result["last_seek_to_ready_ms"] = it.toString() }
        return result
    }
}

private fun currentHeapUsedKb(): Long {
    val runtime = Runtime.getRuntime()
    return (runtime.totalMemory() - runtime.freeMemory()) / 1024L
}
