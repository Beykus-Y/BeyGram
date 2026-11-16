package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareWaveTest {
    private fun item(
        key: String = "tg|acc|chat:1:message:2|Audio|7",
        kind: MediaKind = MediaKind.Audio,
        secret: Boolean = false,
    ) = PlaybackItem(
        key = key,
        kind = kind,
        path = "/music.flac",
        title = "Track",
        durationSeconds = 180,
        secret = secret,
    )

    @Test
    fun canShareWaveAllowsProviderTrackWithoutOriginUnprotected() {
        assertTrue(canShareWave(item(key = "${YANDEX_TRACK_KEY_PREFIX}123456"), originUnprotected = false, isProviderTrack = true))
    }

    @Test
    fun canShareWaveRefusesNonProviderTrackWithProtectedOrigin() {
        assertFalse(canShareWave(item(), originUnprotected = false, isProviderTrack = false))
    }

    @Test
    fun canShareWaveAllowsUnprotectedNonProviderTrack() {
        assertTrue(canShareWave(item(), originUnprotected = true, isProviderTrack = false))
    }

    @Test
    fun canShareWaveRefusesProviderTrackWhenSecret() {
        assertFalse(
            canShareWave(item(key = "${YANDEX_TRACK_KEY_PREFIX}123456", secret = true), originUnprotected = true, isProviderTrack = true),
        )
    }
}
