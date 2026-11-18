package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsMetricsTest {
    @Test
    fun `channel summary keeps channel means and notifications`() {
        assertEquals(
            listOf(
                StatisticsMetric.Members,
                StatisticsMetric.Growth,
                StatisticsMetric.MeanViews,
                StatisticsMetric.MeanShares,
                StatisticsMetric.MeanReactions,
                StatisticsMetric.Notifications
            ),
            statisticsMetrics(isChannel = true)
        )
    }

    @Test
    fun `supergroup summary uses totals instead of channel means`() {
        val metrics = statisticsMetrics(isChannel = false)

        assertEquals(
            listOf(
                StatisticsMetric.Members,
                StatisticsMetric.Growth,
                StatisticsMetric.Messages,
                StatisticsMetric.Viewers,
                StatisticsMetric.Senders
            ),
            metrics
        )
        assertEquals(emptyList<StatisticsMetric>(), metrics.intersect(
            setOf(
                StatisticsMetric.MeanViews,
                StatisticsMetric.MeanShares,
                StatisticsMetric.MeanReactions,
                StatisticsMetric.Notifications
            )
        ).toList())
    }

    @Test
    fun `channel cards show the audience and the per-post means`() {
        assertEquals(
            listOf(
                StatisticsMetric.Members,
                StatisticsMetric.MeanViews,
                StatisticsMetric.MeanReactions,
                StatisticsMetric.MeanShares
            ),
            statisticsHeadlineMetrics(isChannel = true)
        )
        assertEquals(listOf(StatisticsMetric.Notifications), statisticsDetailMetrics(isChannel = true))
    }

    @Test
    fun `supergroup cards show the totals and nothing is left for the compact list`() {
        assertEquals(
            listOf(
                StatisticsMetric.Members,
                StatisticsMetric.Messages,
                StatisticsMetric.Viewers,
                StatisticsMetric.Senders
            ),
            statisticsHeadlineMetrics(isChannel = false)
        )
        assertEquals(emptyList<StatisticsMetric>(), statisticsDetailMetrics(isChannel = false))
    }

    @Test
    fun `every figure appears exactly once across cards and the compact list`() {
        for (isChannel in listOf(true, false)) {
            val shown = statisticsHeadlineMetrics(isChannel) + statisticsDetailMetrics(isChannel)
            assertEquals(shown.distinct(), shown)
            assertEquals(
                statisticsMetrics(isChannel).filter { it != StatisticsMetric.Growth }.toSet(),
                shown.toSet()
            )
        }
    }
}
