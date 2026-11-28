package com.beykus.beygram.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A real secondary screen on the shared chrome (#1358 R2). It must keep its title and back action in every style, and
 * it must gain the glass pane only in Liquid Glass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class SecondaryScreenGlassUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val title: String = app.getString(R.string.privacy_title)
    private val back: String = app.getString(R.string.common_back)

    private fun screen(style: DesignStyle, quality: GlassQuality = GlassQuality.High) {
        rule.setContent {
            BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = style, glassQuality = quality))) {
                PrivacySettingsScreen(
                    loadPrivacy = { Result.success(emptyList()) },
                    savePrivacy = { _, _ -> Result.success(Unit) },
                    loadContacts = { Result.success(emptyList()) },
                    stealthActive = false,
                    onStealth = {},
                    onBlockedSenders = {},
                    onBack = {}
                )
            }
        }
    }

    @Test
    fun thePrivacyScreenKeepsItsToolbarInLiquidGlass() {
        screen(DesignStyle.LiquidGlass)
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithContentDescription(back, substring = true).assertExists()
        rule.onAllNodesWithTag("screenChromeTopPane").assertCountEquals(1)
    }

    @Test
    fun theSameScreenHasNoGlassPaneInTheOtherThemes() {
        screen(DesignStyle.Material3)
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithContentDescription(back, substring = true).assertExists()
        rule.onAllNodesWithTag("screenChromeTopPane").assertCountEquals(0)
    }

    @Test
    fun theScreenAlsoRendersInTheOtherThemes() {
        screen(DesignStyle.Default)
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithContentDescription(back, substring = true).assertExists()
    }

    @Test
    fun theMaterial2ScreenKeepsItsElevatedBar() {
        screen(DesignStyle.Material2)
        rule.onNodeWithText(title).assertExists()
        rule.onAllNodesWithTag("screenChromeTopPane").assertCountEquals(0)
    }

    @Test
    fun theSignatureScreenKeepsItsHairlineBar() {
        screen(DesignStyle.Signature)
        rule.onNodeWithText(title).assertExists()
        rule.onAllNodesWithTag("screenChromeTopPane").assertCountEquals(0)
    }
}