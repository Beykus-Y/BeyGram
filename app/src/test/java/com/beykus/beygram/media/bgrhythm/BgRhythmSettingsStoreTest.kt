package com.beykus.beygram.media.bgrhythm

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BgRhythmSettingsStoreTest {

    @Test
    fun defaultsToOffAndPersistsModeChanges() {
        val context = RuntimeEnvironment.getApplication()
        BgRhythmSettingsStore.setMode(context, BgRhythmMode.Off)
        assertEquals(BgRhythmMode.Off, BgRhythmSettingsStore.current(context))
        assertEquals(BgRhythmMode.Off, BgRhythmSettingsStore.flow(context).value)

        BgRhythmSettingsStore.setMode(context, BgRhythmMode.Dynamic)
        assertEquals(BgRhythmMode.Dynamic, BgRhythmSettingsStore.current(context))
        assertEquals(BgRhythmMode.Dynamic, BgRhythmSettingsStore.flow(context).value)

        BgRhythmSettingsStore.setMode(context, BgRhythmMode.Calm)
        assertEquals(BgRhythmMode.Calm, BgRhythmSettingsStore.current(context))
        BgRhythmSettingsStore.setMode(context, BgRhythmMode.Off)
    }
}
