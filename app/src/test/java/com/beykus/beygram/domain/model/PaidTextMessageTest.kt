package com.beykus.beygram.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidTextMessageTest {
    @Test fun onlyPositiveAffordablePriceCanBeConfirmed() {
        assertTrue(paidTextCanConfirm(5L, StarBalance(5L)))
        assertFalse(paidTextCanConfirm(5L, StarBalance(4L, 999999999)))
        assertFalse(paidTextCanConfirm(5L, null))
        assertFalse(paidTextCanConfirm(0L, StarBalance(50L)))
        assertFalse(paidTextCanConfirm(-1L, StarBalance(50L)))
    }

    @Test fun anyChangedPriceNeedsAnotherConfirmation() {
        assertTrue(paidTextPriceMatches(5L, 5L))
        assertFalse(paidTextPriceMatches(5L, 6L))
        assertFalse(paidTextPriceMatches(5L, 4L))
        assertFalse(paidTextPriceMatches(5L, 0L))
        assertFalse(paidTextPriceMatches(0L, 0L))
    }
    @Test fun paidReplyCannotChangeTopic() {
        assertTrue(paidTextReplyTopicMatches(0, null, false))
        assertFalse(paidTextReplyTopicMatches(0, 17, true))
        assertFalse(paidTextReplyTopicMatches(0, null, true))
        assertTrue(paidTextReplyTopicMatches(17, 17, true))
        assertFalse(paidTextReplyTopicMatches(17, 18, true))
        assertFalse(paidTextReplyTopicMatches(17, null, false))
        assertFalse(paidTextReplyTopicMatches(Long.MAX_VALUE, Long.MAX_VALUE, true))
    }

    @Test fun destinationIncludesAccountChatAndTopic() {
        val destination = PaidTextDestination("a", 123, 17)
        assertEquals(destination, destination.copy())
        assertFalse(destination == destination.copy(accountId = "b"))
        assertFalse(destination == destination.copy(chatId = 124))
        assertFalse(destination == destination.copy(forumTopicId = 18))
    }
}
