package com.beykus.beygram.ui.components

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.ui.design.GlassBackdrop
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.screens.chat.ChatChromeHost
import com.beykus.beygram.ui.screens.chat.LocalComposerOnGlassPlane
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class MainTabGlassLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun highHeadersSampleASiblingSourceAndProtectTheRowsUnderThem() {
        val quality = mutableStateOf(GlassQuality.High)
        var ancestor: GlassBackdrop? = null
        var headerSource: GlassBackdrop? = null
        var listSource: GlassBackdrop? = null
        var rowClicks = 0
        compose.setContent {
            val outer = rememberGlassBackdrop()
            SideEffect { ancestor = outer }
            CompositionLocalProvider(LocalGlassBackdrop provides outer) {
                BeyGramTheme(design = designFor(DesignStyle.LiquidGlass, quality.value, liquidGlassV2 = true)) {
                    MainTabLayout(
                        Modifier.fillMaxSize(),
                        header = {
                            val source = LocalGlassBackdrop.current
                            SideEffect { headerSource = source }
                            Box(Modifier.fillMaxWidth().height(64.dp).testTag("header"))
                        }
                    ) { inset ->
                        val source = LocalGlassBackdrop.current
                        SideEffect { listSource = source }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Spacer(Modifier.height(inset))
                            Box(
                                Modifier.fillMaxWidth().height(140.dp).testTag("row")
                                    .clickable { rowClicks++ }
                            )
                            Spacer(Modifier.height(800.dp))
                        }
                    }
                }
            }
        }

        val header = compose.onNodeWithTag("header")
        val row = compose.onNodeWithTag("row")
        assertEquals(header.getUnclippedBoundsInRoot().bottom, row.getUnclippedBoundsInRoot().top)
        compose.runOnIdle {
            assertNotNull(headerSource)
            assertNotSame(ancestor, headerSource)
            assertNull(listSource)
        }

        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 88f) }
        assertTrue(row.getUnclippedBoundsInRoot().top < header.getUnclippedBoundsInRoot().bottom)
        header.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(0, rowClicks)
            quality.value = GlassQuality.Balanced
        }

        compose.waitForIdle()
        compose.runOnIdle { assertNull(headerSource) }
        assertEquals(header.getUnclippedBoundsInRoot().bottom, row.getUnclippedBoundsInRoot().top)
    }

    @Test
    fun sharedComposerAndItsPopupDoNotInheritTheChatCapture() {
        val floating = mutableStateOf(false)
        var sharedPlane = false
        var composerSource: GlassBackdrop? = null
        var popupSource: GlassBackdrop? = null
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            BeyGramTheme(design = designFor(DesignStyle.LiquidGlass, GlassQuality.High, liquidGlassV2 = true)) {
                Box {
                    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop))
                    ChatChromeHost(
                        glass = true,
                        edge = GlassEdge.TopHairline,
                        backdrop = backdrop,
                        onHeight = {},
                        floatingComposer = floating.value,
                        paneContent = {
                            val source = LocalGlassBackdrop.current
                            val inPlane = LocalComposerOnGlassPlane.current
                            SideEffect {
                                composerSource = source
                                sharedPlane = inPlane
                            }
                            Box(Modifier.fillMaxWidth().height(64.dp)) {
                                BeyGramPopover(expanded = floating.value, onDismissRequest = {}) {
                                    val popupBackdrop = LocalGlassBackdrop.current
                                    SideEffect { popupSource = popupBackdrop }
                                    Text("Attachment actions")
                                }
                            }
                        }
                    )
                }
            }
        }

        compose.runOnIdle {
            assertTrue(sharedPlane)
            assertNull(composerSource)
            floating.value = true
        }
        compose.onNodeWithText("Attachment actions").assertExists()
        compose.runOnIdle {
            assertEquals(false, sharedPlane)
            assertNotNull(composerSource)
            assertNull(popupSource)
        }
    }
}
