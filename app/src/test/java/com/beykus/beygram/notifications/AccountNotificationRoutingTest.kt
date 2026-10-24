package com.beykus.beygram.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccountNotificationRoutingTest {
    @Test fun equalTelegramIdsHaveDifferentAndroidPendingIntentIdentityPerAccount() {
        val chat = 10201L
        val legacy = notificationChatPath("legacy", chat, 34, 57, 0)
        val second = notificationChatPath("a-second", chat, 34, 57, 0)
        val third = notificationChatPath("a-third", chat, 34, 57, 0)
        assertNotEquals(legacy, second)
        assertNotEquals(second, third)
        assertEquals("chat/10201/34/57/0", legacy)
        assertEquals("account/a-second/chat/10201/34/57/0", second)
    }

    @Test fun inlineReplyUriIdentityIsDifferentForEqualNotificationGroupIds() {
        assertEquals("reply/12", notificationReplyPath("legacy", 12))
        assertEquals("account/a-first/reply/12", notificationReplyPath("a-first", 12))
        assertNotEquals(notificationReplyPath("a-first", 12), notificationReplyPath("a-second", 12))
    }

    @Test(expected = IllegalArgumentException::class)
    fun secondaryInlineReplyRejectsUnsafeAccountId() {
        notificationReplyPath("../foreign", 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun secondaryInlineReplyRejectsInvalidGroupId() {
        notificationReplyPath("a-first", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun accountIdPathInjectionIsRejected() {
        notificationChatPath("../legacy", 1L, 1, 1, 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyAccountIdIsRejected() {
        notificationChatPath("", 1L, 1, 1, 0L)
    }
}
