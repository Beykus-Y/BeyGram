package com.beykus.beygram.data.media

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedTgsEmojiRendererTest {

    private fun fakeFrame(sizePx: Int = 64): CachedFrame {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        return CachedFrame(bmp.asImageBitmap(), sizePx * sizePx * 4)
    }

    @Test
    fun sharedContextDeduplicatesPreparationAndFramesAcrossMultipleTargets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var prepareCalls = 0
        val renderedProgresses = mutableListOf<Float>()
        try {
            val renderer = SharedTgsEmojiRenderer(
                scope = scope,
                workerDispatcher = dispatcher,
                frameCache = EmojiFrameCache(),
                rasterizer = object : EmojiFrameRasterizer {
                    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> {
                        prepareCalls++
                        return Result.success(
                            PreparedEmojiAnimation(
                                frameCount = 4,
                                durationMs = 400L,
                                renderFrame = { progress ->
                                    renderedProgresses += progress
                                    fakeFrame(sizePx)
                                }
                            )
                        )
                    }
                },
                clockMs = { testScheduler.currentTime }
            )
            val targets = (1..10).map {
                renderer.attach("acc-1", "/tmp/emoji.tgs", sizePx = 56, playing = true)
            }
            runCurrent()
            assertEquals(1, renderer.activeContextCount())
            assertEquals(10, renderer.totalTargetCount())
            assertEquals(1, prepareCalls)
            assertNotNull(targets.first().frameState.value)
            targets.forEach { target ->
                assertSame(targets.first().frameState, target.frameState)
            }
            advanceTimeBy(220L)
            runCurrent()
            assertTrue(renderedProgresses.size >= 2)
            targets.forEach { renderer.detach(it) }
            assertEquals(0, renderer.activeContextCount())
            assertEquals(0, renderer.totalTargetCount())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staticTargetsShowFirstFrameWithoutStartingScheduler() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val renderer = SharedTgsEmojiRenderer(
                scope = scope,
                workerDispatcher = dispatcher,
                rasterizer = object : EmojiFrameRasterizer {
                    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> =
                        Result.success(PreparedEmojiAnimation(10, 1000L) { fakeFrame(sizePx) })
                },
                clockMs = { testScheduler.currentTime }
            )
            val target = renderer.attach("acc-1", "/tmp/static.tgs", sizePx = 64, playing = false)
            runCurrent()
            assertNotNull(target.frameState.value)
            assertFalse(renderer.isSchedulerRunning())
            renderer.detach(target)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun detachingLastTargetReleasesContextAndStopsScheduler() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val renderer = SharedTgsEmojiRenderer(
                scope = scope,
                workerDispatcher = dispatcher,
                rasterizer = object : EmojiFrameRasterizer {
                    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> =
                        Result.success(PreparedEmojiAnimation(10, 1000L) { fakeFrame(sizePx) })
                },
                clockMs = { testScheduler.currentTime }
            )
            val first = renderer.attach("acc-1", "/tmp/a.tgs", sizePx = 64, playing = true)
            val second = renderer.attach("acc-1", "/tmp/a.tgs", sizePx = 64, playing = true)
            runCurrent()
            assertTrue(renderer.isSchedulerRunning())

            renderer.detach(first)
            assertEquals(1, renderer.activeContextCount())
            assertTrue(renderer.isSchedulerRunning())

            renderer.detach(second)
            assertEquals(0, renderer.activeContextCount())
            assertEquals(0, renderer.totalTargetCount())
            assertFalse(renderer.isSchedulerRunning())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun frameCacheEnforcesByteBudgetAndIsolatesAccounts() {
        val frameBytes = 64 * 64 * 4
        val cache = EmojiFrameCache(
            firstFrameBudgetBytes = frameBytes * 2,
            frameBudgetBytes = frameBytes * 2
        )
        val key1 = EmojiRenderKey("acc-1", "/tmp/1.tgs", 10L, 1L, 64)
        val key2 = EmojiRenderKey("acc-1", "/tmp/2.tgs", 10L, 1L, 64)
        val key3 = EmojiRenderKey("acc-2", "/tmp/3.tgs", 10L, 1L, 64)

        cache.putFirstFrame(key1, fakeFrame(64))
        cache.putFirstFrame(key2, fakeFrame(64))
        cache.putFirstFrame(key3, fakeFrame(64))
        assertEquals(2, cache.firstFrameCount())
        assertNull(cache.getFirstFrame(key1))
        assertNotNull(cache.getFirstFrame(key2))
        assertNotNull(cache.getFirstFrame(key3))

        cache.putFrame(key2, 1, fakeFrame(64))
        cache.putFrame(key3, 1, fakeFrame(64))
        cache.clearAccount("acc-1")
        assertNull(cache.getFirstFrame(key2))
        assertNull(cache.getFrame(key2, 1))
        assertNotNull(cache.getFirstFrame(key3))
        assertNotNull(cache.getFrame(key3, 1))
    }

    @Test
    fun adaptiveFpsAndActiveBudgetRespectDeviceTierAndPriority() {
        assertEquals(30, adaptiveEmojiFps(activePlayingContexts = 8, lowRam = false))
        assertEquals(24, adaptiveEmojiFps(activePlayingContexts = 20, lowRam = false))
        assertEquals(15, adaptiveEmojiFps(activePlayingContexts = 32, lowRam = false))

        assertEquals(24, adaptiveEmojiFps(activePlayingContexts = 8, lowRam = true))
        assertEquals(18, adaptiveEmojiFps(activePlayingContexts = 14, lowRam = true))
        assertEquals(12, adaptiveEmojiFps(activePlayingContexts = 20, lowRam = true))

        assertEquals(16, maxConcurrentEmojiAnimations(lowRam = false))
        assertEquals(10, maxConcurrentEmojiAnimations(lowRam = true))
        assertEquals(64, bucketEmojiSizePx(56))
    }

    @Test
    fun repeatedOpenCloseCyclesLeaveZeroLeakedContexts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val renderer = SharedTgsEmojiRenderer(
                scope = scope,
                workerDispatcher = dispatcher,
                rasterizer = object : EmojiFrameRasterizer {
                    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> =
                        Result.success(PreparedEmojiAnimation(8, 800L) { fakeFrame(sizePx) })
                },
                clockMs = { testScheduler.currentTime }
            )
            repeat(20) { cycle ->
                val batch = (0 until 40).map { idx ->
                    val path = "/tmp/emoji_${idx % 20}.tgs"
                    renderer.attach("acc-${cycle % 2}", path, sizePx = 64, playing = true)
                }
                runCurrent()
                advanceTimeBy(40L)
                runCurrent()
                batch.forEach { renderer.detach(it) }
            }
            assertEquals(0, renderer.activeContextCount())
            assertEquals(0, renderer.totalTargetCount())
            assertFalse(renderer.isSchedulerRunning())
        } finally {
            scope.cancel()
        }
    }
}
