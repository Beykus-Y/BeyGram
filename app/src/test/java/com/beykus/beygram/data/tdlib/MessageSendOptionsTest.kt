package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageSendOptions
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSendOptionsTest {
    @Test
    fun `normal send keeps protocol defaults`() {
        val request = sendOptions(MessageSendOptions())

        assertFalse(request.disableNotification)
        assertNull(request.schedulingState)
    }

    @Test
    fun `silent send disables notification without scheduling`() {
        val request = sendOptions(MessageSendOptions(disableNotification = true))

        assertTrue(request.disableNotification)
        assertNull(request.schedulingState)
    }

    @Test
    fun `scheduled send carries the exact unix timestamp`() {
        val at = 1_900_000_000L
        val request = sendOptions(MessageSendOptions(scheduledAtSeconds = at))
        val state = request.schedulingState as TdApi.MessageSchedulingStateSendAtDate

        assertFalse(request.disableNotification)
        assertEquals(at.toInt(), state.sendDate)
        assertEquals(0, state.repeatPeriod)
    }

    @Test
    fun `silent and scheduled options combine`() {
        val at = 1_900_000_001L
        val request = sendOptions(MessageSendOptions(disableNotification = true, scheduledAtSeconds = at))
        val state = request.schedulingState as TdApi.MessageSchedulingStateSendAtDate

        assertTrue(request.disableNotification)
        assertEquals(at.toInt(), state.sendDate)
    }

    @Test
    fun `scheduling state is absent for immediate sends`() {
        assertNull(scheduledState(null))
        assertTrue(scheduledState(1_900_000_000L) is TdApi.MessageSchedulingStateSendAtDate)
    }

    @Test
    fun `scheduled time must clear TDLib minimum lead time`() {
        assertTrue(MessageSendOptions().scheduledTimeIsFuture(nowSeconds = 100L))
        assertFalse(MessageSendOptions(scheduledAtSeconds = 100L).scheduledTimeIsFuture(nowSeconds = 100L))
        assertFalse(MessageSendOptions(scheduledAtSeconds = 110L).scheduledTimeIsFuture(nowSeconds = 100L))
        assertTrue(MessageSendOptions(scheduledAtSeconds = 111L).scheduledTimeIsFuture(nowSeconds = 100L))
    }

    @Test
    fun `view once uses the per-message immediately self destruct type`() {
        assertNull(selfDestructType(MessageSendOptions()))
        assertTrue(selfDestructType(MessageSendOptions(viewOnce = true)) is TdApi.MessageSelfDestructTypeImmediately)
    }
}
