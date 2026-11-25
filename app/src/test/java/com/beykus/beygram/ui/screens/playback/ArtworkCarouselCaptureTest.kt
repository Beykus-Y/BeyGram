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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.design.styledShape
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
 * Deterministic visual verification & capture for the unified spatial artwork carousel
 * at start, mid-swipe, complete, and cancelled states.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h780dp-xhdpi", application = Application::class)
class ArtworkCarouselCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureCarouselStages() {
        val captureDir = System.getenv("BEYGRAM_CAROUSEL_CAPTURE_DIR")?.let(::File)?.apply { mkdirs() }
        val offsetState = mutableFloatStateOf(0f)

        compose.setContent {
            BeyGramTheme(darkTheme = true, design = designFor(DesignStyle.LiquidGlass, GlassQuality.Balanced, liquidGlassV2 = true)) {
                CarouselPreviewScene(offsetFraction = offsetState.floatValue)
            }
        }

        val stages = listOf(
            0f to "carousel-start",
            -0.48f to "carousel-mid",
            -1.0f to "carousel-complete",
            -0.18f to "carousel-cancel",
        )

        stages.forEach { (offset, name) ->
            compose.runOnIdle { offsetState.floatValue = offset }
            compose.waitForIdle()
            if (captureDir != null) {
                val out = File(captureDir, "$name.png")
                compose.onRoot().captureRoboImage(out.absolutePath)
                check(out.length() > 0L) { "Empty screenshot: $out" }
            }
        }
    }
}

@Composable
private fun CarouselPreviewScene(offsetFraction: Float) {
    val palette = beyGramPalette()
    val density = LocalDensity.current
    val artworkSize = 260.dp
    val travelPx = with(density) { (artworkSize * 0.78f).toPx() }
    val curTransform = carouselCardTransform(offsetFraction, travelPx, reducedMotion = false)
    val adjTransform = carouselCardTransform(1f + offsetFraction, travelPx, reducedMotion = false)

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.screen)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(artworkSize), contentAlignment = Alignment.Center) {
                if (offsetFraction < -0.001f) {
                    CarouselCardMock(
                        title = "Track 2 (Incoming)",
                        colors = listOf(Color(0xFF3E1F47), Color(0xFF7B2CBF)),
                        transform = adjTransform,
                    )
                }
                CarouselCardMock(
                    title = "Track 1 (Current)",
                    colors = listOf(Color(0xFF1B3A4B), Color(0xFF006466)),
                    transform = curTransform,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "offset=${"%.2f".format(offsetFraction)}",
                color = palette.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CarouselCardMock(
    title: String,
    colors: List<Color>,
    transform: CarouselCardTransform,
) {
    val density = LocalDensity.current
    val shape = styledShape(20.dp)
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(transform.zIndex)
            .graphicsLayer {
                translationX = transform.translationX
                scaleX = transform.scale
                scaleY = transform.scale
                rotationY = transform.rotationY
                cameraDistance = 12f * density.density
                alpha = transform.alpha
            }
            .clip(shape)
            .background(Brush.linearGradient(colors), shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
