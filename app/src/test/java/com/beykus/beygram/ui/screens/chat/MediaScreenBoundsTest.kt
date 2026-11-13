package com.beykus.beygram.ui.screens.chat

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Measures real separate Compose windows; numeric transform tests alone cannot catch window-origin mismatches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MediaScreenBoundsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun portraitStatusAndNavigationInsets() = checkWindows(false, false)
    @Test fun landscapeWithKeyboardReserve() = checkWindows(true, true)

    private fun checkWindows(landscape: Boolean, keyboard: Boolean) {
        val measured = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        fun check(name: String, coordinates: LayoutCoordinates, view: View) {
            val screen = IntArray(2); val window = IntArray(2)
            view.getLocationOnScreen(screen); view.getLocationInWindow(window)
            val actual = coordinates.mediaScreenBounds()
            assertNotNull(actual)
            val local = coordinates.boundsInWindow()
            assertEquals(local.left + screen[0] - window[0], actual!!.left, 1f)
            assertEquals(local.top + screen[1] - window[1], actual.top, 1f)
            measured += name
        }
        compose.setContent {
            val activityView = LocalView.current
            Box(Modifier.size(if (landscape) 640.dp else 360.dp, if (landscape) 360.dp else 640.dp)
                .padding(top = 24.dp, bottom = if (keyboard) 200.dp else 48.dp)) {
                Box(Modifier.size(100.dp, 80.dp).onGloballyPositioned { check("activity", it, activityView) })
            }
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                val dialogView = LocalView.current
                Box(Modifier.size(if (landscape) 600.dp else 320.dp, if (landscape) 300.dp else 580.dp)
                    .padding(top = 48.dp, bottom = if (keyboard) 200.dp else 24.dp)) {
                    Box(Modifier.size(200.dp, 120.dp).onGloballyPositioned { check("dialog", it, dialogView) })
                }
            }
        }
        compose.waitUntil(10_000) { measured.containsAll(listOf("activity", "dialog")) }
    }
}
