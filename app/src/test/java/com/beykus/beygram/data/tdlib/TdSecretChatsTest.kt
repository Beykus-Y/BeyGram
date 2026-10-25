package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSelfDestruct
import com.beykus.beygram.domain.model.SecretChatState
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdSecretChatsTest {
    private fun secret(
        id: Int = 7,
        userId: Long = 42,
        state: TdApi.SecretChatState = TdApi.SecretChatStateReady(),
        outbound: Boolean = true,
        layer: Int = 144
    ) = TdApi.SecretChat(id, userId, state, outbound, ByteArray(36), layer)

    @Test
    fun `secret chat state maps to lifecycle values`() {
        assertEquals(SecretChatState.Pending, secretChatStateOf(TdApi.SecretChatStatePending()))
        assertEquals(SecretChatState.Ready, secretChatStateOf(TdApi.SecretChatStateReady()))
        assertEquals(SecretChatState.Closed, secretChatStateOf(TdApi.SecretChatStateClosed()))
    }

    @Test
    fun `secret chat details keep chat and secret chat ids apart`() {
        val details = secret().toDetails(chatId = 1000L, ttlSeconds = 86_400)

        assertEquals(1000L, details.chatId)
        assertEquals(7, details.secretChatId)
        assertEquals(42L, details.userId)
        assertEquals(SecretChatState.Ready, details.state)
        assertEquals(86_400, details.ttlSeconds)
        assertEquals(144, details.layer)
    }

    @Test
    fun `secret chat id comes only from chat type secret`() {
        assertEquals(7, TdApi.ChatTypeSecret(7, 42).secretChatIdOrNull())
        assertNull(TdApi.ChatTypePrivate(42).secretChatIdOrNull())
    }

    @Test
    fun `self destruct types are mapped separately from zero`() {
        assertEquals(MessageSelfDestruct.Immediately, messageSelfDestructOf(TdApi.MessageSelfDestructTypeImmediately()))
        assertEquals(MessageSelfDestruct.Timer(30), messageSelfDestructOf(TdApi.MessageSelfDestructTypeTimer(30)))
        assertNull(messageSelfDestructOf(null))
    }

    @Test
    fun `lifecycle update cannot roll back a newer ttl`() {
        assertEquals(86_400, mergedSecretChatTtl(currentTtl = 86_400, fallbackTtl = 0))
        assertEquals(3_600, mergedSecretChatTtl(currentTtl = null, fallbackTtl = 3_600))
        assertEquals(0, mergedSecretChatTtl(currentTtl = -1, fallbackTtl = 3_600))
    }

    @Test
    fun `expired content keeps the attachment kind for a placeholder`() {
        assertEquals(MediaKind.Photo, expiredMediaKindOf(TdApi.MessageExpiredPhoto()))
        assertEquals(MediaKind.Video, expiredMediaKindOf(TdApi.MessageExpiredVideo()))
        assertEquals(MediaKind.VideoNote, expiredMediaKindOf(TdApi.MessageExpiredVideoNote()))
        assertEquals(MediaKind.Voice, expiredMediaKindOf(TdApi.MessageExpiredVoiceNote()))
        assertNull(expiredMediaKindOf(TdApi.MessageText()))
    }
}
