package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.data.tdlib.MediaFileStore
import com.beykus.beygram.data.tdlib.TdMediaMapper
import com.beykus.beygram.data.tdlib.withUpdatedContent
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.BotButton
import com.beykus.beygram.domain.model.BotButtonKind
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.MessageSelfDestruct
import com.beykus.beygram.domain.model.MessageSpecialContent
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expired self-destructing media is a state of its own: it renders [ExpiredMediaCard] and must never be resolved to the
 * unsupported placeholder, including after TDLib replaces the content or the chat is loaded again.
 */
class ExpiredMediaPresentationTest {
    private val mapper = TdMediaMapper(MediaFileStore())

    private fun message(
        text: String = "",
        media: MessageMedia? = null,
        special: MessageSpecialContent? = null,
        expiredMediaKind: MediaKind? = null,
        reply: MessageReply? = null,
        inlineKeyboard: List<List<BotButton>> = emptyList()
    ) = ChatMessage(
        id = 1L,
        chatId = 2L,
        text = text,
        time = "12:00",
        isOutgoing = false,
        media = media,
        special = special,
        expiredMediaKind = expiredMediaKind,
        reply = reply,
        inlineKeyboard = inlineKeyboard
    )

    @Test
    fun `expired photo and video resolve to their own card without unsupported`() {
        assertNull(resolvedSpecialContent(message(expiredMediaKind = MediaKind.Photo)))
        assertNull(resolvedSpecialContent(message(expiredMediaKind = MediaKind.Video)))
        assertNull(resolvedSpecialContent(message(expiredMediaKind = MediaKind.VideoNote)))
        assertNull(resolvedSpecialContent(message(expiredMediaKind = MediaKind.Voice)))
    }

    @Test
    fun `stale unsupported content cannot add a second card to expired media`() {
        val stale = MessageSpecialContent.Unsupported("MessageExpiredVideo")
        assertNull(resolvedSpecialContent(message(expiredMediaKind = MediaKind.Video, special = stale)))
    }

    @Test
    fun `truly unknown content keeps the explicit fallback`() {
        val unknown = MessageSpecialContent.Unsupported("MessagePaidMedia")
        assertEquals(unknown, resolvedSpecialContent(message(special = unknown)))
        assertTrue(resolvedSpecialContent(message()) is MessageSpecialContent.Unsupported)
    }

    @Test
    fun `regular text media replies and keyboards never get the fallback`() {
        assertNull(resolvedSpecialContent(message(text = "hello")))
        assertNull(resolvedSpecialContent(message(media = MessageMedia(MediaKind.Photo, fileId = 10))))
        assertNull(resolvedSpecialContent(message(reply = MessageReply(2L, 3L, UiText.Raw("quoted")))))
        assertNull(resolvedSpecialContent(message(inlineKeyboard = listOf(listOf(BotButton("Open", BotButtonKind.Text))))))
    }

    @Test
    fun `tdlib content update turns a view once photo into expired media without unsupported`() {
        val content = TdApi.MessageExpiredPhoto()
        val before = message(
            media = MessageMedia(MediaKind.Photo, fileId = 10, isSecret = true),
            special = null
        ).copy(selfDestruct = MessageSelfDestruct.Immediately)

        val after = before.withUpdatedContent(
            content = content,
            media = mapper.media(content),
            special = mapper.special(content),
            text = "",
            entities = emptyList(),
            serviceText = null
        )

        assertTrue(before.isViewOnce)
        assertTrue(after.isViewOnce)
        assertNull(after.media)
        assertNull(after.special)
        assertEquals(MediaKind.Photo, after.expiredMediaKind)
        assertNull(resolvedSpecialContent(after))
    }
}
