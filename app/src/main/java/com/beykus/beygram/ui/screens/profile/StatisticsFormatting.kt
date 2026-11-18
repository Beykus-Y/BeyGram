package com.beykus.beygram.ui.screens.profile

import java.text.DateFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)

internal fun formatDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

/**
 * A percentage change with an explicit sign, such as "+1.2%" or "-0.4%". A value that is not a number gives null, so the
 * screen shows no change instead of an invented one.
 */
internal fun formatSignedPercent(percent: Double): String? {
    if (!percent.isFinite()) return null
    val magnitude = formatDecimal(abs(percent))
    return when (growthTrend(percent)) {
        1 -> "+$magnitude%"
        -1 -> "-$magnitude%"
        else -> "$magnitude%"
    }
}

/** Direction of a change: 1 up, -1 down, 0 when it is missing or rounds to 0.0 at one decimal. */
internal fun growthTrend(percent: Double): Int = when {
    !percent.isFinite() || abs(percent) < GrowthRoundingFloor -> 0
    percent > 0.0 -> 1
    else -> -1
}

/** Half of the last displayed decimal: anything smaller shows as 0.0 and must not get a sign. */
private const val GrowthRoundingFloor = 0.05

internal fun formatDate(seconds: Int): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds.coerceAtLeast(0).toLong() * 1000L))

internal fun formatDateTime(seconds: Int): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(seconds.toLong() * 1000L))

/** Heading of one day in the admin log. Entries with the same heading belong to the same day group. */
internal fun formatDayTitle(seconds: Int): String =
    DateFormat.getDateInstance(DateFormat.LONG).format(Date(seconds.coerceAtLeast(0).toLong() * 1000L))

/** Clock time of an admin-log entry; the day is shown by the group heading. */
internal fun formatTime(seconds: Int): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(seconds.coerceAtLeast(0).toLong() * 1000L))

/** Rows of the summary card; supergroups have totals, channels have per-post means. */
internal enum class StatisticsMetric {
    Members,
    Growth,
    MeanViews,
    MeanShares,
    MeanReactions,
    Notifications,
    Messages,
    Viewers,
    Senders
}

internal fun statisticsMetrics(isChannel: Boolean): List<StatisticsMetric> = if (isChannel) {
    listOf(
        StatisticsMetric.Members,
        StatisticsMetric.Growth,
        StatisticsMetric.MeanViews,
        StatisticsMetric.MeanShares,
        StatisticsMetric.MeanReactions,
        StatisticsMetric.Notifications
    )
} else {
    listOf(
        StatisticsMetric.Members,
        StatisticsMetric.Growth,
        StatisticsMetric.Messages,
        StatisticsMetric.Viewers,
        StatisticsMetric.Senders
    )
}

/** The figures shown as cards at the top of the screen: the audience and the main reach or activity totals. */
internal fun statisticsHeadlineMetrics(isChannel: Boolean): List<StatisticsMetric> = if (isChannel) {
    listOf(
        StatisticsMetric.Members,
        StatisticsMetric.MeanViews,
        StatisticsMetric.MeanReactions,
        StatisticsMetric.MeanShares
    )
} else {
    listOf(
        StatisticsMetric.Members,
        StatisticsMetric.Messages,
        StatisticsMetric.Viewers,
        StatisticsMetric.Senders
    )
}

/** The rest of the figures, listed compactly. Growth is not repeated here because the subscriber card carries it. */
internal fun statisticsDetailMetrics(isChannel: Boolean): List<StatisticsMetric> {
    val headline = statisticsHeadlineMetrics(isChannel).toSet()
    return statisticsMetrics(isChannel).filter { it !in headline && it != StatisticsMetric.Growth }
}

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

/** Short count with at most one decimal, written with the decimal separator of the user's locale ("1,5K" or "1.5K"). */
private fun compact(value: Long, divisor: Double, suffix: String): String {
    val locale = Locale.getDefault()
    val scaled = value / divisor
    val number = if (abs(scaled) >= 100.0) {
        String.format(locale, "%.0f", scaled)
    } else {
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        String.format(locale, "%.1f", scaled).trimEnd('0').trimEnd(separator)
    }
    return number + suffix
}
