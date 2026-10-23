package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun fullPreviewShowsIncomingOutgoingMediaServiceAndComposer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            BeyGramTheme {
                Box(Modifier.size(400.dp, 760.dp)) {
                    AppearanceChatPreview(
                        config = AppearanceConfig(
                            wallpaper = ChatWallpaper.Preset("lavender-flow"),
                            messageAnimations = MessageAnimationConfig(
                                linkedDirections = true,
                                shared = MessageAnimationDirection(MessageAnimationPreset.Flow)
                            )
                        ),
                        compact = false,
                        animate = false,
                        replayKey = 0,
                        systemDark = false
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.theme_preview_incoming)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.theme_preview_outgoing)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.theme_preview_media_caption)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.theme_preview_service)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.wallpaper_preview_input)).assertExists()
    }

    @Test fun composerLaunchPreviewComposesWithoutASendAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            BeyGramTheme {
                Box(Modifier.size(400.dp, 300.dp)) {
                    AppearanceChatPreview(
                        config = AppearanceConfig(
                            messageAnimations = MessageAnimationConfig(
                                linkedDirections = true,
                                shared = MessageAnimationDirection(MessageAnimationPreset.ComposerLaunch)
                            )
                        ),
                        compact = true,
                        animate = false,
                        replayKey = 0,
                        systemDark = false
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.theme_preview_outgoing)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.wallpaper_preview_input)).assertExists()
    }
}
