package com.beykus.beygram

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PersistentPhoneTabsUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun coveredBackdropBlocksPointerInputAndHidesSemanticsWithoutDisposingContent() {
        var covered by mutableStateOf(false)
        var clicks = 0
        var mounts = 0
        var disposes = 0

        rule.setContent {
            PersistentPhoneTabsBackdrop(covered = covered) {
                DisposableEffect(Unit) {
                    mounts++
                    onDispose { disposes++ }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("hidden_tab_item")
                        .clickable { clicks++ }
                ) {
                    Text("Chats", Modifier.size(100.dp))
                }
            }
        }

        // Uncovered: semantics visible and clicks work.
        rule.onNodeWithTag("hidden_tab_item").assertExists().performClick()
        assertEquals(1, clicks)
        assertEquals(1, mounts)
        assertEquals(0, disposes)

        // Covered (e.g. during partial slide or predictive Back): semantics hidden and pointer taps swallowed.
        covered = true
        rule.waitForIdle()
        rule.onNodeWithTag("hidden_tab_item").assertDoesNotExist()
        rule.onRoot().performTouchInput { click(Offset(50f, 50f)) }
        rule.waitForIdle()
        assertEquals("Covered backdrop must swallow taps", 1, clicks)
        assertEquals("Content must stay mounted while covered", 1, mounts)
        assertEquals("Content must not be disposed while covered", 0, disposes)

        // Uncovered again: content immediately interactive without remounting.
        covered = false
        rule.waitForIdle()
        rule.onNodeWithTag("hidden_tab_item").assertExists().performClick()
        assertEquals(2, clicks)
        assertEquals("Returning from covered state must not remount content", 1, mounts)
        assertEquals(0, disposes)
    }
}
