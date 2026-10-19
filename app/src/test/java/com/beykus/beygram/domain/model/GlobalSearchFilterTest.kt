package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobalSearchFilterTest {
    private val now = 1_800_000_000L

    @Test
    fun anyPeriodHasNoLowerBound() {
        assertNull(GlobalSearchPeriod.Any.minDate(now))
    }

    @Test
    fun weekAndMonthStepBackFromNow() {
        assertEquals((now - 7 * 24 * 60 * 60).toInt(), GlobalSearchPeriod.Week.minDate(now))
        assertEquals((now - 30 * 24 * 60 * 60).toInt(), GlobalSearchPeriod.Month.minDate(now))
    }

    @Test
    fun lowerBoundNeverGoesBelowZero() {
        assertEquals(0, GlobalSearchPeriod.Month.minDate(10))
    }

    @Test
    fun defaultFilterHasNoRestrictions() {
        val filter = GlobalSearchFilter()
        assertNull(filter.kind)
        assertNull(filter.chatType)
        assertEquals(GlobalSearchPeriod.Any, filter.period)
    }
}
