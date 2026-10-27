package com.beykus.beygram.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStatisticsAccessTest {
    @Test
    fun `supergroup with canGetStatistics exposes group statistics`() {
        val info = ProfileInfo(id = 1L, title = "Group", kind = ProfileKind.Group, canViewStatistics = true)

        assertTrue(canOpenGroupStatistics(info))
    }

    @Test
    fun `ordinary member and basic group stay hidden`() {
        assertFalse(canOpenGroupStatistics(ProfileInfo(1L, "Group", kind = ProfileKind.Group)))
        assertFalse(canOpenGroupStatistics(ProfileInfo(1L, "Group", kind = ProfileKind.Group, canViewStatistics = false)))
    }

    @Test
    fun `channels do not use the group profile entry`() {
        val channel = ProfileInfo(id = 1L, title = "Channel", kind = ProfileKind.Channel, canViewStatistics = true)

        assertFalse(canOpenGroupStatistics(channel))
    }

    @Test
    fun `missing profile cannot expose statistics`() {
        assertFalse(canOpenGroupStatistics(null))
    }
}
