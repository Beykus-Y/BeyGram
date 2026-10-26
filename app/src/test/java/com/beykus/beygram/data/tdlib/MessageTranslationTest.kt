package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.SpeechRecognitionFailure
import com.beykus.beygram.domain.model.SpeechRecognitionFailureReason
import com.beykus.beygram.domain.model.TextStyleKind
import com.beykus.beygram.domain.model.TranslationPolicy
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTranslationTest {
    private fun file(id: Int) = TdApi.File().apply {
        this.id = id
        size = 100
        local = TdApi.LocalFile()
        remote = TdApi.RemoteFile()
    }

    @Test fun `translation mapping preserves text formatting entities and language key`() {
        val formatted = TdApi.FormattedText("Hallo Welt", arrayOf(
            TdApi.TextEntity(0, 5, TdApi.TextEntityTypeBold()),
            TdApi.TextEntity(6, 4, TdApi.TextEntityTypeItalic())
        ))
        val translation = formatted.toMessageTranslation("de")
        assertEquals("Hallo Welt", translation.text)
        assertEquals(listOf(MessageTextEntity(0, 5, TextStyleKind.Bold), MessageTextEntity(6, 4, TextStyleKind.Italic)),
            translation.entities)
        assertEquals("de", translation.languageCode)
    }

    @Test fun `secret chats never expose translation or recognition policy`() {
        assertTrue(TdApi.ChatTypeSecret(1, 2L).isSecretChatType())
        assertFalse(TdApi.ChatTypePrivate(2L).isSecretChatType())
        assertFalse(TranslationPolicy.canTranslate(isSecretChat = true, hasText = true))
        assertFalse(TranslationPolicy.canTranslate(isSecretChat = true, hasText = false))
        assertTrue(TranslationPolicy.canTranslate(isSecretChat = false, hasText = true))
        assertFalse(TranslationPolicy.canTranscribe(isSecretChat = true, media = MessageMedia(MediaKind.Voice, 1)))
        assertTrue(TranslationPolicy.canTranscribe(isSecretChat = false, media = MessageMedia(MediaKind.Voice, 1)))
    }

    @Test fun `speech recognition states map to text pending and friendly error`() {
        assertEquals(TranscriptionSnapshot(text = "hello"), TdApi.SpeechRecognitionResultText("hello").toTranscriptionSnapshot())
        assertEquals(TranscriptionSnapshot(pendingText = "hel"), TdApi.SpeechRecognitionResultPending("hel").toTranscriptionSnapshot())
        assertEquals(TranscriptionSnapshot(error = SpeechRecognitionFailure(SpeechRecognitionFailureReason.Other)),
            TdApi.SpeechRecognitionResultError(TdApi.Error(400, "SPEECH_RECOGNITION_FAILED")).toTranscriptionSnapshot())
        assertEquals(TranscriptionSnapshot(), (null as TdApi.SpeechRecognitionResult?).toTranscriptionSnapshot())
    }

    @Test fun `voice note media carries speech recognition state`() {
        val voice = TdApi.VoiceNote(12, byteArrayOf(), "audio/ogg",
            TdApi.SpeechRecognitionResultPending("partial"), file(9))
        val media = TdMediaMapper(MediaFileStore()).media(TdApi.MessageVoiceNote().apply { voiceNote = voice })!!
        assertEquals("partial", media.transcriptionPending)
        assertTrue(media.isTranscribing)
        assertEquals("", media.transcriptionText)
        assertNull(media.transcriptionError)
    }

    @Test fun `content update refreshes transcription media and keeps unrelated message fields`() {
        val original = ChatMessage(id = 7, chatId = 1, text = "caption", time = "12:00", isOutgoing = false,
            senderName = "Alice")
        val media = MessageMedia(MediaKind.Voice, 9, transcriptionText = "recognized")
        val updated = original.withUpdatedContent(TdApi.MessageVoiceNote(), media, null, "caption", emptyList(), null)
        assertEquals("recognized", updated.media?.transcriptionText)
        assertEquals("Alice", updated.senderName)
        assertEquals("caption", updated.text)
    }
}
