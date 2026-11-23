package com.beykus.beygram.playback
import com.beykus.beygram.domain.model.MediaKind

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgVideoNoteFileTest {
    private val note = PlaybackItem("note", MediaKind.VideoNote, "/local/note.mp4", "Note", 1)

    @Test
    fun onlyOptedInPublicVideoNotesAreEligible() {
        assertTrue(BgVideoNoteFile.eligible(note, enabled = true))
        assertFalse(BgVideoNoteFile.eligible(note, enabled = false))
        assertFalse(BgVideoNoteFile.eligible(note.copy(kind = MediaKind.Audio), enabled = true))
        assertFalse(BgVideoNoteFile.eligible(note.copy(isSecretChat = true), enabled = true))
        assertFalse(BgVideoNoteFile.eligible(note.copy(secret = true), enabled = true))
        assertFalse(BgVideoNoteFile.eligible(note.copy(viewOnce = true), enabled = true))
    }
}
