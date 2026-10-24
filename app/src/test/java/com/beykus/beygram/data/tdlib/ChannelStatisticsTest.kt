package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChannelEventFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelStatisticsTest {
    @Test
    fun `all filter requests every admin event group`() {
        val filters = adminLogFilters(ChannelEventFilter.All)
        assertTrue(filters.messageEdits)
        assertTrue(filters.memberJoins)
        assertTrue(filters.infoChanges)
        assertTrue(filters.subscriptionExtensions)
    }

    @Test
    fun `message filter excludes membership and settings events`() {
        val filters = adminLogFilters(ChannelEventFilter.Messages)
        assertTrue(filters.messageEdits)
        assertTrue(filters.messageDeletions)
        assertTrue(filters.messagePins)
        assertFalse(filters.memberJoins)
        assertFalse(filters.infoChanges)
        assertFalse(filters.inviteLinkChanges)
    }

    @Test
    fun `member filter excludes messages and settings events`() {
        val filters = adminLogFilters(ChannelEventFilter.Members)
        assertTrue(filters.memberInvites)
        assertTrue(filters.memberRestrictions)
        assertFalse(filters.messageEdits)
        assertFalse(filters.settingChanges)
    }

    @Test
    fun `settings filter excludes messages and membership events`() {
        val filters = adminLogFilters(ChannelEventFilter.Settings)
        assertTrue(filters.infoChanges)
        assertTrue(filters.videoChatChanges)
        assertFalse(filters.messagePins)
        assertFalse(filters.memberLeaves)
    }
}
