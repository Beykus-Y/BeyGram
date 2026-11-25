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
        BgMixSettingsStore.resetForTests()
        TrackTransitionSettings.resetForTests()
    }

    @Test
    fun freshDefaultsEnableBgMixBgBeatCrossfadeAndSkipSilenceWhileKeepingExperimentalOptIn() {
        BgMixSettingsStore.load(context)
        TrackTransitionSettings.load(context)
        val bgBeat = BgBeatSettingsStore.getInstance(context)
        bgBeat.setEnabled(BgBeatSettingsStore.DEFAULT_ENABLED)

        assertTrue(BgMixSettingsStore.enabled.value)
        assertTrue(bgBeat.enabled.value)
        assertTrue(TrackTransitionSettings.crossfade.value)
        assertTrue(TrackTransitionSettings.skipSilence.value)
        assertEquals(TrackTransitionSettings.DEFAULT_CROSSFADE_SECONDS, TrackTransitionSettings.crossfadeSeconds.value)

        // Opt-in / experimental defaults remain off
        assertFalse(LyricsSettingsStore.current(context).externalLookupEnabled)
        assertEquals(BgCanvasMode.Off, CanvasSettingsStore.current(context).mode)
        assertEquals(BgRhythmMode.Off, BgRhythmSettingsStore.current(context))
    }

    @Test
    fun explicitUserOptOutSurvivesReload() {
        BgMixSettingsStore.load(context)
        BgMixSettingsStore.setEnabled(context, false)
        TrackTransitionSettings.load(context)
        TrackTransitionSettings.setCrossfade(context, false)
        TrackTransitionSettings.setSkipSilence(context, false)
        TrackTransitionSettings.setCrossfadeSeconds(context, 7)

        BgMixSettingsStore.resetForTests()
        TrackTransitionSettings.resetForTests()

        BgMixSettingsStore.load(context)
        TrackTransitionSettings.load(context)

        assertFalse(BgMixSettingsStore.enabled.value)
        assertFalse(TrackTransitionSettings.crossfade.value)
        assertFalse(TrackTransitionSettings.skipSilence.value)
        assertEquals(7, TrackTransitionSettings.crossfadeSeconds.value)
    }
}
