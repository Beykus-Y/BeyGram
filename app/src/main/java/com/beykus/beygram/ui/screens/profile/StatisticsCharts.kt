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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChannelChartId
import com.beykus.beygram.domain.model.ChannelChartKind
import com.beykus.beygram.domain.model.ChannelChartPoint
import com.beykus.beygram.domain.model.ChannelChartSeries
import com.beykus.beygram.domain.model.ChannelStatisticsChart
import com.beykus.beygram.ui.components.BeyGramPalette
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun StatisticsChartsSection(
    charts: List<ChannelStatisticsChart>,
    palette: BeyGramPalette,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            stringResource(R.string.channel_statistics_charts_title),
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (charts.isEmpty()) {
            Text(
                stringResource(R.string.channel_statistics_charts_empty),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            charts.forEach { chart ->
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
    val series = remember(chart) { chart.series.filter { it.points.isNotEmpty() } }
    if (series.isEmpty()) return
    val title = stringResource(chartTitle(chart.id))
    val range = remember(chart) { chartRange(chart, series) }
    val maxLabel = formatCompactCount(range.second)
    val minLabel = formatCompactCount(range.first)
    val rangeLabel = if (chart.yScaled && series.size > 1) {
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

    Column(
        modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            rangeLabel,
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp)
                .semantics { contentDescription = description }
        ) {
            drawStatisticsChart(chart, series, palette, range.first, range.second)
        }
        val axisStart = axis.first
        val axisEnd = axis.second
        if (axisStart != null && axisEnd != null) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                Text(axisStart, color = palette.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(axisEnd, color = palette.textSecondary, fontSize = 11.sp)
            }
        }
        if (series.size > 1) ChartLegend(series, palette, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ChartLegend(
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        series.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { columnIndex, item ->
                    Row(
                        Modifier.weight(1f).padding(end = if (columnIndex == 0) 6.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(8.dp)
                                .background(seriesColor(palette, rowIndex * 2 + columnIndex), CircleShape)
                        )
                        Text(
                            seriesName(item),
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
    if (chart.kind == ChannelChartKind.Bar || chart.kind == ChannelChartKind.Area) {
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
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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
        drawPath(path, color, style = stroke)
    }
}

private fun DrawScope.drawLines(
    chart: ChannelStatisticsChart,
    series: List<ChannelChartSeries>,
    palette: BeyGramPalette,
    x: (Long) -> Float,
    y: (Long) -> Float
) {
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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
        drawPath(path, color, style = stroke)
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

private fun seriesColor(palette: BeyGramPalette, index: Int): Color = when (index % 6) {
    0 -> palette.accent
    1 -> palette.successText
    2 -> palette.warningText
    3 -> palette.danger
    4 -> palette.textSecondary
    else -> palette.accentInk
}
