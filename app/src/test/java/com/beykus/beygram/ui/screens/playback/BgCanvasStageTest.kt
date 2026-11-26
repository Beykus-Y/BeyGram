package com.beykus.beygram.ui.screens.playback

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.data.canvas.AppCanvasRepository
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasAsset
import com.beykus.beygram.data.canvas.CanvasLoadState
import com.beykus.beygram.data.canvas.CanvasSettingsStore
import com.beykus.beygram.data.canvas.CanvasUnavailableReason
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.ui.motion.LocalMotionSettings
import com.beykus.beygram.ui.motion.MotionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BgCanvasStageTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private class TestLifecycleOwner(initial: Lifecycle.State = Lifecycle.State.RESUMED) : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = initial }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun yandexItem(key: String = "ym:101") = PlaybackItem(
        key = key,
        kind = MediaKind.Audio,
        path = "",
        title = "Canvas Track",
        durationSeconds = 180,
        accountId = "acc1",
    )

    @Before
    fun resetSettingsAndPowerSave() {
        CanvasSettingsStore.setMode(context, BgCanvasMode.Off)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        Shadows.shadowOf(pm).setIsPowerSaveMode(false)
    }

    @Test
    fun policyHelperRejectsDisabledLifecycleReducedMotionAndPowerSave() {
        assertTrue(
            isBgCanvasAllowed(
                active = true,
                resumed = true,
                mode = BgCanvasMode.Card,
                reducedMotion = false,
                motionEnabled = true,
                mediaMotionDisabled = false,
                powerSave = false,
            )
        )
        assertFalse(isBgCanvasAllowed(false, true, BgCanvasMode.Card, false, true, false, false))
        assertFalse(isBgCanvasAllowed(true, false, BgCanvasMode.Card, false, true, false, false))
        assertFalse(isBgCanvasAllowed(true, true, BgCanvasMode.Off, false, true, false, false))
        assertFalse(isBgCanvasAllowed(true, true, BgCanvasMode.Fullscreen, true, true, false, false))
        assertFalse(isBgCanvasAllowed(true, true, BgCanvasMode.Card, false, false, false, false))
        assertFalse(isBgCanvasAllowed(true, true, BgCanvasMode.Card, false, true, true, false))
        assertFalse(isBgCanvasAllowed(true, true, BgCanvasMode.Card, false, true, false, true))
    }

    @Test
    fun reactsToLivePowerSaveToggleAndLifecyclePauseResume() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.let { caps ->
            Shadows.shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        CanvasSettingsStore.setMode(context, BgCanvasMode.Card)
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        var active by mutableStateOf(true)
        var currentItem by mutableStateOf(yandexItem("ym:101"))
        var observedMode by mutableStateOf(BgCanvasMode.Off)
        var observedState by mutableStateOf<CanvasLoadState>(CanvasLoadState.Idle)

        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalMotionSettings provides MotionSettings(disabledCategories = emptySet()),
            ) {
                val (mode, state) = rememberBgCanvasState(item = currentItem, active = active)
                observedMode = mode
                observedState = state
            }
        }

        compose.waitUntil(timeoutMillis = 3_000) {
            observedState is CanvasLoadState.Unavailable
        }
        assertEquals(BgCanvasMode.Card, observedMode)
        assertEquals(CanvasLoadState.Unavailable(CanvasUnavailableReason.NotAvailable), observedState)

        // Toggle power save mode ON via system broadcast -> immediately becomes Idle
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        Shadows.shadowOf(pm).setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals(CanvasLoadState.Idle, observedState)

        // Toggle power save mode OFF via broadcast -> resumes observing
        Shadows.shadowOf(pm).setIsPowerSaveMode(false)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 3_000) {
            observedState is CanvasLoadState.Unavailable
        }

        // Lifecycle ON_PAUSE -> Idle; ON_RESUME -> active again
        compose.runOnUiThread { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals(CanvasLoadState.Idle, observedState)

        compose.runOnUiThread { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 3_000) {
            observedState is CanvasLoadState.Unavailable
        }

        // Switching to Queue/Lyrics (active = false) -> Idle
        active = false
        compose.waitForIdle()
        assertEquals(CanvasLoadState.Idle, observedState)

        // Re-activating and switching track resets cleanly
        active = true
        currentItem = yandexItem("ym:202")
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 3_000) {
            observedState is CanvasLoadState.Unavailable
        }
    }

    @Test
    fun overlayRecordsMissingFileErrorIntoRedactedDiagnostics() {
        val missing = File(context.cacheDir, "does-not-exist-canvas.mp4")
        val ready = CanvasLoadState.Ready(
            CanvasAsset(
                playbackKey = "ym:999",
                localFilePath = missing.absolutePath,
                videoId = "vid999",
                sizeBytes = 100L,
            )
        )

        compose.setContent {
            BgCanvasOverlay(state = ready, playing = true)
        }
        compose.waitForIdle()

        val diag = AppCanvasRepository.get(context).diagnosticsSummary()
        assertNotNull(diag)
        assertTrue(diag!!.contains("playErr="))
        assertTrue(diag.contains("lastErr=missing_file"))
        assertFalse(diag.contains(missing.absolutePath))
    }
}
