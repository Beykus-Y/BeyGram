package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretChatTest {
    @Test
    fun `tdlib secret chat ttl bounds are explicit`() {
        assertTrue(SecretChatTtl.isValid(SecretChatTtl.OFF))
        assertTrue(SecretChatTtl.isValid(SecretChatTtl.ONE_HOUR))
        assertTrue(SecretChatTtl.isValid(SecretChatTtl.ONE_DAY))
        assertTrue(SecretChatTtl.isValid(SecretChatTtl.SEVEN_DAYS))
        assertTrue(SecretChatTtl.isValid(SecretChatTtl.MAX_SECONDS))
        assertFalse(SecretChatTtl.isValid(-1))
        assertFalse(SecretChatTtl.isValid(SecretChatTtl.MAX_SECONDS + 1))
        assertEquals(listOf(0, 3_600, 86_400, 604_800), SecretChatTtl.presets)
    }

    @Test
    fun `custom ttl combines days hours and minutes`() {
        assertEquals(93_780, SecretChatTtl.customSeconds(days = 1, hours = 2, minutes = 3))
        assertEquals(60, SecretChatTtl.customSeconds(days = 0, hours = 0, minutes = 1))
        assertNull(SecretChatTtl.customSeconds(days = 0, hours = 0, minutes = 0))
        assertNull(SecretChatTtl.customSeconds(days = -1, hours = 0, minutes = 1))
        assertNull(SecretChatTtl.customSeconds(days = 0, hours = 24, minutes = 0))
        assertNull(SecretChatTtl.customSeconds(days = 0, hours = 0, minutes = 60))
        assertNull(SecretChatTtl.customSeconds(days = 366, hours = 0, minutes = 0))
    }

    @Test
    fun `ttl duration keeps only the two largest units`() {
        assertEquals(TtlParts(days = 1, hours = 2, minutes = 3, seconds = 4), ttlParts(93_784))
        assertEquals("1 d 2 h", formatTtlDuration(93_780, "d", "h", "min", "s"))
        assertEquals("1 h", formatTtlDuration(3_600, "d", "h", "min", "s"))
        assertEquals("45 s", formatTtlDuration(45, "d", "h", "min", "s"))
        assertEquals("", formatTtlDuration(0, "d", "h", "min", "s"))
    }

    @Test
    fun `secret chat eligibility rejects self bots and deleted users`() {
        assertTrue(SecretChatEligibility.canStart(userId = 42, isSelf = false, isBot = false))
        assertFalse(SecretChatEligibility.canStart(userId = 42, isSelf = true, isBot = false))
        assertFalse(SecretChatEligibility.canStart(userId = 42, isSelf = false, isBot = true))
        assertFalse(SecretChatEligibility.canStart(userId = 42, isSelf = false, isBot = false, isDeleted = true))
        assertFalse(SecretChatEligibility.canStart(userId = 0, isSelf = false, isBot = false))
    }

    @Test
    fun `view once is a single photo or video in a private chat`() {
        val photo = OutgoingMedia("/photo.jpg", "photo.jpg", "image/jpeg", MediaKind.Photo)
        val video = OutgoingMedia("/video.mp4", "video.mp4", "video/mp4", MediaKind.Video)
        val document = OutgoingMedia("/file.pdf", "file.pdf", "application/pdf", MediaKind.Document)

        assertTrue(ViewOncePolicy.canSend(listOf(photo), isPrivateChat = true, scheduled = false))
        assertTrue(ViewOncePolicy.canSend(listOf(video), isPrivateChat = true, scheduled = false))
        assertFalse(ViewOncePolicy.canSend(listOf(photo), isPrivateChat = false, scheduled = false))
        assertFalse(ViewOncePolicy.canSend(listOf(photo), isPrivateChat = true, isSecretChat = true, scheduled = false))
        assertFalse(ViewOncePolicy.canSend(listOf(photo), isPrivateChat = true, scheduled = true))
        assertFalse(ViewOncePolicy.canSend(listOf(photo, video), isPrivateChat = true, scheduled = false))
        assertFalse(ViewOncePolicy.canSend(listOf(document), isPrivateChat = true, scheduled = false))
    }

    @Test
    fun `view once media is never exportable or forwardable`() {
        assertTrue(ViewOncePolicy.canExport(canBeSaved = true, isViewOnce = false))
        assertFalse(ViewOncePolicy.canExport(canBeSaved = false, isViewOnce = false))
        assertFalse(ViewOncePolicy.canExport(canBeSaved = true, isViewOnce = true))
        assertTrue(ViewOncePolicy.canForward(canBeForwarded = true, isViewOnce = false))
        assertFalse(ViewOncePolicy.canForward(canBeForwarded = true, isViewOnce = true))
    }

    @Test
    fun `message view once state is separate from a timer`() {
        val message = ChatMessage(id = 1, chatId = 2, text = "", time = "", isOutgoing = false)
        assertFalse(message.isViewOnce)
        assertTrue(message.copy(selfDestruct = MessageSelfDestruct.Immediately).isViewOnce)
        assertFalse(message.copy(selfDestruct = MessageSelfDestruct.Timer(30)).isViewOnce)
    }
}
