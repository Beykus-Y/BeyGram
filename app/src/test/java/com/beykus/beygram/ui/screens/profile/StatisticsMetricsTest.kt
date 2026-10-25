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
}
