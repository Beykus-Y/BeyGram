package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The attachment menu of #1358: one compact surface, every action reachable with its caption, and the existing
 * availability rules still deciding whether a tile can be tapped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class AttachmentMenuUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun label(action: AttachmentAction): String =
        ApplicationProvider.getApplicationContext<Application>().getString(action.labelRes())

    private fun menu(
        design: DesignStyle = DesignStyle.LiquidGlass,
        allowsPolls: Boolean = true,
        enabled: Boolean = true,
        chatObjectsEnabled: Boolean = true,
        onAction: (AttachmentAction) -> Unit = {}
    ) {
        rule.setContent {
            BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = design))) {
                AttachmentMenu(
                    allowsPolls = allowsPolls,
                    enabled = enabled,
                    chatObjectsEnabled = chatObjectsEnabled,
                    onAction = onAction
                )
            }
        }
    }

    @Test
    fun everyActionHasItsCaptionInLiquidGlass() {
        menu()
        AttachmentAction.entries.forEach { action ->
            rule.onNodeWithText(label(action), substring = true).assertExists()
        }
    }

    @Test
    fun aTileReportsTheActionItStandsFor() {
        val picked = mutableListOf<AttachmentAction>()
        menu(onAction = { picked += it })
        rule.onNodeWithText(label(AttachmentAction.Camera), substring = true).performClick()
        rule.onNodeWithText(label(AttachmentAction.Dice), substring = true).performClick()
        rule.runOnIdle {
            assert(picked == listOf(AttachmentAction.Camera, AttachmentAction.Dice)) { "tiles must keep their action" }
        }
    }

    @Test
    fun aDisabledMenuDoesNotSendAnything() {
        val picked = mutableListOf<AttachmentAction>()
        menu(enabled = false, onAction = { picked += it })
        rule.onNodeWithText(label(AttachmentAction.Camera), substring = true).performClick()
        rule.runOnIdle { assert(picked.isEmpty()) { "a disabled menu must not start a picker" } }
    }

    @Test
    fun stagedFilesRefuseTheActionsThatWouldReplaceThem() {
        val picked = mutableListOf<AttachmentAction>()
        menu(chatObjectsEnabled = false, onAction = { picked += it })
        rule.onNodeWithText(label(AttachmentAction.Location), substring = true).performClick()
        rule.runOnIdle { assert(picked.none { it.isChatObject }) { "chat objects must be refused while files are staged" } }
        rule.onNodeWithText(label(AttachmentAction.File), substring = true).performClick()
        rule.runOnIdle { assert(picked == listOf(AttachmentAction.File)) { "plain media must stay available" } }
    }

    @Test
    fun musicDisappearsWhereTelegramRejectsIt() {
        rule.setContent {
            BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = DesignStyle.LiquidGlass))) {
                AttachmentMenu(allowsPolls = true, allowsMusic = false, enabled = true, onAction = {})
            }
        }
        rule.onNodeWithText(label(AttachmentAction.Music), substring = true).assertDoesNotExist()
        rule.onNodeWithText(label(AttachmentAction.File), substring = true).assertExists()
    }

    @Test
    fun theMenuAlsoRendersInTheThemesWithoutGlass() {
        menu(design = DesignStyle.Material3)
        rule.onNodeWithText(label(AttachmentAction.Camera), substring = true).assertExists()
        rule.onNodeWithText(label(AttachmentAction.Poll), substring = true).assertExists()
    }
}