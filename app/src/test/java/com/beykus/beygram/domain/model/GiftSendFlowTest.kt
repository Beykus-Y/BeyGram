package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GiftSendFlowTest {
    @Test
    fun `confirmation is required before sending`() {
        var step: GiftSendStep = GiftSendStep.Idle
        step = step.next(GiftSendEvent.Select(7L))
        assertEquals(GiftSendStep.Confirming(7L), step)
        step = step.next(GiftSendEvent.Confirm)
        assertEquals(GiftSendStep.Sending(7L), step)
    }

    @Test
    fun `double confirmation cannot start two purchases`() {
        var step: GiftSendStep = GiftSendStep.Confirming(7L).next(GiftSendEvent.Confirm)
        assertEquals(GiftSendStep.Sending(7L), step)
        step = step.next(GiftSendEvent.Confirm)
        assertEquals(GiftSendStep.Sending(7L), step)
    }

    @Test
    fun `select and dismiss are ignored while a request is in flight`() {
        val sending = GiftSendStep.Sending(7L)
        assertEquals(sending, sending.next(GiftSendEvent.Select(8L)))
        assertEquals(sending, sending.next(GiftSendEvent.Dismiss))
    }

    @Test
    fun `success only comes from the sending state`() {
        assertEquals(GiftSendStep.Sent(7L), GiftSendStep.Sending(7L).next(GiftSendEvent.Succeeded))
        assertEquals(GiftSendStep.Confirming(7L), GiftSendStep.Confirming(7L).next(GiftSendEvent.Succeeded))
    }

    @Test
    fun `uncertain transport result is recorded instead of retrying`() {
        val step = GiftSendStep.Sending(7L).next(GiftSendEvent.Uncertain)
        assertEquals(GiftSendStep.Failed(7L, uncertain = true), step)
        assertEquals(step, step.next(GiftSendEvent.Confirm))
    }

    @Test
    fun `a finished attempt can be dismissed and reopened explicitly`() {
        var step: GiftSendStep = GiftSendStep.Sent(7L)
        step = step.next(GiftSendEvent.Dismiss)
        assertEquals(GiftSendStep.Idle, step)
        step = step.next(GiftSendEvent.Select(8L))
        assertEquals(GiftSendStep.Confirming(8L), step)
    }
}
