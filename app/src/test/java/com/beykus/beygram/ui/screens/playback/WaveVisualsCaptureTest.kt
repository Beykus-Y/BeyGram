package com.beykus.beygram.ui.screens.playback

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasAsset
import com.beykus.beygram.data.canvas.CanvasLoadState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.music.PlayerWaveBackdrop
import com.beykus.beygram.ui.screens.music.WaveBackdrop
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Deterministic visual regression & capture suite for Wave Visuals 2.0, BG Rhythm, and BG Canvas
 * layer compatibility across Light and Dark themes.
 *
 * Uses synthetic Canvas frame placeholders to verify layer visibility/scrim/fallback policy
 * without claiming JVM verification of real hardware video decoding.
 *
 * When `BEYGRAM_WAVE_CAPTURE_DIR` is set, writes PNGs via Roborazzi into that directory.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h780dp-xhdpi", application = Application::class)
class WaveVisualsCaptureTest {

    @get:Rule
    val compose = createComposeRule()

    private enum class SceneKind {
        LegacyWaves,
        WaveVisuals2,
        RhythmCalm,
        RhythmDynamic,
        CanvasCard,
        CanvasFullscreen,
        CanvasFallback,
    }

    @Test
    fun rendersAllVisualScenesInLightAndDarkThemes() {
        val captureDir = System.getenv("BEYGRAM_WAVE_CAPTURE_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.apply { mkdirs() }

        val state = mutableStateOf(SceneKind.WaveVisuals2 to true)
        compose.setContent {
            val (scene, dark) = state.value
            BeyGramTheme(
                darkTheme = dark,
                design = designFor(DesignStyle.Default, GlassQuality.Balanced, liquidGlassV2 = false),
            ) {
                SyntheticPlayerScene(scene = scene, darkTheme = dark)
            }
        }

        for (dark in listOf(true, false)) {
            val themeLabel = if (dark) "dark" else "light"
            for (scene in SceneKind.entries) {
                compose.runOnIdle { state.value = scene to dark }
                compose.waitForIdle()
                if (captureDir != null) {
                    val out = File(captureDir, "${scene.name}-$themeLabel.png")
                    compose.onRoot().captureRoboImage(out.absolutePath)
                    check(out.length() > 0L) { "Empty screenshot at $out" }
                }
            }
        }
    }

    @Composable
    private fun SyntheticPlayerScene(scene: SceneKind, darkTheme: Boolean) {
        val palette = beyGramPalette()
        val coverPrimary = if (darkTheme) Color(0xFF4C6FFF) else Color(0xFF3355CC)
        val coverSecondary = if (darkTheme) Color(0xFF9C4DFF) else Color(0xFF7A36CC)
        val readyAsset = CanvasLoadState.Ready(
            CanvasAsset("ym:synthetic", "/tmp/synthetic.mp4", "vid1", 1024L)
        )

        val (rhythm, canvasMode, canvasVisual, loadState) = when (scene) {
            SceneKind.LegacyWaves -> Quad(BgRhythmSignal(), BgCanvasMode.Off, CanvasVisualState.Inactive, CanvasLoadState.Idle)
            SceneKind.WaveVisuals2 -> Quad(BgRhythmSignal(), BgCanvasMode.Off, CanvasVisualState.Inactive, CanvasLoadState.Idle)
            SceneKind.RhythmCalm -> Quad(
                BgRhythmSignal().apply { isActive = true; beatPulse = 0.55f; downbeatPulse = 0.35f; phraseEnergy = 0.60f },
                BgCanvasMode.Off,
                CanvasVisualState.Inactive,
                CanvasLoadState.Idle,
            )
            SceneKind.RhythmDynamic -> Quad(
                BgRhythmSignal().apply { isActive = true; beatPulse = 0.95f; downbeatPulse = 0.85f; phraseEnergy = 0.92f },
                BgCanvasMode.Off,
                CanvasVisualState.Inactive,
                CanvasLoadState.Idle,
            )
            SceneKind.CanvasCard -> Quad(
                BgRhythmSignal().apply { isActive = true; beatPulse = 0.45f; downbeatPulse = 0.2f; phraseEnergy = 0.5f },
                BgCanvasMode.Card,
                CanvasVisualState.DisplayingFirstFrame,
                readyAsset,
            )
            SceneKind.CanvasFullscreen -> Quad(
                BgRhythmSignal().apply { isActive = true; beatPulse = 0.8f; downbeatPulse = 0.6f; phraseEnergy = 0.85f },
                BgCanvasMode.Fullscreen,
                CanvasVisualState.DisplayingFirstFrame,
                readyAsset,
            )
            SceneKind.CanvasFallback -> Quad(
                BgRhythmSignal().apply { isActive = true; beatPulse = 0.4f; downbeatPulse = 0.2f; phraseEnergy = 0.5f },
                BgCanvasMode.Fullscreen,
                CanvasVisualState.Failed,
                readyAsset,
            )
        }

        val policy = resolvePlayerBackdropPolicy(
            waveEligible = true,
            canvasMode = canvasMode,
            canvasLoadState = loadState,
            canvasVisualState = canvasVisual,
            panel = PlayerPanel.Player,
            playing = true,
            resumed = true,
            reducedMotion = false,
            mediaMotionAllowed = true,
            powerSave = false,
        )

        val rhythmGlowBrush = Brush.verticalGradient(
            0.0f to coverPrimary,
            0.65f to coverPrimary.copy(alpha = 0.2f),
            1.0f to Color.Transparent,
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(palette.surface)
                .drawBehind {
                    if (policy.rhythmGlowScale > 0f && rhythm.isActive && rhythm.phraseEnergy > 0f) {
                        drawRect(
                            brush = rhythmGlowBrush,
                            alpha = (rhythm.phraseEnergy * 0.16f * policy.rhythmGlowScale).coerceIn(0f, 0.16f),
                        )
                    }
                }
        ) {
            if (policy.fullscreenCanvasDisplaying) {
                // Synthetic first video frame placeholder for policy verification.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1D2B64), Color(0xFFF8CDDA))
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = policy.fullscreenScrimAlpha))
                )
            }

            if (scene == SceneKind.LegacyWaves) {
                WaveBackdrop(
                    color = palette.accent,
                    secondary = palette.accentSoft,
                    modifier = Modifier.fillMaxSize().alpha(0.5f),
                )
            } else if (policy.waveAlpha > 0f) {
                PlayerWaveBackdrop(
                    primary = coverPrimary,
                    secondary = coverSecondary,
                    tertiary = palette.accentSoft,
                    darkTheme = darkTheme,
                    trackKey = "ym:synthetic-track-77",
                    animate = false,
                    alphaProvider = { policy.waveAlpha },
                    rhythmSignalProvider = { rhythm },
                    modifier = Modifier.fillMaxSize(),
                    deterministicTimeSec = 2.4f,
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val scale = if (rhythm.isActive) 1f + rhythm.beatPulse * 0.025f else 1f
                Box(
                    Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(styledShape(20.dp))
                        .background(
                            if (scene == SceneKind.CanvasCard) {
                                Brush.linearGradient(listOf(Color(0xFF283E51), Color(0xFF4B79A1)))
                            } else {
                                Brush.linearGradient(listOf(coverPrimary, coverSecondary))
                            }
                        )
                        .border(1.dp, palette.divider, styledShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = scene.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Заново завоевать",
                    color = if (policy.fullscreenCanvasDisplaying) Color.White else palette.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "pepel nahudi",
                    color = if (policy.fullscreenCanvasDisplaying) Color.White.copy(alpha = 0.78f) else palette.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
