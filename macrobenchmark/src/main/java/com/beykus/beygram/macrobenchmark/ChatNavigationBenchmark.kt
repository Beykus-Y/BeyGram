package com.beykus.beygram.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation and scrolling of the chat list (#1119): opening a conversation, returning to the list and scrolling both
 * lists. `FrameTimingMetric` reports frame duration, so the output carries jank and P50/P95/P99 per frame.
 *
 * These scenarios need at least one conversation in the signed-in account. Without chats the test is skipped instead of
 * measuring an empty state.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ChatNavigationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = Benchmarks.targetPackage()

    @Test
    fun openChatAndReturn() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = { killApp() },
    ) {
        startAppAndWait()
        // A list with no rows cannot be measured; the assumption fails the run visibly rather than silently passing.
        assumeTrue("No chat row on screen", openFirstChat())
        goBackFromChat()
    }

    @Test
    fun scrollChatList() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = { killApp() },
    ) {
        startAppAndWait()
        assumeTrue("No chat list on screen", Benchmarks.chatList(device) != null)
        repeat(SCROLLS) {
            Benchmarks.swipeVertically(device, up = true)
        }
        repeat(SCROLLS) {
            Benchmarks.swipeVertically(device, up = false)
        }
    }

    @Test
    fun scrollMessageHistory() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = { killApp() },
    ) {
        startAppAndWait()
        assumeTrue("No chat could be opened", openFirstChat())
        repeat(SCROLLS) {
            Benchmarks.swipeVertically(device, up = false)
        }
        repeat(SCROLLS) {
            Benchmarks.swipeVertically(device, up = true)
        }
    }

    private companion object {
        const val ITERATIONS = 5
        /** Enough to leave the first screen on a phone-sized display and come back. */
        const val SCROLLS = 4
    }
}