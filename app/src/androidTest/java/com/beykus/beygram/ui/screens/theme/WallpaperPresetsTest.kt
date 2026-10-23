package com.beykus.beygram.ui.screens.theme

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperPresetsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun builtInPresetsCanBeSelectedFromAnAccessible48dpOrLargerTile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selected: ChatWallpaper = ChatWallpaper.None
        composeRule.setContent {
            BeyGramTheme {
                AppearanceWallpapersPage(
                    draft = AppearanceConfig(wallpaper = ChatWallpaper.Preset("mint-air")),
                    systemDark = false,
                    onDraftChange = { selected = it.wallpaper },
                    onOpenPreview = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.wallpaper_preset_lavender_flow))
            .performScrollTo()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle {
            assertEquals(
                ChatWallpaper.Preset("lavender-flow", ChatWallpaper.DEFAULT_PATTERN_INTENSITY),
                selected
            )
        }
    }
}
