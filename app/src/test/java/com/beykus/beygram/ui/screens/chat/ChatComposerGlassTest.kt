package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class ChatComposerGlassTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun sendControlAndTypingRowRenderAcrossAllFiveStylesAndSharedGlass() {
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                Column {
                    DesignStyle.entries.forEach { style ->
                        BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = style))) {
                            SendControlFace(busy = false, canSend = true, editing = false)
                            SendControlFace(busy = false, canSend = false, editing = true)
                            SendControlFace(busy = true, canSend = true, editing = false)
                            TypingRow(listOf("Alice-${style.name}"))
                        }
                    }
                    CompositionLocalProvider(LocalComposerOnGlassPlane provides true) {
                        BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = DesignStyle.LiquidGlass))) {
                            SendControlFace(busy = false, canSend = true, editing = false)
                            TypingRow(listOf("Bob-Shared"))
                            AttachmentMenu(allowsPolls = true, enabled = true, onAction = {})
                        }
                    }
                }
            }
        }
        DesignStyle.entries.forEach { style ->
            rule.onNodeWithText("Alice-${style.name}", substring = true).assertExists()
        }
        rule.onNodeWithText("Bob-Shared", substring = true).assertExists()
    }
}
