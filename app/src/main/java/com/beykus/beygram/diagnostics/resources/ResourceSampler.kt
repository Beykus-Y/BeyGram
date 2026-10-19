package com.beykus.beygram.diagnostics.resources

import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.os.SystemClock

/** Интервал опроса на экране «Ресурсы». Опрос идёт только пока экран открыт. */
const val RESOURCE_SAMPLE_INTERVAL_MILLIS = 2_000L

/** Сколько снимков хранится: 150 × 2 секунды = 5 минут истории. */
const val RESOURCE_HISTORY_CAPACITY = 150

/** Один опрос: счётчики для расчёта скоростей и текущая память. */
data class ResourceSample(
    val counters: ResourceCounters,
    /** Скорости относительно предыдущего опроса; null у первого снимка. */
    val rates: ResourceRates?,
    val javaHeapUsedBytes: Long,
    val javaHeapMaxBytes: Long,
    val nativeHeapBytes: Long,
)

/**
 * Читает показатели самого процесса BeyGram. Значения, которые Android не отдал,
 * остаются null. Так экран покажет «недоступно», а не ноль.
 */
class ResourceSampler(
    private val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
) {
    private var previous: ResourceCounters? = null

    fun sample(): ResourceSample {
        val counters = readCounters()
        val runtime = Runtime.getRuntime()
        val rates = previous?.let { ResourceMath.rates(it, counters, cpuCores) }
        previous = counters
        return ResourceSample(
            counters = counters,
            rates = rates,
            javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            javaHeapMaxBytes = runtime.maxMemory(),
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
        )
    }

    private fun readCounters(): ResourceCounters {
        // Монотонные часы: сдвиг системного времени не должен искажать скорости.
        val uid = Process.myUid()
        return ResourceCounters(
            timestampMillis = SystemClock.elapsedRealtime(),
            processCpuMillis = Process.getElapsedCpuTime(),
            // TrafficStats возвращает -1, если счётчик не поддерживается на устройстве.
            rxBytes = TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 },
            txBytes = TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 },
        )
    }
}
