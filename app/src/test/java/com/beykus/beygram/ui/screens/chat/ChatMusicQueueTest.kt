package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessageSelfDestruct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMusicQueueTest {
    private fun message(id: Long, kind: MediaKind = MediaKind.Audio, secret: Boolean = false) =
        ChatMessage(
            id = id, chatId = 9L, text = "", time = "", isOutgoing = false,
            media = MessageMedia(kind = kind, fileId = id.toInt(), isSecret = secret)
        )

    @Test fun newerFirstAndUnloadedTracksRemainInQueue() {
        val result = orderedChatMusic(
            listOf(message(1), message(3), message(2), message(3)),
            "account", { MediaFileState(it) }, "Audio"
        )
        assertEquals(listOf(3L, 2L, 1L), result.map { it.origin?.messageId })
        assertTrue(result.all { it.path.isBlank() })
        assertTrue(result.all { it.accountId == "account" })
    }

    @Test fun voiceAndSecretTracksAreExcluded() {
        val result = orderedChatMusic(
            listOf(message(1, MediaKind.Voice), message(2, secret = true), message(3)),
            "account", { MediaFileState(it) }, "Audio"
        )
        assertEquals(listOf(3L), result.map { it.origin?.messageId })
    }
}
