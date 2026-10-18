package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatActivity
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class ChatActivityTest {
    @Test fun `each kind of outgoing media shows its own upload activity`() {
        assertEquals(ChatActivity.UploadingPhoto, uploadActivityOf(TdApi.MessagePhoto()))
        assertEquals(ChatActivity.UploadingVideo, uploadActivityOf(TdApi.MessageVideo()))
        assertEquals(ChatActivity.UploadingVideoNote, uploadActivityOf(TdApi.MessageVideoNote()))
        assertEquals(ChatActivity.UploadingVoice, uploadActivityOf(TdApi.MessageVoiceNote()))
        assertEquals(ChatActivity.UploadingDocument, uploadActivityOf(TdApi.MessageDocument()))
    }

    @Test fun `audio and animations upload as their nearest kind`() {
        assertEquals(ChatActivity.UploadingDocument, uploadActivityOf(TdApi.MessageAudio()))
        assertEquals(ChatActivity.UploadingVideo, uploadActivityOf(TdApi.MessageAnimation()))
    }

    @Test fun `text and stickers upload nothing`() {
        assertNull(uploadActivityOf(TdApi.MessageText()))
        assertNull(uploadActivityOf(TdApi.MessageSticker()))
    }

    @Test fun `typing stops five seconds after the last keystroke`() {
        assertFalse(typingExpired(touchedAtNs = 0L, nowNs = 4_999_999_999L))
        assertTrue(typingExpired(touchedAtNs = 0L, nowNs = 5_000_000_000L))
    }
}
