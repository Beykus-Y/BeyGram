package com.beykus.beygram.data.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import android.os.PowerManager
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The animation budget of 0.0.7.2 follows battery saver while the app runs. Both the emoji renderer and the GIF budget
 * used to read the performance tier once, when their process-wide instance was created, so the old budget stayed in
 * place until the process was killed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPerformanceTierSourceTest {

    private fun fakeFrame(sizePx: Int = 64): CachedFrame {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        return CachedFrame(bmp.asImageBitmap(), sizePx * sizePx * 4)
    }

    @Test
    fun aTierChangeIsReportedOnlyWhenTheTierActuallyChanges() {
        val changes = mutableListOf<MediaPerformanceTier>()
        val source = MediaPerformanceTierSource(
            isLowRam = false,
            memoryClassMb = 512,
            cpuCores = 8,
            powerSaveMode = false,
            onChanged = { changes += it }
        )
        assertEquals(MediaPerformanceTier.High, source.tier)

        assertEquals(MediaPerformanceTier.Low, source.updatePowerSaveMode(true))
        assertEquals(MediaPerformanceTier.Low, source.updatePowerSaveMode(true))
        assertEquals(MediaPerformanceTier.High, source.updatePowerSaveMode(false))

        assertEquals(listOf(MediaPerformanceTier.Low, MediaPerformanceTier.High), changes)
    }

    @Test
    fun theBatterySaverBroadcastRetunesTheTier() {
        val context = RuntimeEnvironment.getApplication()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        Shadows.shadowOf(powerManager).setIsPowerSaveMode(false)
        val changes = mutableListOf<MediaPerformanceTier>()
        val source = MediaPerformanceTierSource(
            isLowRam = false,
            memoryClassMb = 512,
            cpuCores = 8,
            powerSaveMode = false,
            onChanged = { changes += it }
        )
        source.follow(context, powerManager)
        assertFalse("the receiver must be registered once per process", source.follow(context, powerManager))
        assertEquals(MediaPerformanceTier.High, source.tier)

        // Robolectric delivers a broadcast on the main looper, so it is idled before the receiver is expected to run.
        Shadows.shadowOf(powerManager).setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(MediaPerformanceTier.Low, source.tier)

        Shadows.shadowOf(powerManager).setIsPowerSaveMode(false)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(MediaPerformanceTier.High, source.tier)
        assertEquals(listOf(MediaPerformanceTier.Low, MediaPerformanceTier.High), changes)
    }

    @Test
    fun theGifBudgetRegrantsSlotsWhenTheTierChanges() {
        val budget = GifAnimationBudget(maxConcurrent = 4)
        val tiles = (1..4).map { budget.acquire(EmojiAnimationPolicy.PRIORITY_GRID) }
        assertTrue(tiles.all { it.granted.value })

        // Battery saver: the low tier allows two tiles; the ones that no longer fit lose their grant at once.
        budget.updateMaxConcurrent(maxConcurrentGifAnimations(MediaPerformanceTier.Low))
        assertEquals(2, budget.activeGrantedCount())
        assertTrue(tiles[0].granted.value)
        assertTrue(tiles[1].granted.value)
        assertFalse(tiles[2].granted.value)
        assertFalse(tiles[3].granted.value)

        // Leaving battery saver promotes the waiting tiles without a new acquisition.
        budget.updateMaxConcurrent(maxConcurrentGifAnimations(MediaPerformanceTier.High))
        assertEquals(4, budget.activeGrantedCount())

        tiles.forEach { budget.release(it) }
        assertEquals(0, budget.totalSlotCount())
    }

    @Test
    fun theSchedulerFollowsATierChangeWithoutARestart() = runTest {
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
                    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> =
                        Result.success(
                            PreparedEmojiAnimation(
                                frameCount = 5,
                                durationMs = 500L,
                                renderFrame = { progress ->
                                    if (progress > 0f) renderedPaths += path
                                    fakeFrame(sizePx)
                                }
                            )
                        )
                },
                performanceTier = MediaPerformanceTier.High,
                clockMs = { testScheduler.currentTime }
            )
            // Four 192px stickers weigh 12 units: they fit the High budget (16) but not the Low one (10).
            val targets = (1..4).map { index ->
                renderer.attach(
                    accountId = "acc-1",
                    path = "/tmp/tier-$index.tgs",
                    sizePx = 192,
                    playing = true,
                    priority = EmojiAnimationPolicy.PRIORITY_GRID
                )
            }
            renderer.updatePerformanceTier(MediaPerformanceTier.Low)
            assertEquals(MediaPerformanceTier.Low, renderer.currentPerformanceTier())
            runCurrent()
            advanceTimeBy(220L)
            runCurrent()
            assertEquals(3, renderedPaths.size)

            renderer.updatePerformanceTier(MediaPerformanceTier.High)
            runCurrent()
            advanceTimeBy(220L)
            runCurrent()
            assertEquals(4, renderedPaths.size)

            targets.forEach { renderer.detach(it) }
            assertEquals(0, renderer.activeContextCount())
        } finally {
            scope.cancel()
        }
    }
}