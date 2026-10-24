package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChannelChartId
import com.beykus.beygram.domain.model.ChannelChartKind
import com.beykus.beygram.domain.model.ChannelChartPoint
import com.beykus.beygram.domain.model.ChannelChartSeries
import com.beykus.beygram.domain.model.ChannelStatisticsChart
import com.beykus.beygram.domain.model.ChannelTopPost
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelStatisticsChartsTest {
    private fun graph(
        x: List<Number>,
        series: Map<String, List<Number>>,
        names: Map<String, String> = emptyMap(),
        types: Map<String, String> = emptyMap(),
        hidden: List<String> = emptyList(),
        stacked: Boolean = false,
        percentage: Boolean = false,
        yScaled: Boolean = false
    ): String {
        val columns = JSONArray()
        columns.put(JSONArray(listOf("x") + x))
        series.forEach { (label, values) -> columns.put(JSONArray(listOf(label) + values)) }
        return JSONObject()
            .put("columns", columns)
            .put("names", JSONObject(names))
            .put("types", JSONObject(types))
            .put("hidden", JSONArray(hidden))
            .put("stacked", stacked)
            .put("percentage", percentage)
            .put("y_scaled", yScaled)
            .toString()
    }

    @Test
    fun `line graph is parsed with names and sorted by x`() {
        val chart = parseChannelChart(
            ChannelChartId.Members,
            graph(
                x = listOf(3000, 1000, 2000),
                series = mapOf("y0" to listOf(30, 10, 20)),
                names = mapOf("y0" to "Members"),
                types = mapOf("y0" to "line"),
                yScaled = true
            ),
            ChannelChartKind.Bar
        )

        assertNotNull(chart)
        assertEquals(ChannelChartKind.Line, chart!!.kind)
        assertTrue(chart.yScaled)
        assertEquals(1, chart.series.size)
        assertEquals("Members", chart.series.single().name)
        assertEquals(listOf(1000L, 2000L, 3000L), chart.series.single().points.map { it.x })
        assertEquals(listOf(10L, 20L, 30L), chart.series.single().points.map { it.value })
    }

    @Test
    fun `values beyond the x axis are ignored instead of crashing`() {
        val chart = parseChannelChart(
            ChannelChartId.Members,
            graph(
                x = listOf(1000),
                series = mapOf("y0" to listOf(5, 6, 7)),
                types = mapOf("y0" to "line")
            ),
            ChannelChartKind.Line
        )

        assertNotNull(chart)
        assertEquals(1, chart!!.series.single().points.size)
        assertEquals(5L, chart.series.single().points.single().value)
    }

    @Test
    fun `empty top post list stays empty`() {
        assertTrue(rankTopPosts(emptyList()).isEmpty())
    }

    @Test
    fun `hidden columns are skipped and bars are stacked`() {
        val chart = parseChannelChart(
            ChannelChartId.ViewsBySource,
            graph(
                x = listOf(1000, 2000),
                series = mapOf(
                    "y0" to listOf(1, 2),
                    "y1" to listOf(10, 20),
                    "y2" to listOf(100, 200)
                ),
                names = mapOf("y0" to "Search", "y1" to "Other"),
                types = mapOf("y0" to "bar", "y1" to "bar", "y2" to "bar"),
                hidden = listOf("y2"),
                stacked = true
            ),
            ChannelChartKind.Line
        )

        assertNotNull(chart)
        assertEquals(ChannelChartKind.Bar, chart!!.kind)
        assertTrue(chart.stacked)
        assertEquals(listOf("Search", "Other"), chart.series.map { it.name })
        assertTrue(chart.series.none { it.points.any { point -> point.value >= 100L } })
    }

    @Test
    fun `percentage area is normalized at every x position`() {
        val chart = parseChannelChart(
            ChannelChartId.Languages,
            graph(
                x = listOf(1000, 2000),
                series = mapOf("y0" to listOf(1, 2), "y1" to listOf(3, 2)),
                types = mapOf("y0" to "area", "y1" to "area"),
                stacked = true,
                percentage = true
            ),
            ChannelChartKind.Area
        )

        assertNotNull(chart)
        assertTrue(chart!!.percentage)
        assertEquals(listOf(25L, 50L), chart.series[0].points.map { it.value })
        assertEquals(listOf(75L, 50L), chart.series[1].points.map { it.value })
    }

    @Test
    fun `unknown graph type falls back to the requested kind`() {
        val chart = parseChannelChart(
            ChannelChartId.MessageContent,
            graph(
                x = listOf(1000),
                series = mapOf("y0" to listOf(7)),
                types = mapOf("y0" to "unsupported")
            ),
            ChannelChartKind.Area
        )

        assertNotNull(chart)
        assertEquals(ChannelChartKind.Area, chart!!.kind)
    }

    @Test
    fun `malformed and empty graphs return null`() {
        assertNull(parseChannelChart(ChannelChartId.Members, "{not json", ChannelChartKind.Line))
        assertNull(parseChannelChart(ChannelChartId.Members, "", ChannelChartKind.Line))
        assertNull(parseChannelChart(ChannelChartId.Members, """{"columns":[["x"],["y0"]]}""", ChannelChartKind.Line))
        assertNull(parseChannelChart(ChannelChartId.Members, """{"columns":[["x",1000],["y0"]]}""", ChannelChartKind.Line))
    }

    @Test
    fun `excess series are merged into one other series`() {
        val series = (1..7).associate { index -> "y$index" to listOf<Number>(index) }
        val names = (1..7).associate { index -> "y$index" to "Series $index" }
        val types = (1..7).associate { index -> "y$index" to "line" }
        val chart = parseChannelChart(
            ChannelChartId.Languages,
            graph(x = listOf(1000), series = series, names = names, types = types),
            ChannelChartKind.Area
        )

        assertNotNull(chart)
        assertEquals(MAX_CHART_SERIES, chart!!.series.size)
        assertEquals(
            listOf("Series 7", "Series 6", "Series 5", "Series 4", "Series 3"),
            chart.series.dropLast(1).map { it.name }
        )
        val other = chart.series.last()
        assertTrue(other.isOther)
        assertEquals(3L, other.points.single().value)
    }

    @Test
    fun `line decimation keeps both ends and the point budget`() {
        val points = (0 until 1000).map { index -> ChannelChartPoint(index.toLong(), index.toLong()) }
        val chart = ChannelStatisticsChart(
            id = ChannelChartId.Members,
            kind = ChannelChartKind.Line,
            series = listOf(ChannelChartSeries("Members", points))
        )

        val normalized = normalizeChannelChart(chart, 10)

        assertEquals(10, normalized.series.single().points.size)
        assertEquals(0L, normalized.series.single().points.first().x)
        assertEquals(999L, normalized.series.single().points.last().x)
        assertEquals(999L, normalized.series.single().points.last().value)
    }

    @Test
    fun `bar decimation sums buckets instead of dropping values`() {
        val points = (0 until 250).map { index -> ChannelChartPoint(index.toLong(), 1L) }
        val chart = ChannelStatisticsChart(
            id = ChannelChartId.ViewsByHour,
            kind = ChannelChartKind.Bar,
            series = listOf(ChannelChartSeries("Views", points))
        )

        val normalized = normalizeChannelChart(chart, 10)

        assertEquals(10, normalized.series.single().points.size)
        assertEquals(250L, normalized.series.single().points.sumOf { it.value })
    }

    @Test
    fun `top posts are ranked by views then shares then reactions`() {
        val posts = listOf(
            ChannelTopPost(messageId = 1L, viewCount = 100, forwardCount = 5),
            ChannelTopPost(messageId = 2L, viewCount = 100, forwardCount = 10),
            ChannelTopPost(messageId = 3L, viewCount = 90, forwardCount = 50),
            ChannelTopPost(messageId = 4L, viewCount = 100, forwardCount = 5, reactionCount = 7)
        )

        val ranked = rankTopPosts(posts, limit = 3)

        assertEquals(listOf(2L, 4L, 1L), ranked.map { it.messageId })
        assertFalse(ranked.any { it.messageId == 3L })
    }
}
