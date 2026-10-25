package com.beykus.beygram.data.media

/** Binary size units used by the traffic indicator. Labels are resolved by the UI layer. */
internal enum class TrafficSizeUnit { Bytes, Kilobytes, Megabytes, Gigabytes }

/** A size split into a display value and its unit, kept separate so formatting stays testable. */
internal data class TrafficSizeValue(val amount: Double, val unit: TrafficSizeUnit)

private const val KIB = 1024L
private const val MIB = KIB * 1024L
private const val GIB = MIB * 1024L

/** Negative values never produce a negative size; they are treated as zero. */
internal fun trafficSizeValue(bytes: Long): TrafficSizeValue {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= GIB -> TrafficSizeValue(safe / GIB.toDouble(), TrafficSizeUnit.Gigabytes)
        safe >= MIB -> TrafficSizeValue(safe / MIB.toDouble(), TrafficSizeUnit.Megabytes)
        safe >= KIB -> TrafficSizeValue(safe / KIB.toDouble(), TrafficSizeUnit.Kilobytes)
        else -> TrafficSizeValue(safe.toDouble(), TrafficSizeUnit.Bytes)
    }
}
