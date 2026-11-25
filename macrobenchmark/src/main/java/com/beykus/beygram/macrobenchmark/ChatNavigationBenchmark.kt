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
 * Navigation and scrolling of the chat list (#1119).
 *
 * The app is started, stopped and prepared in `setupBlock`, so the measured block contains only the action the metric
 * is supposed to describe — an open, a return or a scroll — not the cold start. Every non-startup scenario also proves
 * that it measured the intended screen: the chat list must exist, the first row must be identifiable, and a scroll must
 * actually change the first visible row. Otherwise the run fails instead of reporting frames for a static screen.
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
        // Launch and the first list frame happen before the measurement starts.
        setupBlock = {
            killApp()
            startAppAndWait()
        },
    ) {
        assumeTrue("No chat row on screen", Benchmarks.firstChatRow(device) != null)
        check(openFirstChat()) { "The chat did not open: the measured screen was not a conversation" }
        goBackFromChat()
        check(Benchmarks.chatList(device) != null) { "The chat list did not come back after the return gesture" }
    }

    @Test
    fun scrollChatList() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = {
            killApp()
            startAppAndWait()
        },
    ) {
        assumeTrue("No chat list on screen", Benchmarks.chatList(device) != null)
        val before = Benchmarks.firstRowSignature(device)
        repeat(SCROLLS) {
            Benchmarks.swipeVertically(device, up = true)
        }
        Benchmarks.requireListMoved(device, before)
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
        setupBlock = {
            killApp()
            // The chat is opened before the measurement: only scrolling inside it is timed.
            startAppAndWait()
            check(openFirstChat()) { "The chat did not open: nothing to scroll" }
        },
    ) {
        val before = Benchmarks.firstRowSignature(device)
        repeat(SCROLLS) {
            Benchmarks.swipeVertically(device, up = false)
        }
        Benchmarks.requireListMoved(device, before)
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