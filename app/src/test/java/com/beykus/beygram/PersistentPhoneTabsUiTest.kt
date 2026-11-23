package com.beykus.beygram

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.beykus.beygram.navigation.AppRoute
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        rule.onNodeWithTag("hidden_tab_item").assertExists().performClick()
        assertEquals(1, clicks)
        assertEquals(1, mounts)
        assertEquals(0, disposes)

        covered = true
        rule.waitForIdle()
        rule.onNodeWithTag("hidden_tab_item").assertDoesNotExist()
        rule.onRoot().performTouchInput { click(Offset(50f, 50f)) }
        rule.waitForIdle()
        assertEquals("Covered backdrop must swallow taps", 1, clicks)
        assertEquals("Content must stay mounted while covered", 1, mounts)
        assertEquals("Content must not be disposed while covered", 0, disposes)

        covered = false
        rule.waitForIdle()
        rule.onNodeWithTag("hidden_tab_item").assertExists().performClick()
        assertEquals(2, clicks)
        assertEquals("Returning from covered state must not remount content", 1, mounts)
        assertEquals(0, disposes)
    }

    @Test
    fun modalDialogInsideCoveredBackdropIsDismissedAndStaysClosedOnReturn() {
        var covered by mutableStateOf(false)
        var dialogOpen by mutableStateOf(true)
        var mounts = 0

        rule.setContent {
            BeyGramTheme {
                PersistentPhoneTabsBackdrop(covered = covered) {
                    DisposableEffect(Unit) {
                        mounts++
                        onDispose {}
                    }
                    if (dialogOpen) {
                        BeyGramDialog(
                            onDismissRequest = { dialogOpen = false },
                            title = "Read all confirmation",
                            message = "Mark every chat read?",
                            confirmText = "Confirm",
                            onConfirm = { dialogOpen = false }
                        )
                    }
                    Text("Chats list", Modifier.testTag("chats_list_marker"))
                }
            }
        }

        rule.onNodeWithText("Read all confirmation").assertExists()
        assertTrue(dialogOpen)

        // Overlay opens (e.g. notification/deep link to Chat while dialog was open).
        covered = true
        rule.waitForIdle()
        assertFalse("Modal dialog inside covered tab must be dismissed", dialogOpen)
        rule.onNodeWithText("Read all confirmation").assertDoesNotExist()
        assertEquals("Underlying tab must stay mounted", 1, mounts)

        // Returning to Chats must not resurrect the stale dialog and must not remount.
        covered = false
        rule.waitForIdle()
        assertFalse(dialogOpen)
        rule.onNodeWithText("Read all confirmation").assertDoesNotExist()
        rule.onNodeWithTag("chats_list_marker").assertExists()
        assertEquals(1, mounts)
    }

    @Test
    fun hoistedVisitedStateSurvivesTwoPaneToggleAndResetsOnAccountSwitch() {
        var accountId by mutableStateOf("acc1")
        var twoPane by mutableStateOf(false)
        var route by mutableStateOf(AppRoute.Contacts)
        var observedKeepMounted = false
        var observedTab = MainTab.Chats

        rule.setContent {
            var phoneTabsVisited by rememberSaveable(accountId) { mutableStateOf(false) }
            var lastMountedMainTabName by rememberSaveable(accountId) { mutableStateOf(MainTab.Chats.name) }

            key(accountId, twoPane) {
                val frame = RouteTransitionFrame(route, twoPane)
                if (usesSharedPhoneTabs(frame)) {
                    phoneTabsVisited = true
                    lastMountedMainTabName = MainTab.entries.first { it.route == route }.name
                }
                val lastMountedMainTab = MainTab.entries.firstOrNull { it.name == lastMountedMainTabName } ?: MainTab.Chats
                observedKeepMounted = shouldKeepPhoneTabsMounted(frame, phoneTabsVisited)
                observedTab = lastMountedMainTab
            }
        }

        // Visited Contacts in single-pane, then entered Chat.
        rule.waitForIdle()
        assertTrue(observedKeepMounted)
        assertEquals(MainTab.Contacts, observedTab)

        route = AppRoute.Chat
        rule.waitForIdle()
        assertTrue(observedKeepMounted)
        assertEquals(MainTab.Contacts, observedTab)

        // Switch to twoPane (e.g. tablet/unfold) -> back to single-pane while still in Chat.
        twoPane = true
        rule.waitForIdle()
        assertFalse(observedKeepMounted)

        twoPane = false
        rule.waitForIdle()
        assertTrue("Visited flag must survive single-pane -> twoPane -> single-pane", observedKeepMounted)
        assertEquals("Selected root tab must survive twoPane toggle", MainTab.Contacts, observedTab)

        // Switching accounts resets visited state cleanly (cold Chat on new account must not eagerly mount tabs).
        accountId = "acc2"
        rule.waitForIdle()
        assertFalse("New account in Chat must not mount tabs before visiting root", observedKeepMounted)
        assertEquals(MainTab.Chats, observedTab)
    }

    @Test
    fun popoverInsideCoveredBackdropIsDismissedAndStaysClosedOnReturn() {
        var covered by mutableStateOf(false)
        var menuOpen by mutableStateOf(true)
        var mounts = 0

        rule.setContent {
            BeyGramTheme {
                PersistentPhoneTabsBackdrop(covered = covered) {
                    DisposableEffect(Unit) {
                        mounts++
                        onDispose {}
                    }
                    Box(Modifier.size(120.dp)) {
                        BeyGramPopover(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            BeyGramPopoverItem(
                                text = "Overflow action",
                                onClick = { menuOpen = false }
                            )
                        }
                    }
                }
            }
        }

        rule.onNodeWithText("Overflow action").assertExists()
        assertTrue(menuOpen)

        // Deep link / push covers the root tab while its overflow or contact popup is open.
        covered = true
        rule.waitForIdle()
        assertFalse("Popover inside covered tab must be dismissed", menuOpen)
        rule.onNodeWithText("Overflow action").assertDoesNotExist()
        assertEquals("Underlying tab must stay mounted", 1, mounts)

        // Returning to root tab must keep the popup dismissed and avoid remounting.
        covered = false
        rule.waitForIdle()
        assertFalse(menuOpen)
        rule.onNodeWithText("Overflow action").assertDoesNotExist()
        assertEquals(1, mounts)
    }

    @Test
    fun coveredFolderManagerResetsAndDoesNotInterceptBack() {
        var covered by mutableStateOf(false)
        var managingFolders by mutableStateOf(true)
        var backgroundBackCalls = 0
        var foregroundBackCalls = 0
        val dispatcher = androidx.activity.OnBackPressedDispatcher()

        rule.setContent {
            val owner = object : androidx.activity.OnBackPressedDispatcherOwner {
                override val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                override val onBackPressedDispatcher = dispatcher
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides owner
            ) {
                PersistentPhoneTabsBackdrop(covered = covered) {
                    val isCovered = LocalMainTabsCovered.current
                    androidx.compose.runtime.LaunchedEffect(isCovered) {
                        if (isCovered) managingFolders = false
                    }
                    if (managingFolders && !isCovered) {
                        androidx.activity.compose.BackHandler(enabled = !isCovered) {
                            backgroundBackCalls++
                            managingFolders = false
                        }
                    }
                }
                if (covered) {
                    androidx.activity.compose.BackHandler(enabled = true) {
                        foregroundBackCalls++
                        covered = false
                    }
                }
            }
        }

        rule.waitForIdle()
        assertTrue(managingFolders)

        // External intent opens Chat on top while folder manager was active.
        covered = true
        rule.waitForIdle()
        assertFalse("Folder manager must reset when covered", managingFolders)

        // Pressing Back must hit the foreground Chat BackHandler, never the background folder manager.
        rule.runOnIdle { dispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals("Foreground BackHandler must receive Back", 1, foregroundBackCalls)
        assertEquals("Covered background BackHandler must not intercept Back", 0, backgroundBackCalls)
        assertFalse("Returning from Chat must land on Chats list, not stale folder manager", managingFolders)
    }
}
