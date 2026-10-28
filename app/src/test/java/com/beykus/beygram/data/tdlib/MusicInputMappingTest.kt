package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicInputMappingTest {
    @Test
    fun musicTrackIsSentAsAudioByFileIdWithItsTags() {
        val media = OutgoingMedia(
            path = "",
            fileName = "manit.mp3",
            mimeType = "audio/mpeg",
            kind = MediaKind.Audio,
            durationSeconds = 185,
            remoteFileId = 42,
            title = "Манит",
            performer = "Люкселин"
        )

        val content = media.toInputContent("") as TdApi.InputMessageAudio

        assertEquals(42, (content.audio as TdApi.InputFileId).id)
        assertEquals(185, content.duration)
        assertEquals("Манит", content.title)
        assertEquals("Люкселин", content.performer)
        assertNull(content.albumCoverThumbnail)
    }
}
