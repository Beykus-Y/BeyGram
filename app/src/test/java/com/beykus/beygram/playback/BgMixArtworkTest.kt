package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BgMixArtworkTest {

    private fun track(key: String, artwork: String? = null) = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = "/music/$key.mp3",
        title = key,
        durationSeconds = 180,
        artworkPath = artwork
    )

    @Test
    fun aCoverReachesEveryItemWithThatKeyAndNoOther() {
        val queue = listOf(track("a"), track("b"), track("a"))
        val updated = queue.withArtwork("b", "/covers/b.jpg")
        assertEquals(listOf(null, "/covers/b.jpg", null), updated.map { it.artworkPath })
        assertEquals(listOf("a", "b", "a"), updated.map { it.key })
    }

    @Test
    fun aCoverThatIsAlreadyThereIsNotCopiedAgain() {
        val queue = listOf(track("a", "/covers/a.jpg"), track("b"))
        assertEquals(queue, queue.withArtwork("a", "/covers/a.jpg"))
        assertEquals(queue, queue.withArtwork("missing", "/covers/x.jpg"))
    }
}
