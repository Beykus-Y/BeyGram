package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.deeplink.yandexMusicEntityLink
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextUxPolicyTest {
    private fun message(id: Long, text: String = "", date: String? = null) = ChatMessage(
        id = id, chatId = 1L, text = text, time = "09:45", isOutgoing = false,
        dateSeconds = date?.let { Instant.parse(it).epochSecond } ?: 0L,
    )

    private fun row(id: Long, date: String) = ChatMessageListItem("message:$id", listOf(message(id, date = date)))

    @Test
    fun dateBoundaryUsesLocalDayAndDoesNotDependOnUnreadRow() {
        val items = listOf(
            row(3L, "2026-11-16T02:45:00Z"),
            row(2L, "2026-11-15T18:00:00Z"),
            ChatMessageListItem("unread", emptyList(), unreadSeparator = true),
            row(1L, "2026-11-15T09:51:00Z"),
        )
        assertEquals(
            mapOf("message:2" to LocalDate.of(2026, 11, 16)),
            chatDaySeparators(items, false, ZoneId.of("Asia/Barnaul")),
        )
    }

    @Test
    fun oldestDayAppearsOnlyWhenHistoryIsComplete() {
        val items = listOf(row(1L, "2026-11-15T09:51:00Z"))
        assertTrue(chatDaySeparators(items, false, ZoneId.of("UTC")).isEmpty())
        assertEquals(
            mapOf("message:1" to LocalDate.of(2026, 11, 15)),
            chatDaySeparators(items, true, ZoneId.of("UTC")),
        )
    }

    @Test
    fun lastLineMetadataSupportsWrappedAndForwardedPlainText() {
        val long = message(1L, "A long forwarded message\nShort last line").copy(
            isForwarded = true, forwardedFromName = "Original",
        )
        assertTrue(useLastLineTextMetadata(long, false))
        assertFalse(useLastLineTextMetadata(long, true))
        assertTrue(useLastLineTextMetadata(long.copy(entities = listOf(
            MessageTextEntity(0, 1, TextStyleKind.Bold)
        )), false))
        assertFalse(useLastLineTextMetadata(long.copy(sendState = com.beykus.beygram.domain.model.MessageSendState.Failed), false))
    }

    @Test
    fun lastLineMetadataUsesMeasuredWidthAndGap() {
        assertTrue(lastLineMetadataFits(lastLineWidth = 120, metaWidth = 50, gap = 4, maxWidth = 200))
        assertTrue(lastLineMetadataFits(lastLineWidth = 146, metaWidth = 50, gap = 4, maxWidth = 200))
        assertFalse(lastLineMetadataFits(lastLineWidth = 147, metaWidth = 50, gap = 4, maxWidth = 200))
        assertFalse(lastLineMetadataFits(lastLineWidth = Int.MAX_VALUE, metaWidth = 20, gap = 4, maxWidth = 200))
        assertFalse(lastLineMetadataFits(lastLineWidth = 120, metaWidth = 0, gap = 4, maxWidth = 200))
    }

    @Test
    fun symbolOnlyNamesRetainRealIdentity() {
        assertTrue(isSymbolOnlyDisplayName("🏎️"))
        assertTrue(isSymbolOnlyDisplayName("❤️"))
        assertFalse(isSymbolOnlyDisplayName("ден"))
        assertFalse(isSymbolOnlyDisplayName("Beykus🏎️"))
        assertFalse(isSymbolOnlyDisplayName("𐐀"))
        assertFalse(isSymbolOnlyDisplayName(" "))
    }

    @Test
    fun standaloneMusicUrlCanBeReplacedButCaptionAndSpoilerCannot() {
        val raw = "https://music.yandex.ru/album/123/track/456?utm_source=share"
        val link = requireNotNull(yandexMusicEntityLink(raw))
        val standalone = message(1L, raw)
        val urlEntity = MessageTextEntity(0, raw.length, TextStyleKind.Url)
        assertTrue(isStandaloneMusicUrl(standalone, link))
        assertTrue(isStandaloneMusicUrl(standalone.copy(entities = listOf(urlEntity)), link))
        assertFalse(isStandaloneMusicUrl(standalone.copy(text = "Listen! $raw"), link))
        assertFalse(isStandaloneMusicUrl(standalone.copy(text = "$raw\nhttps://example.org"), link))
        assertFalse(isStandaloneMusicUrl(standalone.copy(entities = listOf(urlEntity, MessageTextEntity(0, raw.length, TextStyleKind.Spoiler))), link))
        assertFalse(isStandaloneMusicUrl(standalone.copy(entities = listOf(urlEntity.copy(argument = "https://example.org"))), link))
    }
}
