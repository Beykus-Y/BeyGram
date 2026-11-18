package com.beykus.beygram.ui.screens.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.domain.model.ChannelChartId
import com.beykus.beygram.domain.model.ChannelChartKind
import com.beykus.beygram.domain.model.ChannelChartPoint
import com.beykus.beygram.domain.model.ChannelChartSeries
import com.beykus.beygram.domain.model.ChannelStatisticsChart
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import kotlin.math.max
import kotlin.math.min

/** Height of the plotting area; the value and date labels sit around it. */
private val PlotHeight: Dp = 150.dp

@Composable
internal fun StatisticsChartsSection(
    charts: List<ChannelStatisticsChart>,
    palette: BeyGramPalette,
    modifier: Modifier = Modifier
) {
    // Signature draws no card per chart, so the charts are separated by a hairline instead.
    val ink = LocalDesignSystem.current.style == DesignStyle.Signature
    Column(modifier.fillMaxWidth().padding(top = 18.dp)) {
        StatisticsSectionHeader(stringResource(R.string.channel_statistics_charts_title))
        if (charts.isEmpty()) {
            Text(
                stringResource(R.string.channel_statistics_charts_empty),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            charts.forEachIndexed { index, chart ->
                if (ink && index > 0) HorizontalDivider(color = palette.divider)
                StatisticsChartCard(chart, palette, Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun StatisticsChartCard(
    chart: ChannelStatisticsChart,
    palette: BeyGramPalette,
    modifier: Modifier = Modifier
) {
    val inset = statisticsInset()
    val title = stringResource(chartTitle(chart.id))
    val series = remember(chart) { chart.series.filter { it.points.isNotEmpty() } }
    Column(modifier.fillMaxWidth().statisticsGroupSurface().padding(horizontal = inset, vertical = 14.dp)) {
        Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (hasTrend(chart.kind, series)) {
            ChartPlot(title, chart, series, palette)
        } else {
            // One point cannot show a direction, so the chart says so instead of drawing a dot.
            Text(
                stringResource(R.string.channel_statistics_chart_no_data),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** A line or area needs two points to show a direction; a bar chart can show a single bar. */
private fun hasTrend(kind: ChannelChartKind, series: List<ChannelChartSeries>): Boolean =
    series.isNotEmpty() && (kind == ChannelChartKind.Bar || series.any { it.points.size >= 2 })

@Composable
private fun ChartPlot(
    title: String,
    chart: ChannelStatisticsChart,
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette
) {
    val range = remember(chart) { chartRange(chart, series) }
    val percentSuffix = if (chart.percentage) "%" else ""
    val maxLabel = formatCompactCount(range.second) + percentSuffix
    val minLabel = formatCompactCount(range.first) + percentSuffix
    val middleLabel = formatCompactCount(range.first + (range.second - range.first) / 2) + percentSuffix
    // Lines with their own scales have no single value axis; the caption says so.
    val valueAxis = !(chart.yScaled && series.size > 1)
    val rangeLabel = if (!valueAxis) {
        stringResource(R.string.channel_statistics_chart_scaled)
    } else if (range.first == range.second) {
        stringResource(R.string.channel_statistics_chart_max, maxLabel)
    } else {
        stringResource(R.string.channel_statistics_chart_range, maxLabel, minLabel)
    }
    val description = stringResource(
        R.string.channel_statistics_chart_description,
        title,
        series.size,
        maxLabel
    )
    val axis = remember(chart) { chartAxis(chart) }
    val axisStart = axis.first
    val axisEnd = axis.second

    Text(
        rangeLabel,
        color = palette.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 2.dp)
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (valueAxis) {
            // Three labels at the top, middle and bottom grid lines of the plot.
            Column(
                Modifier.height(PlotHeight).padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(maxLabel, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1)
                Text(middleLabel, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1)
                Text(minLabel, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier.fillMaxWidth().height(PlotHeight)
                    .semantics { contentDescription = description }
            ) {
                drawStatisticsChart(chart, series, palette, range.first, range.second)
            }
            if (axisStart != null && axisEnd != null) {
                Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                    Text(axisStart, color = palette.textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(axisEnd, color = palette.textSecondary, fontSize = 11.sp)
                }
            }
        }
    }
    if (series.size > 1) {
        ChartLegend(series, chart.kind, palette, Modifier.padding(top = 10.dp))
    }
}

/**
 * Names every series. Each entry carries the same mark as its line or bar, so the legend identifies a series by its
 * dash pattern as well as its colour. Names wrap instead of being cut off.
 */
@Composable
private fun ChartLegend(
    series: List<ChannelChartSeries>,
    kind: ChannelChartKind,
    palette: BeyGramPalette,
    modifier: Modifier = Modifier
) {
    val asLine = kind == ChannelChartKind.Line || kind == ChannelChartKind.Step
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        series.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { columnIndex, item ->
                    Row(
                        Modifier.weight(1f).padding(end = if (columnIndex == 0) 8.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SeriesSwatch(rowIndex * 2 + columnIndex, asLine, palette)
                        Text(
                            seriesName(item),
                            color = palette.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SeriesSwatch(index: Int, asLine: Boolean, palette: BeyGramPalette) {
    val color = seriesColor(palette, index)
    if (asLine) {
        Canvas(Modifier.width(18.dp).height(10.dp)) {
            val middle = size.height / 2f
            drawLine(
                color = color,
                start = Offset(0f, middle),
                end = Offset(size.width, middle),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = seriesDashEffect(index)
            )
        }
    } else {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun seriesName(series: ChannelChartSeries): String =
    if (series.isOther) stringResource(R.string.channel_statistics_chart_other) else series.name

@StringRes
private fun chartTitle(id: ChannelChartId): Int = when (id) {
    ChannelChartId.Members -> R.string.channel_statistics_chart_members
    ChannelChartId.Joins -> R.string.channel_statistics_chart_joins
    ChannelChartId.Mutes -> R.string.channel_statistics_chart_mutes
    ChannelChartId.ViewsByHour -> R.string.channel_statistics_chart_views_by_hour
    ChannelChartId.ViewsBySource -> R.string.channel_statistics_chart_views_by_source
    ChannelChartId.JoinsBySource -> R.string.channel_statistics_chart_joins_by_source
    ChannelChartId.Languages -> R.string.channel_statistics_chart_languages
    ChannelChartId.MessageInteractions -> R.string.channel_statistics_chart_interactions
    ChannelChartId.MessageReactions -> R.string.channel_statistics_chart_reactions
    ChannelChartId.MessageContent -> R.string.channel_statistics_chart_content
    ChannelChartId.Actions -> R.string.channel_statistics_chart_actions
    ChannelChartId.ViewsByWeekday -> R.string.channel_statistics_chart_weekday
    ChannelChartId.GroupMembers -> R.string.channel_statistics_chart_group_members
    ChannelChartId.GroupJoins -> R.string.channel_statistics_chart_group_joins
    ChannelChartId.MessagesByHour -> R.string.channel_statistics_chart_messages_by_hour
    ChannelChartId.MessagesByWeekday -> R.string.channel_statistics_chart_messages_by_weekday
}

/** Uses the same range for the caption and the canvas, including stacked totals. */
private fun chartRange(
    chart: ChannelStatisticsChart,
    series: List<ChannelChartSeries>
): Pair<Long, Long> {
    val values = if ((chart.kind == ChannelChartKind.Bar || chart.kind == ChannelChartKind.Area) && chart.stacked) {
        val count = series.maxOf { it.points.size }
        (0 until count).map { index -> series.sumOf { it.points.getOrNull(index)?.value ?: 0L } }
    } else {
        series.flatMap { item -> item.points.map { it.value } }
    }
    if (values.isEmpty()) return 0L to 1L
    var minValue = values.minOrNull() ?: 0L
    var maxValue = values.maxOrNull() ?: 0L
    if (chart.percentage) {
        minValue = 0L
        maxValue = 100L
    } else if (chart.kind == ChannelChartKind.Bar || chart.kind == ChannelChartKind.Area) {
        minValue = min(0L, minValue)
        maxValue = max(0L, maxValue)
    }
    if (minValue == maxValue) {
        when {
            minValue == 0L -> maxValue = 1L
            minValue > 0L -> minValue = 0L
            else -> maxValue = 0L
        }
    }
    return minValue to maxValue
}

private data class ChartAxis(val first: String?, val second: String?)

private fun chartAxis(chart: ChannelStatisticsChart): ChartAxis {
    val points = chart.series.firstOrNull()?.points.orEmpty()
    if (points.size < 2) return ChartAxis(null, null)
    val first = points.first().x
    val last = points.last().x
    if (first < MIN_CHART_TIMESTAMP || last < MIN_CHART_TIMESTAMP || first == last) {
        return ChartAxis(null, null)
    }
    return ChartAxis(formatChartDate(first), formatChartDate(last))
}

private const val MIN_CHART_TIMESTAMP = 1_000_000_000_000L

private fun DrawScope.drawStatisticsChart(
    chart: ChannelStatisticsChart,
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette,
    minValue: Long,
    maxValue: Long
) {
    val allPoints = series.flatMap { it.points }
    if (allPoints.isEmpty()) return
    val minX = allPoints.minOf { it.x }
    val maxX = allPoints.maxOf { it.x }
    val left = 2.dp.toPx()
    val right = size.width - 2.dp.toPx()
    val top = 4.dp.toPx()
    val bottom = size.height - 4.dp.toPx()
    val width = (right - left).coerceAtLeast(1f)
    val height = (bottom - top).coerceAtLeast(1f)
    val valueSpan = (maxValue - minValue).coerceAtLeast(1L).toFloat()

    fun x(value: Long): Float = if (maxX == minX) {
        left + width / 2f
    } else {
        left + (value - minX).toFloat() / (maxX - minX).toFloat() * width
    }

    fun y(value: Long): Float = bottom - (value - minValue).toFloat() / valueSpan * height

    repeat(3) { index ->
        val gridY = top + height * index / 2f
        drawLine(
            palette.divider.copy(alpha = 0.55f),
            Offset(left, gridY),
            Offset(right, gridY),
            strokeWidth = 1.dp.toPx()
        )
    }

    when (chart.kind) {
        ChannelChartKind.Line, ChannelChartKind.Step -> {
            if (chart.yScaled && series.size > 1) {
                drawScaledLines(chart, series, palette, ::x, top, bottom)
            } else {
                drawLines(chart, series, palette, ::x, ::y)
            }
        }
        ChannelChartKind.Area -> drawAreas(chart, series, palette, ::x, ::y)
        ChannelChartKind.Bar -> drawBars(series, palette, ::y)
    }
}

/** TDLib's y_scaled graphs compare series shapes, not absolute values, so each line fills the height. */
private fun DrawScope.drawScaledLines(
    chart: ChannelStatisticsChart,
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette,
    x: (Long) -> Float,
    top: Float,
    bottom: Float
) {
    val height = (bottom - top).coerceAtLeast(1f)
    series.forEachIndexed { index, item ->
        val points = item.points
        if (points.isEmpty()) return@forEachIndexed
        val color = seriesColor(palette, index)
        val minValue = points.minOf { it.value }
        val maxValue = points.maxOf { it.value }
        val span = (maxValue - minValue).coerceAtLeast(1L).toFloat()
        fun scaledY(value: Long): Float = bottom - (value - minValue).toFloat() / span * height
        if (points.size == 1) {
            drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(x(points[0].x), scaledY(points[0].value)))
            return@forEachIndexed
        }
        val path = Path()
        path.moveTo(x(points.first().x), scaledY(points.first().value))
        for (pointIndex in 1 until points.size) {
            val previous = points[pointIndex - 1]
            val current = points[pointIndex]
            val px = x(current.x)
            if (chart.kind == ChannelChartKind.Step) {
                path.lineTo(px, scaledY(previous.value))
            }
            path.lineTo(px, scaledY(current.value))
        }
        drawPath(path, color, style = seriesStroke(index))
    }
}

private fun DrawScope.drawLines(
    chart: ChannelStatisticsChart,
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette,
    x: (Long) -> Float,
    y: (Long) -> Float
) {
    series.forEachIndexed { index, item ->
        val points = item.points
        val color = seriesColor(palette, index)
        if (points.size == 1) {
            drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(x(points[0].x), y(points[0].value)))
            return@forEachIndexed
        }
        val path = Path()
        path.moveTo(x(points.first().x), y(points.first().value))
        for (pointIndex in 1 until points.size) {
            val previous = points[pointIndex - 1]
            val current = points[pointIndex]
            val px = x(current.x)
            if (chart.kind == ChannelChartKind.Step) {
                path.lineTo(px, y(previous.value))
            }
            path.lineTo(px, y(current.value))
        }
        drawPath(path, color, style = seriesStroke(index))
    }
}

private fun DrawScope.drawAreas(
    chart: ChannelStatisticsChart,
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette,
    x: (Long) -> Float,
    y: (Long) -> Float
) {
    if (series.isEmpty()) return
    val base = series.first().points
    if (base.isEmpty()) return
    val count = base.size
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (chart.stacked) {
        val cumulative = LongArray(count)
        series.forEachIndexed { index, item ->
            val color = seriesColor(palette, index)
            val upper = LongArray(count) { pointIndex ->
                cumulative[pointIndex] + (item.points.getOrNull(pointIndex)?.value ?: 0L)
            }
            val area = Path()
            for (pointIndex in 0 until count) {
                val px = x(base[pointIndex].x)
                val py = y(upper[pointIndex])
                if (pointIndex == 0) area.moveTo(px, py) else area.lineTo(px, py)
            }
            for (pointIndex in count - 1 downTo 0) {
                area.lineTo(x(base[pointIndex].x), y(cumulative[pointIndex]))
            }
            area.close()
            drawPath(area, color.copy(alpha = 0.35f))
            drawPath(upperLine(base, upper, x, y), color, style = stroke)
            upper.copyInto(cumulative)
        }
    } else {
        val baseline = y(0L)
        series.forEachIndexed { index, item ->
            val color = seriesColor(palette, index)
            val area = Path()
            area.moveTo(x(item.points.first().x), baseline)
            item.points.forEach { point -> area.lineTo(x(point.x), y(point.value)) }
            area.lineTo(x(item.points.last().x), baseline)
            area.close()
            drawPath(area, color.copy(alpha = 0.18f))
            val line = Path()
            item.points.forEachIndexed { pointIndex, point ->
                if (pointIndex == 0) line.moveTo(x(point.x), y(point.value))
                else line.lineTo(x(point.x), y(point.value))
            }
            drawPath(line, color, style = stroke)
        }
    }
}

private fun upperLine(
    base: List<ChannelChartPoint>,
    values: LongArray,
    x: (Long) -> Float,
    y: (Long) -> Float
): Path {
    val path = Path()
    values.forEachIndexed { index, value ->
        val px = x(base[index].x)
        val py = y(value)
        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    return path
}

private fun DrawScope.drawBars(
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette,
    y: (Long) -> Float
) {
    val count = series.firstOrNull()?.points?.size ?: return
    if (count == 0) return
    val left = 2.dp.toPx()
    val width = size.width - 4.dp.toPx()
    val groupWidth = width / count
    val barWidth = (groupWidth * 0.72f).coerceAtLeast(1f)
    val positive = LongArray(count)
    val negative = LongArray(count)
    series.forEachIndexed { seriesIndex, item ->
        val color = seriesColor(palette, seriesIndex)
        for (index in 0 until count) {
            val value = item.points.getOrNull(index)?.value ?: 0L
            if (value == 0L) continue
            val top: Long
            val bottom: Long
            if (value > 0L) {
                bottom = positive[index]
                top = bottom + value
                positive[index] = top
            } else {
                top = negative[index]
                bottom = top + value
                negative[index] = bottom
            }
            val topY = y(max(top, bottom))
            val bottomY = y(min(top, bottom))
            val barHeight = (bottomY - topY).coerceAtLeast(1f)
            drawRect(
                color = color,
                topLeft = Offset(left + groupWidth * index + (groupWidth - barWidth) / 2f, topY),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

/** Solid line for the first series; the other series add a dash pattern so they stay apart without colour. */
private val seriesDashPatterns: List<FloatArray?> = listOf(
    null,
    floatArrayOf(8f, 5f),
    floatArrayOf(1f, 4f),
    floatArrayOf(10f, 4f, 2f, 4f),
    floatArrayOf(16f, 6f),
    floatArrayOf(4f, 3f)
)

private fun DrawScope.seriesDashEffect(index: Int): PathEffect? {
    val pattern = seriesDashPatterns[index % seriesDashPatterns.size] ?: return null
    return PathEffect.dashPathEffect(FloatArray(pattern.size) { pattern[it].dp.toPx() })
}

private fun DrawScope.seriesStroke(index: Int): Stroke = Stroke(
    width = 2.dp.toPx(),
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
    pathEffect = seriesDashEffect(index)
)

private fun seriesColor(palette: BeyGramPalette, index: Int): Color = when (index % 6) {
    0 -> palette.accent
    1 -> palette.successText
    2 -> palette.warningText
    3 -> palette.danger
    4 -> palette.textSecondary
    else -> palette.accentInk
}
