package com.beykus.beygram.media.bgbeat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.GrowingPlaybackSource
import com.beykus.beygram.playback.PlaybackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BgMediaEligibilityTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun audio(path: String): PlaybackItem = PlaybackItem(
        key = "track",
        kind = MediaKind.Audio,
        path = path,
        title = "Track",
        durationSeconds = 180
    )

    @Test
    fun acceptsACompletedLocalAudioFile() {
        val file = tempFolder.newFile("track.mp3").apply { writeBytes(ByteArray(64)) }
        assertNull(BgMediaEligibility.reasonFor(audio(file.absolutePath)))
    }

    @Test
    fun rejectsNonAudioAndPrivateMedia() {
        val file = tempFolder.newFile("track.mp3").apply { writeBytes(ByteArray(64)) }
        assertEquals(
            BgBeatIneligibleReason.NotMusic,
            BgMediaEligibility.reasonFor(audio(file.absolutePath).copy(kind = MediaKind.Video))
        )
        assertEquals(
            BgBeatIneligibleReason.PrivateMedia,
            BgMediaEligibility.reasonFor(audio(file.absolutePath).copy(secret = true))
        )
        assertEquals(
            BgBeatIneligibleReason.PrivateMedia,
            BgMediaEligibility.reasonFor(audio(file.absolutePath).copy(isSecretChat = true))
        )
    }

    @Test
    fun rejectsGrowingAndMissingFiles() {
        val file = tempFolder.newFile("track.mp3").apply { writeBytes(ByteArray(64)) }
        val growing = GrowingPlaybackSource(
            totalSize = 100,
            readablePrefix = { 64 },
            isComplete = { false },
            completedPath = { null },
            isDownloading = { true },
            onNeedMore = {}
        )
        assertEquals(
            BgBeatIneligibleReason.StillDownloading,
            BgMediaEligibility.reasonFor(audio(file.absolutePath).copy(growing = growing))
        )
        assertEquals(
            BgBeatIneligibleReason.NotDownloaded,
            BgMediaEligibility.reasonFor(audio(tempFolder.root.resolve("missing.mp3").absolutePath))
        )
        val temp = tempFolder.newFile("voice.temp").apply { writeBytes(ByteArray(64)) }
        assertEquals(
            BgBeatIneligibleReason.NotDownloaded,
            BgMediaEligibility.reasonFor(audio(temp.absolutePath))
        )
    }
}
