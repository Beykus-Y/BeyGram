package com.beykus.beygram.playback

import android.app.Application
import android.content.Context
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasSettingsStore
import com.beykus.beygram.data.lyrics.LyricsSettingsStore
import com.beykus.beygram.media.bgbeat.BgBeatSettingsStore
import com.beykus.beygram.media.bgmix.BgMixSettingsStore
import com.beykus.beygram.media.bgrhythm.BgRhythmMode
import com.beykus.beygram.media.bgrhythm.BgRhythmSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicDefaultsSettingsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("bgmix_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(BgBeatSettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("track_transition_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(BgRhythmSettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(CanvasSettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(LyricsSettingsStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        BgMixSettingsStore.resetForTests()
        TrackTransitionSettings.resetForTests()
        BgRhythmSettingsStore.resetForTests()
        CanvasSettingsStore.resetForTests()
        LyricsSettingsStore.resetForTests()
    }

    @Test
    fun freshDefaultsEnableReleaseMusicFeaturesWhileKeepingExternalLyricsOptIn() {
        BgMixSettingsStore.load(context)
        TrackTransitionSettings.load(context)
        val bgBeat = BgBeatSettingsStore.getInstance(context)
        bgBeat.setEnabled(BgBeatSettingsStore.DEFAULT_ENABLED)

        assertTrue(BgMixSettingsStore.enabled.value)
        assertTrue(bgBeat.enabled.value)
        assertTrue(TrackTransitionSettings.crossfade.value)
        assertTrue(TrackTransitionSettings.skipSilence.value)
        assertEquals(TrackTransitionSettings.DEFAULT_CROSSFADE_SECONDS, TrackTransitionSettings.crossfadeSeconds.value)
        assertEquals(BgRhythmMode.Calm, BgRhythmSettingsStore.current(context))
        assertEquals(BgCanvasMode.Card, CanvasSettingsStore.current(context).mode)
        assertTrue(CanvasSettingsStore.current(context).respectDataSaver)

        val lyrics = LyricsSettingsStore.current(context)
        assertTrue(lyrics.embeddedTagsEnabled)
        assertTrue(lyrics.yandexEnabled)
        // Separate consent for external metadata transmission (including LRCLIB) remains opt-in
        assertFalse(lyrics.externalLookupEnabled)
    }

    @Test
    fun explicitUserOptOutSurvivesReload() {
        BgMixSettingsStore.load(context)
        BgMixSettingsStore.setEnabled(context, false)
        TrackTransitionSettings.load(context)
        TrackTransitionSettings.setCrossfade(context, false)
        TrackTransitionSettings.setSkipSilence(context, false)
        TrackTransitionSettings.setCrossfadeSeconds(context, 7)
        BgRhythmSettingsStore.setMode(context, BgRhythmMode.Off)
        CanvasSettingsStore.update(context) { it.copy(mode = BgCanvasMode.Off, respectDataSaver = false) }
        LyricsSettingsStore.update(context) {
            it.copy(
                externalLookupEnabled = true,
                embeddedTagsEnabled = false,
                yandexEnabled = false,
                lrcLibEnabled = false,
            )
        }

        BgMixSettingsStore.resetForTests()
        TrackTransitionSettings.resetForTests()
        BgRhythmSettingsStore.resetForTests()
        CanvasSettingsStore.resetForTests()
        LyricsSettingsStore.resetForTests()

        BgMixSettingsStore.load(context)
        TrackTransitionSettings.load(context)
        val canvas = CanvasSettingsStore.current(context)
        val lyrics = LyricsSettingsStore.current(context)

        assertFalse(BgMixSettingsStore.enabled.value)
        BgBeatSettingsStore.getInstance(context).setEnabled(false)
        assertFalse(BgBeatSettingsStore.getInstance(context).enabled.value)
        assertFalse(TrackTransitionSettings.crossfade.value)
        assertFalse(TrackTransitionSettings.skipSilence.value)
        assertEquals(7, TrackTransitionSettings.crossfadeSeconds.value)
        assertEquals(BgRhythmMode.Off, BgRhythmSettingsStore.current(context))
        assertEquals(BgCanvasMode.Off, canvas.mode)
        assertFalse(canvas.respectDataSaver)
        assertTrue(lyrics.externalLookupEnabled)
        assertFalse(lyrics.embeddedTagsEnabled)
        assertFalse(lyrics.yandexEnabled)
        assertFalse(lyrics.lrcLibEnabled)
    }
}
