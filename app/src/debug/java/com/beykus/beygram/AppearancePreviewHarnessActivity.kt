package com.beykus.beygram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.beykus.beygram.data.appearance.AppearanceSettingsStore
import kotlinx.coroutines.launch
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import com.beykus.beygram.ui.screens.chat.LocalBubbleMetrics
import com.beykus.beygram.ui.screens.chat.bubbleMetricsFor
import com.beykus.beygram.ui.screens.theme.AppearancePage
import com.beykus.beygram.ui.screens.theme.ThemeSettingsScreen
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.resolveTokens

/**
 * Debug-only visual harness for the Appearance redesign. It never signs in and never touches TDLib data.
 * Launch it with `adb shell am start -n com.beykus.beygram.dev/com.beykus.beygram.AppearancePreviewHarnessActivity`.
 */
class AppearancePreviewHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = ThemeMode.entries.firstOrNull { it.name == intent.getStringExtra("mode") } ?: ThemeMode.Dark
        val preset = ThemePreset.entries.firstOrNull { it.name == intent.getStringExtra("palette") } ?: ThemePreset.Warm
        val style = DesignStyle.entries.firstOrNull { it.name == intent.getStringExtra("style") } ?: DesignStyle.Default
        val quality = GlassQuality.entries.firstOrNull { it.name == intent.getStringExtra("quality") } ?: GlassQuality.Auto
        val initialPage = AppearancePage.entries.firstOrNull { it.name == intent.getStringExtra("page") } ?: AppearancePage.Overview
        // Debug-only: write the same appearance store the app reads, so a cold start can be tested with a saved style.
        if (intent.getBooleanExtra("persist", false)) {
            val persisted = AppearanceConfig(
                preset = preset,
                mode = mode,
                wallpaper = ChatWallpaper.Preset("lavender-flow", ChatWallpaper.DEFAULT_PATTERN_INTENSITY),
                messageAnimations = MessageAnimationConfig(
                    linkedDirections = true,
                    shared = MessageAnimationDirection(MessageAnimationPreset.Flow)
                ),
                designStyle = style,
                glassQuality = quality
            )
            lifecycleScope.launch { AppearanceSettingsStore(applicationContext).save(persisted) }
        }
        setContent {
            val initial = remember {
                AppearanceConfig(
                    preset = preset,
                    mode = mode,
                    wallpaper = ChatWallpaper.Preset("lavender-flow", ChatWallpaper.DEFAULT_PATTERN_INTENSITY),
                    messageAnimations = MessageAnimationConfig(
                        linkedDirections = true,
                        shared = MessageAnimationDirection(MessageAnimationPreset.Flow)
                    )
                )
            }
            val appearanceConfig = initial.copy(designStyle = style, glassQuality = quality)
            var saved by remember { mutableStateOf(appearanceConfig) }
            var draft by remember { mutableStateOf(appearanceConfig) }
            val systemDark = isSystemInDarkTheme()
            val chrome = remember { intent.getStringExtra("screen") == "chrome" }
            if (chrome) {
                DesignChromePreview(saved, systemDark)
                return@setContent
            }
            val design = rememberDesign(saved)
            BeyGramTheme(
                darkTheme = saved.mode.isDark(systemDark),
                tokens = resolveTokens(saved, systemDark),
                design = design,
                messageTextSizeSp = saved.messageTextSizeSp,
                messageLineSpacing = saved.messageLineSpacing
            ) {
                CompositionLocalProvider(
                    LocalBubbleMetrics provides bubbleMetricsFor(
                        saved.bubbleStyle, saved.density, saved.bubbleCornerRadiusDp, design.bubbles
                    )
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ThemeSettingsScreen(
                            saved = saved,
                            draft = draft,
                            onDraftChange = { draft = it },
                            onSave = { saved = it; draft = it },
                            onBack = { finish() },
                            initialPageName = initialPage.name,
                            // The harness shares the debug app's filesDir. Never let its synthetic preview delete
                            // a wallpaper imported by the real Dev build.
                            manageWallpaperFiles = false
                        )
                    }
                }
            }
        }
    }
}
