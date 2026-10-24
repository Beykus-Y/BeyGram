package com.beykus.beygram.ui.screens.profile

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)

internal fun formatDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

internal fun formatDate(seconds: Int): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds.coerceAtLeast(0).toLong() * 1000L))

internal fun formatDateTime(seconds: Int): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(seconds.toLong() * 1000L))

/** Compact axis labels: the canvas has no room for grouped thousands on a phone. */
internal fun formatCompactCount(value: Long): String {
    val magnitude = abs(value)
    return when {
        magnitude < 1_000L -> value.toString()
        magnitude < 999_500L -> compact(value, 1_000.0, "K")
        magnitude < 999_500_000L -> compact(value, 1_000_000.0, "M")
        else -> compact(value, 1_000_000_000.0, "B")
    }
}

internal fun formatChartDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))

private fun compact(value: Long, divisor: Double, suffix: String): String {
    val scaled = value / divisor
    val number = if (abs(scaled) >= 100.0) {
        String.format(Locale.ROOT, "%.0f", scaled)
    } else {
        String.format(Locale.ROOT, "%.1f", scaled).trimEnd('0').trimEnd('.')
    }
    return number + suffix
}
