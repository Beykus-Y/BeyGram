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

    @Test
    fun `key fingerprint groups bytes as uppercase hex`() {
        assertEquals("", SecretChatKey.fingerprint(emptyList()))
        assertEquals("00AB7FFF", SecretChatKey.fingerprint(listOf<Byte>(0x00, 0xAB.toByte(), 0x7F, 0xFF.toByte())))
        assertEquals(
            "00112233 44556677",
            SecretChatKey.fingerprint(listOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77).map(Int::toByte))
        )
    }

    // Reference values computed with Telegram for Android's IdenticonActivity and IdenticonDrawable.
    private val fullKeyHash = List(36) { ((it * 37 + 11) and 0xFF).toByte() }

    @Test
    fun `key emoji follow the official secret chat mapping`() {
        assertEquals(333, SecretChatKeyEmoji.table.size)
        assertEquals("\uD83D\uDE09", SecretChatKeyEmoji.table.first())
        assertEquals("\uD83D\uDD37", SecretChatKeyEmoji.table.last())
        assertEquals(
            listOf("\uD83D\uDC55", "\uD83D\uDCB0", "\uD83C\uDFAF", "7\u20E3", "\uD83D\uDC4D"),
            SecretChatKey.emoji(fullKeyHash)
        )
        assertEquals(emptyList<String>(), SecretChatKey.emoji(fullKeyHash.take(16)))
    }

    @Test
    fun `key picture reads two low bits per pixel`() {
        val pixels = SecretChatKey.identicon(fullKeyHash)
        assertEquals(12, SecretChatKey.identiconSide(fullKeyHash))
        assertEquals(144, pixels.size)
        assertEquals(listOf(3, 2, 0, 0, 0, 0, 3, 0, 1, 1, 1, 1, 2, 2, 3, 1), pixels.take(16))
        assertEquals(listOf(2, 2, 1, 0), pixels.takeLast(4))
        assertEquals(8, SecretChatKey.identiconSide(fullKeyHash.take(16)))
        assertEquals(0, SecretChatKey.identiconSide(fullKeyHash.take(3)))
        assertEquals(emptyList<Int>(), SecretChatKey.identicon(fullKeyHash.take(3)))
    }

    @Test
    fun `key code shows the first 32 bytes like official clients`() {
        val code = SecretChatKey.fingerprint(fullKeyHash)
        assertEquals(8, code.split(" ").size)
        assertEquals("0B30557A", code.substringBefore(" "))
    }

    @Test
    fun `fingerprint never uses localized digits`() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            assertEquals("0A0B", SecretChatKey.fingerprint(listOf(0x0A, 0x0B)))
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test
    fun `closed secret chat blocks posting while history stays readable`() {
        assertTrue(SecretChatPosting.canPost(canPostInChat = true, isSecretChat = true, state = SecretChatState.Ready))
        assertTrue(SecretChatPosting.canPost(canPostInChat = true, isSecretChat = true, state = SecretChatState.Pending))
        assertTrue(SecretChatPosting.canPost(canPostInChat = true, isSecretChat = true, state = null))
        assertFalse(SecretChatPosting.canPost(canPostInChat = true, isSecretChat = true, state = SecretChatState.Closed))
        assertFalse(SecretChatPosting.canPost(canPostInChat = false, isSecretChat = false, state = null))
        // A stale non-secret state must never block an ordinary chat.
        assertTrue(SecretChatPosting.canPost(canPostInChat = true, isSecretChat = false, state = SecretChatState.Closed))
    }
}
