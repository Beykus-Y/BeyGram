package com.beykus.beygram.media.bgbeat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BgBeatManagerPolicyTest {

    @Test
    fun analysisIdentityEqualsOnlyWhenAllKeyParametersMatch() {
        val id1 = BgBeatAnalysisIdentity(
            accountId = "acc1",
            itemKey = "item_100",
            filePath = "/data/user/0/com.beykus.beygram/files/song.mp3",
            fileLength = 5_000_000L,
            fileLastModified = 1700000000L
        )

        val id2 = BgBeatAnalysisIdentity(
            accountId = "acc1",
            itemKey = "item_100",
            filePath = "/data/user/0/com.beykus.beygram/files/song.mp3",
            fileLength = 5_000_000L,
            fileLastModified = 1700000000L
        )

        assertEquals("Identities with identical attributes must be equal", id1, id2)
        assertEquals(id1.hashCode(), id2.hashCode())

        // File download completed (fileLength grew from 1M to 5M)
        val idPartial = id1.copy(fileLength = 1_000_000L)
        assertNotEquals("Partially downloaded file has different identity", id1, idPartial)

        // Account switched
        val idOtherAccount = id1.copy(accountId = "acc2")
        assertNotEquals("Same track on different account has distinct identity", id1, idOtherAccount)

        // Track switched
        val idOtherItem = id1.copy(itemKey = "item_200")
        assertNotEquals("Different track has distinct identity", id1, idOtherItem)
    }

    @Test
    fun playbackItemEligibilityPolicyChecks() {
        val regularMusic = PlaybackItem(
            key = "music_1",
            kind = MediaKind.Audio,
            path = "/valid/path.mp3",
            title = "Track",
            durationSeconds = 180,
            secret = false,
            growing = null
        )

        assertTrue(regularMusic.isMusic)
        assertFalse(regularMusic.secret)
        assertFalse(regularMusic.systemPrivate)

        val voiceMessage = PlaybackItem(
            key = "voice_1",
            kind = MediaKind.Voice,
            path = "/valid/voice.ogg",
            title = "Voice",
            durationSeconds = 15
        )
        assertFalse("Voice notes must not be eligible for BG Beat music analysis", voiceMessage.isMusic)

        val secretAttachment = regularMusic.copy(secret = true)
        assertTrue("Secret attachments must be flagged", secretAttachment.secret || secretAttachment.systemPrivate)

        val growingSource = com.beykus.beygram.playback.GrowingPlaybackSource(
            totalSize = 10_000_000L,
            readablePrefix = { 1_000_000L },
            isComplete = { false },
            completedPath = { null },
            isDownloading = { true },
            onNeedMore = {}
        )
        val growingDownload = regularMusic.copy(growing = growingSource)
        assertTrue("Actively downloading media must be detected", growingDownload.growing != null)
    }
}
