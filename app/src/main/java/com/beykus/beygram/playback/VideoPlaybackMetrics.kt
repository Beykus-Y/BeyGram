package com.beykus.beygram.playback

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Замеры одного просмотра видео в чате (Issue #1121). Объект живёт вместе с плеером, а сводка
 * уходит в журнал диагностики один раз при освобождении плеера, и только если видео начинало
 * играть или перематывалось. Имён файлов и содержимого в сводке нет.
 *
 * Определения:
 * - `ttff_ms`: от запроса воспроизведения до первого момента, когда плеер сообщил «играет».
 *   Кадр, отрисованный на паузе до запроса, на это значение не влияет и его не теряет.
 * - `last_seek_to_ready_ms`: от перемотки до готовности. Готовность наступает либо по переходу в READY
 *   после буферизации, либо сразу, если перемотка применилась без смены состояния (READY не потерян).
 * - Ребуферизация: вход в буферизацию во время воспроизведения (запрос play активен), не во время
 *   перемотки и не до первого старта. Повторные сигналы одного и того же состояния не считаются заново.
 *
 * Время берётся по [now], поэтому логику можно проверить тестом без плеера.
 */
class VideoPlaybackMetrics(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val heapUsedKb: () -> Long = ::currentHeapUsedKb
) {
    private var playRequestedAt: Long? = null
    private var started = false
    private var ttffMs: Long? = null
    private var heapAtStartKb: Long? = null

    private var bufferingSince: Long? = null
    /** Текущая буферизация засчитана как ребуферизация (нужна, чтобы не считать её дважды и не учесть не по месту). */
    private var bufferingCounted = false
    private var rebufferCount = 0
    private var rebufferTotalMs = 0L

    private var seekCount = 0
    private var seekPending = false
    private var seekSentAt: Long? = null
    private var lastSeekToReadyMs: Long? = null

    private val bytesRead = AtomicLong(0L)

    /** Пользователь (или автостарт страницы) попросил воспроизведение. От этого момента считается TTFF. */
    fun onPlayRequested() {
        if (playRequestedAt == null) playRequestedAt = now()
    }

    /**
     * Плеер сообщил, играет он или нет. Первое «играет» после запроса даёт TTFF. Кадр, который был
     * готов до запроса, на TTFF не влияет: до «играет» он не нужен.
     */
    fun onPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying || started) return
        started = true
        val requested = playRequestedAt ?: return
        ttffMs = now() - requested
        heapAtStartKb = heapUsedKb()
    }

    /**
     * Состояние буферизации из Media3 (`Player.STATE_*`) и `playWhenReady`. Буферизация, которая
     * пришла на паузе или до первого старта, в ребуферизации не учитывается.
     */
    fun onPlaybackState(state: Int, playWhenReady: Boolean) {
        val t = now()
        if (state == BUFFERING_STATE) {
            // Повторный сигнал буферизации ничего не меняет: вход в неё учитывается один раз.
            if (bufferingSince != null) return
            bufferingSince = t
            bufferingCounted = started && !seekPending && playWhenReady
            if (bufferingCounted) rebufferCount++
            return
        }
        bufferingSince?.let { since ->
            if (bufferingCounted) rebufferTotalMs += t - since
        }
        bufferingSince = null
        bufferingCounted = false
        if (state == READY_STATE) completeSeekIfPending(t)
    }

    /** Пользователь перематывает. */
    fun onSeek() {
        seekCount++
        seekPending = true
        seekSentAt = now()
    }

    /**
     * Перемотка применилась (`onPositionDiscontinuity` с причиной SEEK). Если плеер уже готов и
     * буферизации нет, перемотка без смены состояния завершена сразу. Иначе ждём перехода в READY.
     */
    fun onSeekApplied(ready: Boolean) {
        if (!seekPending) return
        if (ready && bufferingSince == null) completeSeekIfPending(now())
    }

    /** Байты, которые источник отдал плееру. Вызывается из потока загрузки, поэтому счётчик атомарный. */
    fun onBytesRead(count: Int) {
        if (count > 0) bytesRead.addAndGet(count.toLong())
    }

    /** Сводка пишется только для просмотров, где видео начинало играть или перематывалось. */
    fun hasPlayed(): Boolean = started || seekCount > 0

    /**
     * Итоговая сводка для журнала. [streaming] = true, если видео шло из растущего файла TDLib (там
     * действует стартовый буфер), false для полностью скачанного файла. Значения без измерения пропускаются.
     */
    fun summary(startBuffer: VideoStartBuffer, fileSizeBytes: Long, streaming: Boolean): Map<String, String> {
        val result = linkedMapOf(
            "start_buffer" to startBuffer.wire,
            "source" to (if (streaming) "growing_file" else "complete_file"),
            "file_size_bytes" to fileSizeBytes.toString(),
            "bytes_read" to bytesRead.get().toString(),
            "rebuffer_count" to rebufferCount.toString(),
            "rebuffer_total_ms" to rebufferTotalMs.toString(),
            "seek_count" to seekCount.toString(),
            "heap_used_kb_end" to heapUsedKb().toString()
        )
        ttffMs?.let { result["ttff_ms"] = it.toString() }
        heapAtStartKb?.let { result["heap_used_kb_at_start"] = it.toString() }
        lastSeekToReadyMs?.let { result["last_seek_to_ready_ms"] = it.toString() }
        return result
    }

    private fun completeSeekIfPending(t: Long) {
        if (!seekPending) return
        seekPending = false
        seekSentAt?.let { lastSeekToReadyMs = t - it }
        seekSentAt = null
    }

    private companion object {
        // Значения совпадают с Player.STATE_BUFFERING и Player.STATE_READY из Media3; константы нужны,
        // чтобы логику можно было проверить на JVM без Media3.
        const val BUFFERING_STATE = 2
        const val READY_STATE = 3
    }
}

private fun currentHeapUsedKb(): Long {
    val runtime = Runtime.getRuntime()
    return (runtime.totalMemory() - runtime.freeMemory()) / 1024L
}
