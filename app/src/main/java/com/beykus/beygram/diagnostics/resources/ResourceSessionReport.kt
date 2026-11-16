package com.beykus.beygram.diagnostics.resources

import java.util.Locale

/** Сколько снимков можно записать в одну сессию: 300 × 2 секунды = 10 минут. */
const val RESOURCE_SESSION_CAPACITY = 300

/**
 * Копит снимки одной записи. Запись конечна: при заполнении буфера её нужно остановить.
 */
class ResourceSessionRecorder(private val capacity: Int = RESOURCE_SESSION_CAPACITY) {
    private val samples = mutableListOf<ResourceSample>()

    val size: Int get() = samples.size

    val isFull: Boolean get() = samples.size >= capacity

    fun start() {
        samples.clear()
    }

    fun add(sample: ResourceSample) {
        if (!isFull) samples.add(sample)
    }

    /** Возвращает записанное и очищает буфер. */
    fun stop(): List<ResourceSample> {
        val recorded = samples.toList()
        samples.clear()
        return recorded
    }
}

/**
 * Текстовый отчёт для «Поделиться». Содержит только числа: без адресов, токенов,
 * имён процессов и содержимого сообщений. Пользователь видит текст до отправки.
 */
fun formatResourceReport(samples: List<ResourceSample>, cpuCores: Int): String {
    if (samples.isEmpty()) return "samples=0"
    val cpu = samples.mapNotNull { it.rates?.cpuPercent }
    val first = samples.first().counters
    val last = samples.last().counters
    val durationSeconds = (last.timestampMillis - first.timestampMillis) / 1000
    return buildString {
        appendLine("BeyGram resource session")
        appendLine("samples=${samples.size}")
        appendLine("duration_seconds=$durationSeconds")
        appendLine("cpu_cores=$cpuCores")
        appendLine("cpu_percent_avg=${cpu.averageOrNull()?.let(::percent) ?: "unavailable"}")
        appendLine("cpu_percent_max=${cpu.maxOrNull()?.let(::percent) ?: "unavailable"}")
        appendLine("java_heap_max_bytes=${samples.maxOf { it.javaHeapUsedBytes }}")
        appendLine("native_heap_max_bytes=${samples.maxOf { it.nativeHeapBytes }}")
        appendLine("bgmix_prepared_pcm_max_bytes=${samples.maxOf { it.bgMixMemory.preparedPcmBytes }}")
        appendLine("bgmix_retired_pcm_max_bytes=${samples.maxOf { it.bgMixMemory.retiredPcmBytes }}")
        appendLine("bgmix_prepared_tracks_max=${samples.maxOf { it.bgMixMemory.preparedTracks }}")
        appendLine("bgmix_preparing_tracks_max=${samples.maxOf { it.bgMixMemory.preparingTracks }}")
        appendLine("bgmix_live_hosts_max=${samples.maxOf { it.bgMixMemory.liveHosts }}")
        appendLine("bgmix_stopping_hosts_max=${samples.maxOf { it.bgMixMemory.stoppingHosts }}")
        appendLine("bgmix_live_hosts_last=${samples.last().bgMixMemory.liveHosts}")
        appendLine("rx_bytes_delta=${delta(first.rxBytes, last.rxBytes) ?: "unavailable"}")
        appendLine("tx_bytes_delta=${delta(first.txBytes, last.txBytes) ?: "unavailable"}")
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun percent(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

private fun delta(from: Long?, to: Long?): Long? =
    if (from == null || to == null || to < from) null else to - from
