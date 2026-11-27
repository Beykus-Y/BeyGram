package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class VideoNoteSeekGestureTest {
    @get:Rule val compose = createComposeRule()
    private val seeks = mutableListOf<Float>()
    private val previews = mutableListOf<Float?>()
    private var clicks = 0

    @Test fun edgeTapSeeksWhileTheCenterKeepsPlayPause() {
        render()
        compose.onNodeWithTag("note").performTouchInput {
            click(center)
            click(Offset(width - 8f, center.y))
        }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(0.25f, seeks.single(), 0.001f)
        }
    }

    @Test fun finishedDragSeeksOnceAndCancelOnlyClearsThePreview() {
        render()
        val note = compose.onNodeWithTag("note")
        note.performTouchInput {
            down(Offset(center.x, 8f))
            moveTo(Offset(width - 8f, center.y))
            moveTo(Offset(center.x, height - 8f))
            up()
        }
        note.performTouchInput {
            down(Offset(center.x, 8f))
            moveTo(Offset(width - 8f, center.y))
            cancel()
        }
        compose.runOnIdle {
            assertEquals(0.5f, seeks.single(), 0.001f)
            assertEquals(0, clicks)
            assertTrue(previews.any { it != null })
            assertNull(previews.last())
        }
    }

    private fun render() {
        compose.setContent {
            Box(
                Modifier.size(180.dp).testTag("note")
                    .videoNoteSeekGesture("note", onPreview = { previews += it }, onSeek = { seeks += it })
                    .combinedClickable(onClick = { clicks++ })
            )
        }
    }
}
