package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
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
class ChatBottomInsetsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun timelineReachesWindowEdgeAndControlsReserveOnlyUnconsumedInsets() {
        val nav = mutableIntStateOf(24)
        val ime = mutableIntStateOf(0)
        val floating = mutableStateOf(false)
        val composerShown = mutableStateOf(true)
        var rootHeight = 0
        var controlHeight = 0
        var chromeHeight = 0
        var bodyHeight = 0
        lateinit var list: LazyListState
        compose.setContent {
            BeyGramTheme {
                val safe = WindowInsets(bottom = maxOf(nav.intValue, ime.intValue))
                val keyboard = WindowInsets(bottom = ime.intValue)
                var measuredChrome by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                list = rememberLazyListState()
                Column(Modifier.fillMaxSize().onSizeChanged { rootHeight = it.height }
                    .chatViewportInsets(safe, keyboard)) {
                    Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { bodyHeight = it.height }) {
                        LazyColumn(
                            state = list,
                            reverseLayout = true,
                            modifier = Modifier.underChatChrome({ 0 }, { measuredChrome })
                                .fillMaxSize().testTag("timeline"),
                            contentPadding = PaddingValues(bottom = with(density) { measuredChrome.toDp() })
                        ) {
                            items(60) { Box(Modifier.fillMaxWidth().height(36.dp)) }
                        }
                    }
                    ChatChromeHost(
                        glass = false,
                        edge = GlassEdge.TopHairline,
                        backdrop = null,
                        onHeight = {
                            measuredChrome = it
                            chromeHeight = it
                        },
                        floatingComposer = floating.value,
                        paneContent = {
                            Box(Modifier.fillMaxWidth().chatBottomInsets(safe)) {
                                if (composerShown.value) {
                                    Box(Modifier.fillMaxWidth().height(48.dp).onSizeChanged { controlHeight = it.height })
                                }
                            }
                        }
                    )
                }
            }
        }

        fun assertGeometry() = compose.runOnIdle {
            val control = if (composerShown.value) controlHeight else 0
            assertTrue(rootHeight > 0)
            assertTrue(controlHeight > 0)
            assertEquals(rootHeight - ime.intValue, list.layoutInfo.viewportSize.height)
            assertEquals(control + (nav.intValue - ime.intValue).coerceAtLeast(0), chromeHeight)
            assertEquals(rootHeight - maxOf(nav.intValue, ime.intValue), bodyHeight + control)
            assertEquals(-chromeHeight, list.layoutInfo.viewportStartOffset)
        }

        assertGeometry()
        compose.onNodeWithTag("timeline").performScrollToIndex(10)
        compose.runOnIdle { assertEquals(10, list.firstVisibleItemIndex) }
        compose.runOnIdle { list.layoutInfo.visibleItemsInfo.first { it.index == 10 }.let {
            assertTrue(it.offset >= list.layoutInfo.viewportStartOffset + chromeHeight)
        } }
        compose.runOnIdle { floating.value = true; nav.intValue = 48 }
        assertGeometry()
        compose.runOnIdle { ime.intValue = 180 }
        assertGeometry()
        compose.runOnIdle { ime.intValue = 12 }
        assertGeometry()
        compose.runOnIdle { ime.intValue = 0; composerShown.value = false }
        assertGeometry()
    }
}
