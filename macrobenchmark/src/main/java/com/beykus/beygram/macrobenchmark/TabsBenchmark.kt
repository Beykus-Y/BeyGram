package com.beykus.beygram.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching between the main tabs (#1119, Performance 2.0). The app is launched in `setupBlock`; the measured block only
 * taps a tab and waits for its content, so the number describes the switch and not the cold start.
 *
 * Tab labels come from the bottom navigation of the current UI. A missing tab fails the run with a clear message
 * instead of measuring an unchanged screen.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class TabsBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = Benchmarks.targetPackage()

    @Test
    fun switchBetweenTabs() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = {
            killApp()
            startAppAndWait()
        },
    ) {
        // The chats tab is the starting point: the benchmark only switches away and back.
        for (label in listOf(TAB_SETTINGS, TAB_CHATS)) {
            val tab = device.findObject(By.text(label))
            check(tab != null) { "Tab '$label' was not found: the measured screen is not the main tab host" }
            tab.click()
            device.waitForIdle()
        }
        // A scroll inside the chats list proves the switch actually landed on it.
        val list = Benchmarks.chatList(device)
        check(list != null) { "The chats list did not come back after switching tabs" }
        list.setGestureMargin(device.displayWidth / 5)
        list.scroll(Direction.DOWN, 0.6f)
        list.scroll(Direction.UP, 0.6f)
        device.waitForIdle()
    }

    private companion object {
        const val ITERATIONS = 5
        const val TAB_CHATS = "Чаты"
        const val TAB_SETTINGS = "Настройки"
    }
}