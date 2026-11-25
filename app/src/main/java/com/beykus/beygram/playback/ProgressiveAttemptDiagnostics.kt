package com.beykus.beygram.playback

import android.os.SystemClock
import com.beykus.beygram.data.media.Mp4Layout
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val progressiveAttemptCounter = AtomicInteger(0)

/** Номер открытия страницы с видео в этом процессе. Нужен, чтобы отличать повторные попытки в логе. */
fun nextProgressiveAttemptNumber(): Int = progressiveAttemptCounter.incrementAndGet()

/**
 * Диагностика прогрессивного пути воспроизведения (Issue #1174). Одна запись на фактическую попытку прогрессивного
 * старта (проба, источник или воспроизведение), в том числе без старта. Страницы без такой попытки не пишутся
 * (см. [hasAttempt]). В записи нет имён файлов, путей, id чатов и сообщений,
 * содержимого. Ключей не больше 24, потому что журнал отчётов обрезает лишние атрибуты.
 *
 * Поток: главный поток меняет состояние страницы и пробы, поток загрузки ([onRead], [onWait], [onNeedMore],
 * [onStall]) и поток плеера (`createDataSource`) меняют только атомарные счётчики и отметку первого раза.
 */
internal class ProgressiveAttemptDiagnostics(
    private val attempt: Int,
    private val senderSupportsStreaming: Boolean,
    private val now: () -> Long = SystemClock::elapsedRealtime
) {
    private val openedAt = now()
    private var finished = false

    // Снимок состояния файла, его обновляет страница при каждом изменении прогресса.
    private var hasPartialPath = false
    private var prefixBytes = 0L
    private var totalBytes = 0L
    private var downloadOffset = 0L
    private var downloading = false
    // Файл уже полностью на диске: прогрессивный путь для него не нужен.
    private var completed = false

    // Проба: запуски, отмены перезапуском, суммарное время, последний результат и раскладка боксов.
    private var probeRuns = 0
    private var probeCancelled = 0
    private var probeTotalMs = 0L
    private var probeStartedAt: Long? = null
    private var probeResult: String? = null
    private var layout: Mp4Layout? = null

    // Решение о разблокировке и источник.
    private var unlockedMs: Long? = null
    private var rejectedReason: String? = null
    @Volatile private var sourceCreated = false

    // Чтение и ожидание данных. Пишет поток загрузки, поэтому атомарно.
    private val firstReadMs = AtomicLong(-1L)
    private val bytesRead = AtomicLong(0L)
    private val readWaits = AtomicInteger(0)
    private val needMoreCalls = AtomicInteger(0)
    private val stallNoTransfer = AtomicInteger(0)
    private val stallTimeout = AtomicInteger(0)

    // Главный поток: состояния Media3 с первой отметкой времени, воспроизведение, ошибка.
    private val stateMarks = LinkedHashMap<String, Long>()
    private var playingMs: Long? = null
    private var playerError: String? = null

    /** Текущий снимок файла. Вызывается при каждом изменении прогресса. */
    fun snapshot(
        hasPartialPath: Boolean,
        prefixBytes: Long,
        totalBytes: Long,
        downloadOffset: Long,
        downloading: Boolean,
        completed: Boolean = false
    ) {
        this.hasPartialPath = hasPartialPath
        this.prefixBytes = prefixBytes
        this.totalBytes = totalBytes
        this.downloadOffset = downloadOffset
        this.downloading = downloading
        this.completed = completed
    }

    /**
     * Была ли фактическая работа прогрессивного пути: проба, источник, состояние или воспроизведение.
     * Полностью скачанный файл без пробы не считается попыткой, поэтому его запись не пишется.
     */
    fun hasAttempt(): Boolean =
        probeRuns > 0 || sourceCreated || playingMs != null || stateMarks.isNotEmpty() || (hasPartialPath && !completed)

    fun probeStarted() {
        probeRuns++
        probeStartedAt = now()
    }

    fun probeFinished(reason: String, layout: Mp4Layout?) {
        probeStartedAt?.let { probeTotalMs += now() - it }
        probeStartedAt = null
        probeResult = reason
        this.layout = layout
    }

    /** Проба прервана перезапуском (новый прирост префикса отменил её). */
    fun probeCancelled() {
        probeCancelled++
        probeStartedAt?.let { probeTotalMs += now() - it }
        probeStartedAt = null
    }

    fun unlocked() {
        if (unlockedMs == null) unlockedMs = now() - openedAt
    }

    fun rejected(reason: String) {
        rejectedReason = reason
    }

    /** Источник создан плеером (`createDataSource`). Вызывается из потока плеера. */
    fun sourceCreated() {
        sourceCreated = true
    }

    /** Байты, отданные плееру. Вызывается из потока загрузки. */
    fun onRead(count: Int) {
        if (count <= 0) return
        bytesRead.addAndGet(count.toLong())
        firstReadMs.compareAndSet(-1L, now() - openedAt)
    }

    /** Чтение ожидает новых байтов (один раз на вызов чтения). */
    fun onWait() {
        readWaits.incrementAndGet()
    }

    /** Запрос дополнительной загрузки у TDLib. */
    fun onNeedMore() {
        needMoreCalls.incrementAndGet()
    }

    /** Таймаут чтения: [kind] = `no_transfer` или `timeout`. */
    fun onStall(kind: String) {
        if (kind == STALL_NO_TRANSFER) stallNoTransfer.incrementAndGet() else stallTimeout.incrementAndGet()
    }

    /** Состояние Media3 (`Player.STATE_*`), первый раз за попытку. */
    fun onMediaState(state: Int) {
        val name = stateName(state) ?: return
        if (!stateMarks.containsKey(name)) stateMarks[name] = now() - openedAt
    }

    fun onPlaying() {
        if (playingMs == null) playingMs = now() - openedAt
    }

    fun onPlayerError(errorCodeName: String) {
        if (playerError == null) playerError = errorCodeName
    }

    /**
     * Причина, почему видео ещё ждёт полного файла. Пусто, если источник уже разблокирован.
     * Значения: `offset_nonzero`, `moov_at_end`, `prefix_too_small`, `probe_starved`, `codec_unsupported`, `unknown`.
     */
    fun cause(): String {
        if (unlockedMs != null) return "none"
        // Файл полностью на диске: проба не нужна, и это не «неизвестная» причина.
        if (completed) return "complete"
        val types = layout?.boxTypes.orEmpty()
        return when {
            downloadOffset != 0L -> "offset_nonzero"
            probeResult == "MoovAfterMediaData" -> "moov_at_end"
            probeResult == "MissingMoov" && "mdat" in types && "moov" !in types -> "moov_at_end"
            probeResult == "MissingMoov" || probeResult == "TruncatedMoov" -> "prefix_too_small"
            probeResult == null && probeCancelled > 0 -> "probe_starved"
            probeResult == "UnsupportedVideoCodec" -> "codec_unsupported"
            else -> "unknown"
        }
    }

    /**
     * Итог попытки. Возвращает атрибуты один раз; повторный вызов (например, после пересоздания страницы) ничего не даёт.
     */
    fun finish(): Map<String, String>? {
        if (finished) return null
        finished = true
        val attrs = linkedMapOf(
            "attempt" to attempt.toString(),
            "has_partial_path" to hasPartialPath.toString(),
            "prefix_bytes" to prefixBytes.toString(),
            "total_bytes" to totalBytes.toString(),
            "download_offset" to downloadOffset.toString(),
            "downloading" to downloading.toString(),
            "sender_streaming" to senderSupportsStreaming.toString(),
            "probe" to "runs=$probeRuns,cancelled=$probeCancelled,ms=$probeTotalMs",
            "probe_result" to (probeResult ?: "none"),
            "layout" to (layout?.boxTypes?.take(MAX_LAYOUT_TYPES)?.joinToString(",") ?: ""),
            "moov_bytes" to (layout?.moovPayloadBytes?.toString() ?: ""),
            "unlocked" to (unlockedMs != null).toString(),
            "unlock_ms" to (unlockedMs?.toString() ?: ""),
            "rejected" to (rejectedReason ?: ""),
            "cause" to cause(),
            "source_created" to sourceCreated.toString(),
            "first_read_ms" to firstReadMs.get().toString(),
            "bytes_read" to bytesRead.get().toString(),
            "reads" to "waits=${readWaits.get()},need_more=${needMoreCalls.get()}",
            "stalls" to "$STALL_NO_TRANSFER=${stallNoTransfer.get()},timeout=${stallTimeout.get()}",
            "media_states" to stateMarks.entries.joinToString(",") { "${it.key}@${it.value}" },
            "first_ready_ms" to (stateMarks[STATE_READY]?.toString() ?: ""),
            "playing_ms" to (playingMs?.toString() ?: ""),
            "player_error" to (playerError ?: "")
        )
        return attrs
    }

    private fun stateName(state: Int): String? = when (state) {
        1 -> "IDLE"
        2 -> "BUFFERING"
        3 -> STATE_READY
        4 -> "ENDED"
        else -> null
    }

    private companion object {
        const val STATE_READY = "READY"
        const val STALL_NO_TRANSFER = "no_transfer"
        const val MAX_LAYOUT_TYPES = 8
    }
}
