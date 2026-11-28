package com.beykus.beygram.ui.screens.chat

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.CachedFrame
import com.beykus.beygram.data.media.EmojiFrameCache
import com.beykus.beygram.data.media.EmojiFrameRasterizer
import com.beykus.beygram.data.media.GifAnimationBudget
import com.beykus.beygram.data.media.MediaPerformanceTier
import com.beykus.beygram.data.media.PreparedEmojiAnimation
import com.beykus.beygram.data.media.SharedTgsEmojiRenderer
import com.beykus.beygram.data.unavailable.UnavailableTelegramRepository
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.domain.model.StickerShelfVersions
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MediaPickerPerformanceRegressionTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        MediaPickerSessionCache.clearAll()
    }

    @After
    fun tearDown() {
        MediaPickerSessionCache.clearAll()
    }

    private class CountingPickerRepository :
        ConversationRepository by UnavailableTelegramRepository() {

        val shelfVersions = MutableStateFlow(StickerShelfVersions())
        val premiumFlow = MutableStateFlow<Boolean?>(true)

        var recentStickersCalls = 0
        var favoriteStickersCalls = 0
        var installedStickerSetsCalls = 0
        var installedCustomEmojiSetsCalls = 0
        var recommendedCustomEmojiSetsCalls = 0
        var recentAnimationsCalls = 0
        var savedAnimationsCalls = 0
        var isPremiumCalls = 0

        override fun observeStickerShelfVersions(): Flow<StickerShelfVersions> = shelfVersions

        override fun observeIsPremium(): Flow<Boolean?> = premiumFlow

        override suspend fun isPremiumAccount(): Result<Boolean> {
            isPremiumCalls++
            return Result.success(true)
        }

        override suspend fun installedCustomEmojiSets(): Result<List<CustomEmojiSetBrief>> {
            installedCustomEmojiSetsCalls++
            return Result.success(
                listOf(CustomEmojiSetBrief(id = 101L, title = "Emoji Pack", covers = listOf(1L)))
            )
        }

        override suspend fun recommendedCustomEmojiSets(): Result<List<CustomEmojiSetBrief>> {
            recommendedCustomEmojiSetsCalls++
            return Result.success(emptyList())
        }

        override suspend fun customEmojiSetIds(setId: Long): Result<List<Long>> =
            Result.success(listOf(1L))

        override suspend fun recentStickers(): Result<List<StickerItem>> {
            recentStickersCalls++
            return Result.success(listOf(StickerItem(setId = 1L, stickerId = 10L, fileId = 10)))
        }

        override suspend fun favoriteStickers(): Result<List<StickerItem>> {
            favoriteStickersCalls++
            return Result.success(listOf(StickerItem(setId = 1L, stickerId = 11L, fileId = 11)))
        }

        override suspend fun installedStickerSets(): Result<List<StickerSetSummary>> {
            installedStickerSetsCalls++
            return Result.success(listOf(StickerSetSummary(id = 200L, title = "Stickers 1")))
        }

        override suspend fun stickerSet(setId: Long): Result<StickerSetContent> =
            Result.success(
                StickerSetContent(
                    id = setId,
                    title = "Stickers 1",
                    stickers = listOf(StickerItem(setId = setId, stickerId = 20L, fileId = 20))
                )
            )

        override suspend fun recentAnimations(offset: String): Result<AnimationPage> {
            recentAnimationsCalls++
            return Result.success(AnimationPage(animations = listOf(AnimationItem(fileId = 30))))
        }

        override suspend fun savedAnimations(): Result<List<AnimationItem>> {
            savedAnimationsCalls++
            return Result.success(listOf(AnimationItem(fileId = 31)))
        }
    }

    @Test
    fun coldOpenGatesOtherTabsAndWarmReopenAvoidsRedundantTdlibCalls() {
        val context = RuntimeEnvironment.getApplication()
        val repo = CountingPickerRepository()
        val fileFlows = HashMap<Int, MutableStateFlow<MediaFileState>>()
        val observeFile: (Int) -> StateFlow<MediaFileState> = { id ->
            fileFlows.getOrPut(id) { MutableStateFlow(MediaFileState(id = id)) }
        }
        var open by mutableStateOf(true)

        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(LocalPlaybackAccountId provides "acc-perf") {
                    val scope = rememberCoroutineScope()
                    if (open) {
                        StickerPanelHost(
                            conversation = repo,
                            chatId = 42L,
                            replyId = null,
                            forumTopicId = 0L,
                            observeFile = observeFile,
                            downloadFile = {},
                            scope = scope,
                            composerReserve = 0.dp,
                            keyboardHeightHint = 280.dp,
                            onReplyConsumed = {},
                            onAnimationAttach = {},
                            onStickerOptions = {},
                            onSendError = {},
                            onInsertEmoji = {},
                            onInsertCustomEmoji = { _: CustomEmoji -> },
                            savedMessages = false,
                            onDeleteBackward = {},
                            onClose = { open = false }
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        // Cold open defaults to Stickers tab: only Stickers + Premium queries run; Emoji & GIF tabs stay deferred.
        assertEquals(1, repo.recentStickersCalls)
        assertEquals(1, repo.favoriteStickersCalls)
        assertEquals(1, repo.installedStickerSetsCalls)
        assertEquals(1, repo.isPremiumCalls)
        assertEquals(0, repo.installedCustomEmojiSetsCalls)
        assertEquals(0, repo.recommendedCustomEmojiSetsCalls)
        assertEquals(0, repo.recentAnimationsCalls)
        assertEquals(0, repo.savedAnimationsCalls)

        // Close and warm reopen: zero redundant TDLib calls.
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        compose.runOnIdle { open = true }
        compose.waitForIdle()
        assertEquals(1, repo.recentStickersCalls)
        assertEquals(1, repo.favoriteStickersCalls)
        assertEquals(1, repo.installedStickerSetsCalls)
        assertEquals(1, repo.isPremiumCalls)

        // Bump only recentStickers version: surgical refresh without touching favorites or sets.
        compose.runOnIdle {
            repo.shelfVersions.value = repo.shelfVersions.value.copy(recentStickers = 2L)
        }
        compose.waitForIdle()
        assertEquals(2, repo.recentStickersCalls)
        assertEquals(1, repo.favoriteStickersCalls)
        assertEquals(1, repo.installedStickerSetsCalls)

        // Switch to Emoji tab: fetches custom emoji sets once, GIFs still deferred.
        val emojiTabLabel = context.getString(R.string.sticker_panel_tab_emoji)
        compose.onNodeWithText(emojiTabLabel).performClick()
        compose.waitForIdle()
        assertEquals(1, repo.installedCustomEmojiSetsCalls)
        assertEquals(1, repo.recommendedCustomEmojiSetsCalls)
        assertEquals(0, repo.recentAnimationsCalls)

        // Logout clears account cache, so reopening fetches fresh data for the default Stickers tab.
        MediaPickerSessionCache.clearAccount("acc-perf")
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        compose.onNodeWithText(emojiTabLabel).assertDoesNotExist()
        compose.runOnIdle { open = true }
        compose.waitForIdle()
        compose.onNodeWithText(emojiTabLabel).assertExists()
        // Reopen starts with collectAsStateWithLifecycle initialValue (0L) then receives current StateFlow value (2L).
        assertEquals(4, repo.recentStickersCalls)
        assertEquals(2, repo.favoriteStickersCalls)
        assertEquals(2, repo.installedStickerSetsCalls)
    }

    @Test
    fun fiftyOpenCloseCyclesReleaseAllSharedRendererAndGifBudgetResources() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val cache = EmojiFrameCache()
        val gifBudget = GifAnimationBudget(maxConcurrent = 3)
        try {
            val renderer = SharedTgsEmojiRenderer(
                scope = scope,
                mainDispatcher = dispatcher,
                schedulerDispatcher = dispatcher,
                workerDispatcher = dispatcher,
                frameCache = cache,
                rasterizer = object : EmojiFrameRasterizer {
                    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> {
                        return Result.success(
                            PreparedEmojiAnimation(
                                frameCount = 6,
                                durationMs = 600L,
                                renderFrame = {
                                    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                                    CachedFrame(bmp.asImageBitmap(), sizePx * sizePx * 4)
                                }
                            )
                        )
                    }
                },
                lowRam = false,
                performanceTier = MediaPerformanceTier.Mid,
                clockMs = { testScheduler.currentTime }
            )

            repeat(50) { cycle ->
                val emojiTargets = (0 until 20).map { idx ->
                    renderer.attach(
                        accountId = "acc-stress",
                        path = "/tmp/stress_emoji_${idx % 12}.tgs",
                        sizePx = 64,
                        playing = true,
                        priority = EmojiAnimationPolicy.PRIORITY_GRID
                    )
                }
                val stickerTargets = (0 until 6).map { idx ->
                    renderer.attach(
                        accountId = "acc-stress",
                        path = "/tmp/stress_sticker_$idx.tgs",
                        sizePx = 192,
                        playing = true,
                        priority = EmojiAnimationPolicy.PRIORITY_GRID
                    )
                }
                val gifHandles = (0 until 5).map {
                    gifBudget.acquire(EmojiAnimationPolicy.PRIORITY_GRID)
                }
                val previewHandle = if (cycle % 2 == 0) gifBudget.acquire(EmojiAnimationPolicy.PRIORITY_PREVIEW) else null

                runCurrent()
                advanceTimeBy(120L)
                runCurrent()

                // Simulate scroll pause.
                emojiTargets.forEach { renderer.updateTarget(it, playing = false, priority = EmojiAnimationPolicy.PRIORITY_GRID) }
                stickerTargets.forEach { renderer.updateTarget(it, playing = false, priority = EmojiAnimationPolicy.PRIORITY_GRID) }
                assertFalse(renderer.isSchedulerRunning())

                // Close picker: detach all targets and release all GIF slots.
                emojiTargets.forEach { renderer.detach(it) }
                stickerTargets.forEach { renderer.detach(it) }
                previewHandle?.let { gifBudget.release(it) }
                gifHandles.forEach { gifBudget.release(it) }
            }

            assertEquals(0, renderer.activeContextCount())
            assertEquals(0, renderer.totalTargetCount())
            assertFalse(renderer.isSchedulerRunning())
            assertEquals(0, gifBudget.activeGrantedCount())
            assertEquals(0, gifBudget.totalSlotCount())
            assertTrue(cache.firstFrameCount() > 0)
        } finally {
            scope.cancel()
        }
    }
}
