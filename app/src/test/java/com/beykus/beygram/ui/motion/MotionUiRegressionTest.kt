package com.beykus.beygram.ui.motion

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion
import com.beykus.beygram.ui.screens.chat.AnimatedTypingRow
import com.beykus.beygram.ui.screens.chat.TypingDots
import com.beykus.beygram.ui.screens.chats.ChatPeekSheet
import com.beykus.beygram.ui.screens.music.WaveBackdrop
import com.beykus.beygram.ui.screens.playback.MiniPlayerUi
import com.beykus.beygram.ui.screens.playback.NowPlayingBarHost
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric Compose UI regressions executed in CI (`:app:testDevDebugUnitTest`) covering live
 * Motion Off and individual [MotionCategory] disabling with `systemReducedMotion = false`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class MotionUiRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chatPeekSheetHonorsLiveMotionOffToggleWhileOpen() {
        val settings = mutableStateOf(MotionSettings())
        val open = mutableStateOf(true)
        var closeCount = 0
        val chat = ChatPreview(
            id = 42L,
            title = "Peek Preview Chat",
            initials = "PP",
            lastMessage = UiText.Raw("Hello"),
            lastMessageTime = "12:00",
            unreadCount = 0,
            avatarColorArgb = 0xFF4477AA
        )
        val messages = listOf(
            ChatMessage(
                id = 1L,
                chatId = 42L,
                text = "Latest preview message",
                time = "12:00",
                isOutgoing = false
            )
        )

        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(
                    LocalMotionSettings provides settings.value,
                    LocalSystemReducedMotion provides false
                ) {
                    if (open.value) {
                        ChatPeekSheet(
                            chat = chat,
                            observeFile = { id -> MutableStateFlow(MediaFileState(id = id)) },
                            downloadFile = {},
                            load = { Result.success(messages) },
                            onClose = {
                                closeCount++
                                open.value = false
                            }
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Peek Preview Chat").assertIsDisplayed()
        compose.onNodeWithText("Latest preview message").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            settings.value = settings.value.withPreset(MotionPreset.Off)
        }
        compose.waitForIdle()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }

        // Tap the dimmed backdrop inside the Dialog root (last root) to trigger dismiss.
        compose.onAllNodes(isRoot()).onLast().performTouchInput { click(Offset(4f, 4f)) }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()

        assertEquals("Dismissing ChatPeekSheet after live Motion Off must close immediately", 1, closeCount)
        assertFalse(open.value)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun typingDotsStopWhenMotionOffOrMicroCategoryDisabledWhileMounted() {
        val settings = mutableStateOf(MotionSettings())
        lateinit var hostView: View
        compose.setContent {
            hostView = LocalView.current
            BeyGramTheme {
                CompositionLocalProvider(
                    LocalMotionSettings provides settings.value,
                    LocalSystemReducedMotion provides false
                ) {
                    TypingDots(
                        color = Color.Magenta,
                        modifier = Modifier.testTag("typing-dots")
                    )
                }
            }
        }

        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val activeMounted = hostView.renderToBitmap()

        // Toggle Motion Off while mounted: must switch from animated branch to static 0.7f dots and stay still.
        compose.runOnUiThread { settings.value = settings.value.withPreset(MotionPreset.Off) }
        compose.waitForIdle()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val offBefore = hostView.renderToBitmap()
        compose.mainClock.advanceTimeBy(1000L)
        compose.waitForIdle()
        val offAfter = hostView.renderToBitmap()
        assertFalse(
            "TypingDots should leave animated branch when Motion Off is toggled while mounted",
            activeMounted.sameAs(offBefore)
        )
        assertTrue("TypingDots must stay still after live Motion Off", offBefore.sameAs(offAfter))

        // Re-enable motion, verify animated branch returns, then disable only MotionCategory.Micro with systemReducedMotion = false.
        compose.runOnUiThread { settings.value = MotionSettings() }
        compose.waitForIdle()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val reEnabled = hostView.renderToBitmap()
        assertFalse("TypingDots should return to animated branch when motion is re-enabled", reEnabled.sameAs(offBefore))

        compose.runOnUiThread {
            settings.value = MotionSettings().withCategoryEnabled(MotionCategory.Micro, enabled = false)
        }
        compose.waitForIdle()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val microOffBefore = hostView.renderToBitmap()
        compose.mainClock.advanceTimeBy(1000L)
        compose.waitForIdle()
        val microOffAfter = hostView.renderToBitmap()
        assertTrue(
            "TypingDots must match static branch when Micro category is disabled with systemReducedMotion=false",
            microOffBefore.sameAs(offBefore)
        )
        assertTrue(
            "TypingDots must stay still when Micro category is disabled with systemReducedMotion=false",
            microOffBefore.sameAs(microOffAfter)
        )
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun typingRowSnapsVisibilityWhenChatsCategoryDisabled() {
        val settings = mutableStateOf(
            MotionSettings().withCategoryEnabled(MotionCategory.Chats, enabled = false)
        )
        val names = mutableStateOf(emptyList<String>())
        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(
                    LocalMotionSettings provides settings.value,
                    LocalSystemReducedMotion provides false
                ) {
                    AnimatedTypingRow(names = names.value)
                }
            }
        }

        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { names.value = listOf("Alice") }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNodeWithText("Alice", substring = true).assertIsDisplayed()

        compose.runOnUiThread { names.value = emptyList() }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNodeWithText("Alice", substring = true).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun nowPlayingBarRailCoverImeHideShowHonorsMotionOffAndCategories() {
        val context = RuntimeEnvironment.getApplication()
        val playLabel = context.getString(R.string.common_play)
        val railButtonMatcher = SemanticsMatcher("onClick label $playLabel") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == playLabel
        }
        val settings = mutableStateOf(MotionSettings().withPreset(MotionPreset.Off))
        val keyboardVisible = mutableStateOf(false)
        val mini = sampleMiniPlayerUi()

        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(
                    LocalMotionSettings provides settings.value,
                    LocalSystemReducedMotion provides false
                ) {
                    NowPlayingBarHost(
                        mini = mini,
                        keyboardVisible = keyboardVisible.value,
                        bottomInset = true,
                        hideWhenImeVisible = true,
                        railCover = true
                    )
                }
            }
        }

        compose.waitForIdle()
        compose.onNode(railButtonMatcher).assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        // Hide for IME -> disappears immediately without waiting for exit duration.
        compose.runOnUiThread { keyboardVisible.value = true }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNode(railButtonMatcher).assertDoesNotExist()

        // Disable only Panels + Media while systemReducedMotion = false -> reappears immediately on IME hide.
        compose.runOnUiThread {
            settings.value = MotionSettings()
                .withCategoryEnabled(MotionCategory.Panels, enabled = false)
                .withCategoryEnabled(MotionCategory.Media, enabled = false)
            keyboardVisible.value = false
        }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNode(railButtonMatcher).assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun nowPlayingBarAnimatedContentImeHideShowHonorsMotionOffAndMediaCategory() {
        val settings = mutableStateOf(MotionSettings().withPreset(MotionPreset.Off))
        val keyboardVisible = mutableStateOf(false)
        val mini = sampleMiniPlayerUi()

        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(
                    LocalMotionSettings provides settings.value,
                    LocalSystemReducedMotion provides false
                ) {
                    NowPlayingBarHost(
                        mini = mini,
                        keyboardVisible = keyboardVisible.value,
                        bottomInset = true,
                        hideWhenImeVisible = true,
                        railCover = false
                    )
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Sample Track").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { keyboardVisible.value = true }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNodeWithText("Sample Track").assertDoesNotExist()

        // With Panels and Media disabled (systemReducedMotion=false), reopening after IME dismisses is instant.
        compose.runOnUiThread {
            settings.value = MotionSettings()
                .withCategoryEnabled(MotionCategory.Panels, enabled = false)
                .withCategoryEnabled(MotionCategory.Media, enabled = false)
            keyboardVisible.value = false
        }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNodeWithText("Sample Track").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun disablingIndividualCategoriesSnapsTargetUiWhileKeepingOthersActive() {
        val settings = mutableStateOf(MotionSettings())
        val startScroll = mutableStateOf(false)
        val startList = mutableStateOf(false)
        val startPager = mutableStateOf(false)
        lateinit var scrollState: ScrollState
        lateinit var waveHostView: View
        var listFirstVisible = -1
        var pagerPage = -1

        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(
                    LocalMotionSettings provides settings.value,
                    LocalSystemReducedMotion provides false
                ) {
                    val listsInstant = rememberMotionInstant(MotionToken.ListReorder)
                    val tabsInstant = rememberMotionInstant(MotionToken.TabSelect)
                    val panelSpec = rememberMotionSpec(MotionToken.SheetEnter)
                    val mediaSpec = rememberMotionSpec(MotionToken.MediaExpand)

                    scrollState = rememberScrollState()
                    val lazyListState = rememberLazyListState()
                    val pagerState = rememberPagerState(pageCount = { 50 })
                    listFirstVisible = lazyListState.firstVisibleItemIndex
                    pagerPage = pagerState.currentPage

                    LaunchedEffect(startScroll.value) {
                        if (startScroll.value) scrollState.scrollToRespectingMotion(1200, motionInstant = listsInstant)
                    }
                    LaunchedEffect(startList.value) {
                        if (startList.value) lazyListState.scrollToItemRespectingMotion(80, motionInstant = listsInstant)
                    }
                    LaunchedEffect(startPager.value) {
                        if (startPager.value) pagerState.scrollToPageRespectingMotion(30, motionInstant = tabsInstant)
                    }

                    Column(Modifier.fillMaxSize()) {
                        Text(
                            "panelInstant=${panelSpec.isInstant},mediaInstant=${mediaSpec.isInstant}",
                            modifier = Modifier.testTag("flags")
                        )
                        androidx.compose.foundation.layout.Box(Modifier.size(120.dp, 48.dp)) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    androidx.compose.ui.platform.ComposeView(ctx).also { waveHostView = it }
                                },
                                update = { view ->
                                    view.setContent {
                                        CompositionLocalProvider(
                                            LocalMotionSettings provides settings.value,
                                            LocalSystemReducedMotion provides false
                                        ) {
                                            WaveBackdrop(Color.Blue, Color.Cyan, Modifier.fillMaxSize())
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(Modifier.height(120.dp).verticalScroll(scrollState)) {
                            repeat(40) { Text("Scroll $it", Modifier.height(60.dp)) }
                        }
                        LazyColumn(Modifier.height(120.dp), state = lazyListState) {
                            items(120) { Text("Item $it", Modifier.height(48.dp)) }
                        }
                        HorizontalPager(pagerState, modifier = Modifier.size(200.dp, 80.dp)) {
                            Text("Page $it")
                        }
                    }
                }
            }
        }

        // Disable only Lists while systemReducedMotion = false: Panels and Media remain active.
        compose.runOnUiThread {
            settings.value = MotionSettings().withCategoryEnabled(MotionCategory.Lists, enabled = false)
        }
        compose.waitForIdle()
        compose.onNodeWithText("panelInstant=false,mediaInstant=false").assertIsDisplayed()

        // Verify ScrollState snaps immediately under Lists disabled without advancing animation duration.
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { startScroll.value = true }
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        assertEquals(1200, scrollState.value)

        // Disable Media only -> WaveBackdrop stops while Panels stays active.
        compose.runOnUiThread {
            settings.value = settings.value.withCategoryEnabled(MotionCategory.Media, enabled = false)
        }
        compose.waitForIdle()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val waveFirst = waveHostView.renderToBitmap()
        compose.mainClock.advanceTimeBy(800L)
        compose.waitForIdle()
        val waveLater = waveHostView.renderToBitmap()
        assertTrue("WaveBackdrop must stop when Media category is disabled", waveFirst.sameAs(waveLater))
        compose.onNodeWithText("panelInstant=false,mediaInstant=true").assertIsDisplayed()
        compose.mainClock.autoAdvance = true

        // Mid-flight ScrollState, LazyList, and HorizontalPager cancellation when Lists & Tabs are disabled mid-scroll.
        compose.runOnUiThread {
            settings.value = MotionSettings()
            startScroll.value = false
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { startScroll.value = true }
        compose.waitForIdle()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnUiThread {
            settings.value = settings.value.withCategoryEnabled(MotionCategory.Lists, enabled = false)
        }
        compose.waitForIdle()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1200, scrollState.value)

        compose.runOnUiThread { settings.value = MotionSettings() }
        compose.waitForIdle()
        startAndDisable(startList, settings, MotionCategory.Lists)
        assertEquals(80, listFirstVisible)

        compose.runOnUiThread { settings.value = MotionSettings() }
        compose.waitForIdle()
        startAndDisable(startPager, settings, MotionCategory.Tabs)
        assertEquals(30, pagerPage)
    }

    private fun startAndDisable(
        start: MutableState<Boolean>,
        settings: MutableState<MotionSettings>,
        category: MotionCategory
    ) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { start.value = true }
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnUiThread {
            settings.value = settings.value.withCategoryEnabled(category, enabled = false)
        }
        compose.waitForIdle()
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun View.renderToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        return bitmap
    }

    private fun sampleMiniPlayerUi(): MiniPlayerUi = MiniPlayerUi(
        key = "track-1",
        title = "Sample Track",
        artist = "Sample Artist",
        album = "Sample Album",
        artworkPath = null,
        audioPath = null,
        isMusic = true,
        playing = false,
        intentPlaying = false,
        buffering = false,
        failed = false,
        ended = false,
        canNext = true,
        durationSeconds = 180,
        isVideoNote = false,
        origin = null
    )
}
