package com.beykus.beygram.data.media

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.beykus.beygram.ui.screens.chat.EmojiAnimationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnifiedAnimationBudgetTest {

    private fun fakeFrame(sizePx: Int = 64): CachedFrame {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        return CachedFrame(bmp.asImageBitmap(), sizePx * sizePx * 4)
    }

    @Test
    fun deviceTierAndCostHelpersClassifyMidLowHighCorrectly() {
        assertEquals(MediaPerformanceTier.Low, detectMediaPerformanceTier(isLowRam = true, memoryClassMb = 512, cpuCores = 8, powerSaveMode = false))
        assertEquals(MediaPerformanceTier.Low, detectMediaPerformanceTier(isLowRam = false, memoryClassMb = 256, cpuCores = 8, powerSaveMode = true))
        assertEquals(MediaPerformanceTier.Low, detectMediaPerformanceTier(isLowRam = false, memoryClassMb = 256, cpuCores = 4, powerSaveMode = false))
        assertEquals(MediaPerformanceTier.Mid, detectMediaPerformanceTier(isLowRam = false, memoryClassMb = 256, cpuCores = 8, powerSaveMode = false))
        assertEquals(MediaPerformanceTier.High, detectMediaPerformanceTier(isLowRam = false, memoryClassMb = 512, cpuCores = 8, powerSaveMode = false))

        assertEquals(1, animationCostUnits(48))
        assertEquals(1, animationCostUnits(64))
        assertEquals(2, animationCostUnits(96))
        assertEquals(3, animationCostUnits(128))
        assertEquals(3, animationCostUnits(192))
        assertEquals(4, animationCostUnits(256))

        assertEquals(24, adaptiveEmojiFps(activeWeightUnits = 6, tier = MediaPerformanceTier.Mid))
        assertEquals(18, adaptiveEmojiFps(activeWeightUnits = 12, tier = MediaPerformanceTier.Mid))
        assertEquals(12, adaptiveEmojiFps(activeWeightUnits = 20, tier = MediaPerformanceTier.Mid))
    }

    @Test
    fun weightedBudgetAndPriorityPreemptionLimitConcurrentLargeStickers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val renderedPaths = mutableSetOf<String>()
        try {
            val renderer = SharedTgsEmojiRenderer(
                scope = scope,
                mainDispatcher = dispatcher,
                schedulerDispatcher = dispatcher,
                workerDispatcher = dispatcher,
                frameCache = EmojiFrameCache(),
                rasterizer = object : EmojiFrameRasterizer {
                    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> {
                        return Result.success(
                            PreparedEmojiAnimation(
                                frameCount = 5,
                                durationMs = 500L,
                                renderFrame = { progress ->
                                    if (progress > 0f) renderedPaths += path
                                    fakeFrame(sizePx)
                                }
                            )
                        )
                    }
                },
                lowRam = true,
                performanceTier = MediaPerformanceTier.Low,
                clockMs = { testScheduler.currentTime }
            )

            // Low tier budget = 10 units. 4 stickers at 192px (cost 3 each = 12) -> only 3 play concurrently.
            val gridTargets = (1..4).map { idx ->
                renderer.attach(
                    accountId = "acc-1",
                    path = "/tmp/sticker-$idx.tgs",
                    sizePx = 192,
                    playing = true,
                    priority = EmojiAnimationPolicy.PRIORITY_GRID
                )
            }
            // High-priority preview sticker (cost 3) should preempt one of the grid stickers.
            val previewTarget = renderer.attach(
                accountId = "acc-1",
                path = "/tmp/sticker-preview.tgs",
                sizePx = 192,
                playing = true,
                priority = EmojiAnimationPolicy.PRIORITY_PREVIEW
            )
            runCurrent()
            advanceTimeBy(220L)
            runCurrent()

            assertTrue(renderedPaths.contains("/tmp/sticker-preview.tgs"))
            assertTrue(renderedPaths.size <= 3)

            // Pausing all targets stops the scheduler immediately.
            gridTargets.forEach { renderer.updateTarget(it, playing = false, priority = EmojiAnimationPolicy.PRIORITY_GRID) }
            renderer.updateTarget(previewTarget, playing = false, priority = EmojiAnimationPolicy.PRIORITY_PREVIEW)
            assertFalse(renderer.isSchedulerRunning())

            gridTargets.forEach { renderer.detach(it) }
            renderer.detach(previewTarget)
            assertEquals(0, renderer.activeContextCount())
            assertEquals(0, renderer.totalTargetCount())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun gifAnimationBudgetCapsConcurrentSlotsPreemptsAndPromotesOnRelease() {
        val budget = GifAnimationBudget(maxConcurrent = 2)
        val grid1 = budget.acquire(EmojiAnimationPolicy.PRIORITY_GRID)
        val grid2 = budget.acquire(EmojiAnimationPolicy.PRIORITY_GRID)
        val grid3 = budget.acquire(EmojiAnimationPolicy.PRIORITY_GRID)

        assertTrue(grid1.granted.value)
        assertTrue(grid2.granted.value)
        assertFalse(grid3.granted.value)
        assertEquals(2, budget.activeGrantedCount())

        // Preview preempts lower-priority grid slot.
        val preview = budget.acquire(EmojiAnimationPolicy.PRIORITY_PREVIEW)
        assertTrue(preview.granted.value)
        assertTrue(grid1.granted.value)
        assertFalse(grid2.granted.value)
        assertEquals(2, budget.activeGrantedCount())

        // Releasing preview promotes grid2 back.
        budget.release(preview)
        assertTrue(grid1.granted.value)
        assertTrue(grid2.granted.value)
        assertFalse(grid3.granted.value)

        budget.release(grid1)
        budget.release(grid2)
        budget.release(grid3)
        assertEquals(0, budget.activeGrantedCount())
        assertEquals(0, budget.totalSlotCount())
    }
}
