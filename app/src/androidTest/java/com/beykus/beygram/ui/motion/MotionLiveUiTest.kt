package com.beykus.beygram.ui.motion

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion
import com.beykus.beygram.ui.screens.music.WaveBackdrop
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented Compose regressions for live Motion Off/category changes. */
@RunWith(AndroidJUnit4::class)
class MotionLiveUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listScrollSnapsWhenMotionOffIsEnabledMidFlight() {
        val settings = mutableStateOf(MotionSettings())
        val start = mutableStateOf(false)
        var firstVisible: () -> Int = { -1 }
        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(LocalMotionSettings provides settings.value, LocalSystemReducedMotion provides false) {
                    val state = rememberLazyListState()
                    val instant = rememberMotionInstant(MotionToken.ListReorder)
                    firstVisible = { state.firstVisibleItemIndex }
                    LaunchedEffect(start.value) {
                        if (start.value) state.scrollToItemRespectingMotion(180, motionInstant = instant)
                    }
                    LazyColumn(Modifier.height(240.dp)) { items(250) { Text("Item $it", Modifier.height(48.dp)) } }
                }
            }
        }
        startAndDisable(start, settings)
        compose.runOnUiThread { assertEquals(180, firstVisible()) }
    }

    @Test fun gridScrollSnapsWhenListCategoryIsDisabledMidFlight() {
        val settings = mutableStateOf(MotionSettings())
        val start = mutableStateOf(false)
        var firstVisible: () -> Int = { -1 }
        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(LocalMotionSettings provides settings.value, LocalSystemReducedMotion provides false) {
                    val state = rememberLazyGridState()
                    val instant = rememberMotionInstant(MotionToken.ListReorder)
                    firstVisible = { state.firstVisibleItemIndex }
                    LaunchedEffect(start.value) {
                        if (start.value) state.scrollToItemRespectingMotion(180, motionInstant = instant)
                    }
                    LazyVerticalGrid(GridCells.Fixed(2), Modifier.size(240.dp, 240.dp)) {
                        items(250) { Text("Item $it", Modifier.height(48.dp)) }
                    }
                }
            }
        }
        startAndDisable(start, settings, MotionCategory.Lists)
        compose.runOnUiThread { assertEquals(180, firstVisible()) }
    }

    @Test fun pagerSnapsWhenTabCategoryIsDisabledMidFlight() {
        val settings = mutableStateOf(MotionSettings())
        val start = mutableStateOf(false)
        var page: () -> Int = { -1 }
        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(LocalMotionSettings provides settings.value, LocalSystemReducedMotion provides false) {
                    val state = rememberPagerState(pageCount = { 80 })
                    val instant = rememberMotionInstant(MotionToken.TabSelect)
                    page = { state.currentPage }
                    LaunchedEffect(start.value) {
                        if (start.value) state.scrollToPageRespectingMotion(60, motionInstant = instant)
                    }
                    HorizontalPager(state, modifier = Modifier.size(240.dp, 240.dp)) { Text("Page $it") }
                }
            }
        }
        startAndDisable(start, settings, MotionCategory.Tabs)
        compose.runOnUiThread { assertEquals(60, page()) }
    }

    @Test fun waveBackdropStopsWhenMediaCategoryIsDisabledWhileMounted() {
        val settings = mutableStateOf(MotionSettings())
        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(LocalMotionSettings provides settings.value, LocalSystemReducedMotion provides false) {
                    WaveBackdrop(Color.Blue, Color.Magenta, Modifier.size(240.dp, 120.dp).testTag("wave"))
                }
            }
        }
        compose.runOnUiThread { settings.value = settings.value.withCategoryEnabled(MotionCategory.Media, false) }
        compose.waitForIdle()
        val first = compose.onNodeWithTag("wave").captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(1000L)
        val later = compose.onNodeWithTag("wave").captureToImage().asAndroidBitmap()
        assertTrue("Wave must remain still after disabling Media motion", first.sameAs(later))
    }

    /** Frozen frames distinguish an immediate snap from a naturally completed animation. */
    private fun startAndDisable(start: MutableState<Boolean>, settings: MutableState<MotionSettings>, category: MotionCategory? = null) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { start.value = true }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnUiThread {
            settings.value = if (category == null) settings.value.withPreset(MotionPreset.Off)
                else settings.value.withCategoryEnabled(category, false)
        }
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.autoAdvance = true
    }
}
