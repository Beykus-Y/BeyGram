package com.beykus.beygram.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationReplyTokenTest {
    @Test
    fun sameRoutingAndSecretGiveSameToken() {
        assertEquals(replyRoutingToken("secret", 10L, 2, 3, 4L), replyRoutingToken("secret", 10L, 2, 3, 4L))
    }

    @Test
    fun changedRoutingOrSecretGivesAnotherToken() {
        val token = replyRoutingToken("secret", 10L, 2, 3, 4L)
        assertNotEquals(token, replyRoutingToken("secret", 99L, 2, 3, 4L))
        assertNotEquals(token, replyRoutingToken("secret", 10L, 2, 3, 5L))
        assertNotEquals(token, replyRoutingToken("secret", 10L, 2, 3, 0L))
        assertNotEquals(token, replyRoutingToken("other", 10L, 2, 3, 4L))
    }
}
