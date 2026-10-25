package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.LightTokens
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabItemTest {
    @get:Rule val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tabKeepsItsRoleSelectionTouchTargetAndHapticFeedback() {
        val opened = mutableListOf<MainTab>()
        val haptics = RecordingHapticFeedback()
        composeRule.setContent {
            BeyGramTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    MainTabItem(MainTab.Chats, selected = true, onSelect = { opened += MainTab.Chats })
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.tabs_chats))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(MainTab.Chats), opened)
            assertEquals(listOf(HapticFeedbackType.ContextClick), haptics.types)
        }
    }

    @Test
    fun pressShowsTheCapsuleOnlyWhileTheFingerIsDown() {
        composeRule.setContent {
            BeyGramTheme {
                MainTabItem(MainTab.Settings, selected = false, onSelect = {})
            }
        }
        val tab = composeRule.onNodeWithText(context.getString(R.string.settings_title))
        tab.performTouchInput { down(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainTabPressCapsuleTag, useUnmergedTree = true).assertExists()

        tab.performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainTabPressCapsuleTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun pressedFeedbackStaysInsideThePillInsteadOfFillingTheTouchTarget() {
        val surface = LightTokens.toPalette().surface
        composeRule.setContent {
            BeyGramTheme(darkTheme = false, tokens = LightTokens) {
                Box(Modifier.size(width = 120.dp, height = 80.dp).background(surface)) {
                    MainTabItem(MainTab.Settings, selected = false, onSelect = {})
                }
            }
        }
        val tab = composeRule.onNodeWithText(context.getString(R.string.settings_title))
        tab.performTouchInput { down(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainTabPressCapsuleTag, useUnmergedTree = true).assertExists()

        val pixels = tab.captureToImage().toPixelMap()
        // The ripple used to tint the whole rectangular cell, corners included.
        assertTrue(
            "corner of the touch target must stay on the bar background",
            pixels[1, 1].maxChannelDifference(surface) <= 0.02f
        )
        // The capsule is drawn over the top of the pill, above the icon.
        val capsuleY = with(composeRule.density) { 2.dp.roundToPx() }
        assertTrue(
            "pressed capsule must be visible inside the pill",
            pixels[pixels.width / 2, capsuleY].maxChannelDifference(surface) > 0.03f
        )

        tab.performTouchInput { up() }
    }

    @Test
    fun previewIsInertAndNeverShowsALingeringHighlight() {
        composeRule.setContent {
            BeyGramTheme {
                MainTabItem(MainTab.Chats, selected = true, onSelect = {}, interactive = false)
            }
        }
        val preview = composeRule.onNodeWithText(context.getString(R.string.tabs_chats))
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))

        preview.performTouchInput { down(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainTabPressCapsuleTag, useUnmergedTree = true).assertDoesNotExist()
        preview.performTouchInput { up() }
    }

    private class RecordingHapticFeedback : HapticFeedback {
        val types = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            types += hapticFeedbackType
        }
    }
}

private fun Color.maxChannelDifference(other: Color): Float =
    maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))
