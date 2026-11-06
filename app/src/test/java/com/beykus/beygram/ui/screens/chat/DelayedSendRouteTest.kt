package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class DelayedSendRouteTest {
    @Test fun aSendIsHeldWhileTheQueueAndTheDelayAreStillBeingRead() {
        // "Not read yet" is never taken for "delay off", whatever the other inputs say.
        assertEquals(DelayedSendRoute.Hold, delayedSendRoute(setupRead = false, hasQueue = false, delaySeconds = 0, eligible = true))
        assertEquals(DelayedSendRoute.Hold, delayedSendRoute(setupRead = false, hasQueue = true, delaySeconds = 5, eligible = true))
        assertEquals(DelayedSendRoute.Hold, delayedSendRoute(setupRead = false, hasQueue = true, delaySeconds = 0, eligible = false))
    }

    @Test fun aReadDelayOfZeroSendsAtOnce() {
        assertEquals(DelayedSendRoute.SendNow, delayedSendRoute(setupRead = true, hasQueue = true, delaySeconds = 0, eligible = true))
    }

    @Test fun aReadDelayQueuesAnEligibleText() {
        assertEquals(DelayedSendRoute.Queue, delayedSendRoute(setupRead = true, hasQueue = true, delaySeconds = 5, eligible = true))
    }

    @Test fun anIneligibleTextOrAnAccountWithoutAQueueSendsAtOnce() {
        assertEquals(DelayedSendRoute.SendNow, delayedSendRoute(setupRead = true, hasQueue = true, delaySeconds = 5, eligible = false))
        assertEquals(DelayedSendRoute.SendNow, delayedSendRoute(setupRead = true, hasQueue = false, delaySeconds = 5, eligible = true))
    }
}
