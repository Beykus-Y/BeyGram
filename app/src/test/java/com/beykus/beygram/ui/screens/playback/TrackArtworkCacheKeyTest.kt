package com.beykus.beygram.ui.screens.playback

import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackArtworkCacheKeyTest {
    private val cover = File("/data/thumbs/cover.jpg")
    private val audio = File("/data/music/song.mp3")

    @Test fun noSourcesHaveNoCacheKey() {
        assertNull(artworkCacheKey(null, null))
    }

    @Test fun audioArrivingAfterAMissedCoverChangesTheKey() {
        // A cover that does not decode caches a miss; the audio file downloaded later must still be tried.
        assertNotEquals(artworkCacheKey(cover, null), artworkCacheKey(cover, audio))
    }

    @Test fun coverAloneAndAudioAloneAreDistinct() {
        assertNotEquals(artworkCacheKey(cover, null), artworkCacheKey(null, audio))
    }
}
