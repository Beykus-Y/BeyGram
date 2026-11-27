package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class ChatTimelineGlassTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun jumpButtonsAndBadgesRenderAcrossAllFiveDesignStyles() {
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                Column {
                    DesignStyle.entries.forEach { style ->
                        BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = style))) {
                            JumpToLatestButton(unread = 5, onClick = {})
                            ChatFloatingBadgeButton(
                                icon = Icons.Rounded.AlternateEmail,
                                count = 3,
                                label = "mention-${style.name}",
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
        DesignStyle.entries.forEach { style ->
            rule.onNodeWithContentDescription("mention-${style.name}").assertIsDisplayed()
        }
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().let {
            org.junit.Assert.assertEquals(DesignStyle.entries.size * 2, it.size)
        }
    }
}
