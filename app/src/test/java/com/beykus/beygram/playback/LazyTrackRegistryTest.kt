package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.ui.screens.playback.canShareTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyTrackRegistryTest {
    private fun row(key: String, path: String = "", sizeBytes: Long = 0L) = PlaybackItem(
        key = key, kind = MediaKind.Audio, path = path, title = "Song", durationSeconds = 200, sizeBytes = sizeBytes
    )

    @Test fun keysRouteToTheProviderThatOwnsThePrefix() {
        assertSame(BotTrackResolver, LazyTrackRegistry.sourceFor("bot:12:abc"))
        assertSame(YandexTrackResolver, LazyTrackRegistry.sourceFor("ym:123:9"))
        assertNull(LazyTrackRegistry.sourceFor("123:456"))
        assertNull(LazyTrackRegistry.sourceFor("profile|1|2|3"))
        assertTrue(LazyTrackRegistry.isLazyKey("ym:123"))
        assertFalse(LazyTrackRegistry.isLazyKey("chat:1:2"))
    }

    @Test fun yandexKeysRoundTripTheTrackId() {
        assertEquals("ym:123:9", YandexTrackResolver.keyOf("123", "9"))
        assertEquals("ym:123", YandexTrackResolver.keyOf("123", null))
        assertEquals("ym:123", YandexTrackResolver.keyOf("123", " "))
        assertEquals("123", YandexTrackResolver.trackIdOf("ym:123:9"))
        assertEquals("123", YandexTrackResolver.trackIdOf("ym:123"))
        assertNull(YandexTrackResolver.trackIdOf("ym:"))
        assertNull(YandexTrackResolver.trackIdOf("ym::9"))
        assertNull(YandexTrackResolver.trackIdOf("bot:1:abc"))
    }

    @Test fun onlyTheYandexProviderIsRestricted() {
        assertTrue(YandexTrackResolver.restricted)
        assertFalse(BotTrackResolver.restricted)
        assertTrue(row("ym:123:9").isRestrictedCatalog)
        assertFalse(row("bot:1:abc").isRestrictedCatalog)
    }

    @Test fun restrictedTracksCannotBeShared() {
        val copy = row("ym:123:9", path = "/cache/music-yandex/a.mp3", sizeBytes = 10L)
        assertFalse(canShareTrack(copy, originUnprotected = true))
    }

    @Test fun yandexCopyIsMeasuredUnderItsRowKey() {
        val copy = row("ym:123:9", path = "/cache/music-yandex/a.mp3")
        assertTrue(SilenceAnalyzer.isMeasurable(copy))
        assertEquals("ym:123:9", SilenceAnalyzer.cacheKey(copy))
    }

    @Test fun rowWithoutFileIsNotMeasurable() {
        assertFalse(SilenceAnalyzer.isMeasurable(row("ym:123:9")))
    }
}
