package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChannelChartId
import com.beykus.beygram.domain.model.ChannelChartKind
import com.beykus.beygram.domain.model.ChannelChartPoint
import com.beykus.beygram.domain.model.ChannelChartSeries
import com.beykus.beygram.domain.model.ChannelStatisticsChart
import com.beykus.beygram.domain.model.ChannelTopPost
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject

/**
 * Upper bounds keep a long period cheap to draw. TDLib may return a point per day per series and
 * up to 50 language series; the canvas only needs a representative sample at phone width.
 */
internal const val MAX_CHART_POINTS = 180
internal const val MAX_CHART_SERIES = 6

private data class RawSeries(val label: String, val values: List<Long>, val name: String)

/**
 * Parses the JSON inside `statisticalGraphData` using the documented Telegram graph schema:
 * `columns` holds `["x", ...]` and `["y0", ...]` arrays, `names`/`types` map labels, and `hidden`
 * lists columns the server does not want shown. Unknown or malformed input returns null instead of
 * failing the whole statistics screen.
 */
internal fun parseChannelChart(
    id: ChannelChartId,
    json: String,
    fallbackKind: ChannelChartKind
): ChannelStatisticsChart? {
    if (json.isBlank()) return null
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val columns = root.optJSONArray("columns") ?: return null
    val hidden = root.optJSONArray("hidden").toStringSet()
    val names = root.stringMap("names")
    val types = root.stringMap("types")

    var xValues: List<Long>? = null
    val raw = mutableListOf<RawSeries>()
    for (index in 0 until columns.length()) {
        val column = columns.optJSONArray(index) ?: continue
        if (column.length() < 2) continue
        val label = column.optString(0)
        if (label == "x") {
            xValues = (1 until column.length()).map { valueIndex ->
                column.finiteDouble(valueIndex)?.roundToLong() ?: 0L
            }
        } else {
            if (label in hidden) continue
            val values = (1 until column.length()).map { valueIndex ->
                column.finiteDouble(valueIndex)?.roundToLong() ?: 0L
            }
            raw += RawSeries(label, values, names[label].orEmpty().ifBlank { label })
        }
    }

    val x = xValues ?: return null
    if (x.isEmpty() || raw.isEmpty()) return null
    val length = minOf(x.size, raw.minOf { it.values.size })
    if (length == 0) return null
    val pointsX = x.take(length)
    val kind = chartKind(types.values, fallbackKind)
    val stacked = root.optBoolean("stacked", false) || kind == ChannelChartKind.Bar
    val percentage = root.optBoolean("percentage", false)
    val yScaled = root.optBoolean("y_scaled", false)

    val selected = selectSeries(raw, length)
    if (selected.isEmpty()) return null
    val chart = ChannelStatisticsChart(
        id = id,
        kind = kind,
        series = selected.map { series ->
            ChannelChartSeries(
                name = series.name,
                points = series.values.take(length).mapIndexed { pointIndex, value ->
                    ChannelChartPoint(pointsX[pointIndex], value)
                }.sortedBy { it.x },
                isOther = series.label.isEmpty()
            )
        },
        stacked = stacked,
        percentage = percentage,
        yScaled = yScaled
    )
    return normalizeChannelChart(chart)
}

/**
 * Keeps the chart readable when Telegram returns dozens of columns. The largest series are kept
 * and the remaining values are summed into one "other" series, so totals do not silently disappear.
 */
private fun selectSeries(
    raw: List<RawSeries>,
    length: Int
): List<RawSeries> {
    if (raw.size <= MAX_CHART_SERIES) return raw
    val ranked = raw.sortedByDescending { series ->
        series.values.take(length).sumOf { abs(it).toDouble() }
    }
    val kept = ranked.take(MAX_CHART_SERIES - 1)
    val rest = ranked.drop(MAX_CHART_SERIES - 1)
    if (rest.isEmpty()) return kept
    val otherValues = List(length) { index ->
        rest.sumOf { it.values.getOrElse(index) { 0L } }
    }
    // Empty label marks the synthetic series; the UI replaces it with a localized "Other".
    return kept + RawSeries("", otherValues, "")
}

private fun chartKind(types: Collection<String>, fallback: ChannelChartKind): ChannelChartKind {
    val normalized = types.map { it.lowercase() }.toSet()
    return when {
        "bar" in normalized -> ChannelChartKind.Bar
        "area" in normalized -> ChannelChartKind.Area
        "step" in normalized -> ChannelChartKind.Step
        "line" in normalized -> ChannelChartKind.Line
        else -> fallback
    }
}

/**
 * Reduces a long chart to at most [maxPoints] points. Line-like charts are evenly sampled including
 * both ends; bars are summed into buckets, because dropping bars would change the totals shown.
 */
internal fun normalizeChannelChart(
    chart: ChannelStatisticsChart,
    maxPoints: Int = MAX_CHART_POINTS
): ChannelStatisticsChart {
    val x = chart.series.flatMap { series -> series.points.map { it.x } }.distinct().sorted()
    if (x.size <= maxPoints || maxPoints <= 0 || chart.series.isEmpty()) return chart
    return if (chart.kind == ChannelChartKind.Bar) {
        aggregateBars(chart, x, maxPoints)
    } else {
        sampleLines(chart, x, maxPoints)
    }
}

private fun sampleLines(
    chart: ChannelStatisticsChart,
    x: List<Long>,
    maxPoints: Int
): ChannelStatisticsChart {
    if (maxPoints == 1 || x.size <= maxPoints) return chart
    val sampledX = (0 until maxPoints)
        .map { index -> x[(index.toLong() * (x.size - 1) / (maxPoints - 1)).toInt()] }
        .distinct()
    val sampledSet = sampledX.toHashSet()
    return chart.copy(
        series = chart.series.map { series ->
            series.copy(points = series.points.filter { it.x in sampledSet })
        }.filter { it.points.isNotEmpty() }
    )
}

private fun aggregateBars(
    chart: ChannelStatisticsChart,
    x: List<Long>,
    maxPoints: Int
): ChannelStatisticsChart {
    val bucketSize = ceil(x.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val buckets = x.chunked(bucketSize)
    return chart.copy(
        series = chart.series.map { series ->
            val byX = series.points.associate { it.x to it.value }
            series.copy(
                points = buckets.map { bucket ->
                    ChannelChartPoint(bucket.first(), bucket.sumOf { byX[it] ?: 0L })
                }
            )
        }
    )
}

/** Orders top posts by the metrics TDLib actually returns, with a stable id tie-breaker. */
internal fun rankTopPosts(posts: List<ChannelTopPost>, limit: Int = 5): List<ChannelTopPost> =
    posts.sortedWith(
        compareByDescending<ChannelTopPost> { it.viewCount }
            .thenByDescending { it.forwardCount }
            .thenByDescending { it.reactionCount }
            .thenByDescending { it.messageId }
    ).take(limit.coerceAtLeast(0))

private fun JSONArray?.toStringSet(): Set<String> {
    val array = this ?: return emptySet()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index).takeIf { it.isNotBlank() }
    }.toSet()
}

private fun JSONArray.finiteDouble(index: Int): Double? =
    optDouble(index, Double.NaN).takeIf { it.isFinite() }

private fun JSONObject.stringMap(key: String): Map<String, String> {
    val objectValue = optJSONObject(key) ?: return emptyMap()
    val result = mutableMapOf<String, String>()
    val keys = objectValue.keys()
    while (keys.hasNext()) {
        val name = keys.next()
        val value = objectValue.opt(name)
        if (value is String) result[name] = value
    }
    return result
}
