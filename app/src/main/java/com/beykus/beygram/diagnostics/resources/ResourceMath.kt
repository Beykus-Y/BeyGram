package com.beykus.beygram.diagnostics.resources

/**
 * Один снимок счётчиков процесса. `null` означает, что Android не отдал значение:
 * такой показатель показывается как «недоступно», а не как 0.
 */
data class ResourceCounters(
    val timestampMillis: Long,
    val processCpuMillis: Long?,
    val rxBytes: Long?,
    val txBytes: Long?,
)

/** Скорости, посчитанные между двумя соседними снимками. */
data class ResourceRates(
    /** Доля от всей мощности процессора (все ядра = 100%). */
    val cpuPercent: Double?,
    val rxBytesPerSecond: Double?,
    val txBytesPerSecond: Double?,
)

/** Чистые формулы без Android API, чтобы их можно было проверить юнит-тестами. */
object ResourceMath {

    fun rates(previous: ResourceCounters, current: ResourceCounters, cpuCores: Int): ResourceRates {
        val wallMillis = current.timestampMillis - previous.timestampMillis
        if (wallMillis <= 0) return ResourceRates(null, null, null)
        return ResourceRates(
            cpuPercent = cpuPercent(previous.processCpuMillis, current.processCpuMillis, wallMillis, cpuCores),
            rxBytesPerSecond = bytesPerSecond(previous.rxBytes, current.rxBytes, wallMillis),
            txBytesPerSecond = bytesPerSecond(previous.txBytes, current.txBytes, wallMillis),
        )
    }

    /**
     * Процент от всей мощности CPU за интервал: CPU-время процесса делится на
     * (длительность интервала × число ядер). Отрицательная разница означает сброс счётчика.
     */
    fun cpuPercent(previousMillis: Long?, currentMillis: Long?, wallMillis: Long, cpuCores: Int): Double? {
        if (previousMillis == null || currentMillis == null || wallMillis <= 0 || cpuCores <= 0) return null
        val cpuDelta = currentMillis - previousMillis
        if (cpuDelta < 0) return null
        return cpuDelta.toDouble() * 100.0 / (wallMillis.toDouble() * cpuCores)
    }

    /**
     * Байты в секунду. `TrafficStats` возвращает -1, если счётчик не поддерживается,
     * а после перезагрузки или сброса значение может стать меньше предыдущего.
     */
    fun bytesPerSecond(previousBytes: Long?, currentBytes: Long?, wallMillis: Long): Double? {
        if (previousBytes == null || currentBytes == null || wallMillis <= 0) return null
        if (previousBytes < 0 || currentBytes < 0) return null
        val delta = currentBytes - previousBytes
        if (delta < 0) return null
        return delta * 1000.0 / wallMillis
    }
}

/** Ограниченный буфер последних значений: старые записи вытесняются, память не растёт. */
class BoundedBuffer<T>(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val items = ArrayDeque<T>(capacity)

    fun add(item: T) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(item)
    }

    fun snapshot(): List<T> = items.toList()

    fun clear() = items.clear()
}
