package com.beykus.beygram.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching between the main tabs (#1119, Performance 2.0).
 *
 * The app is launched and brought to the chats tab in `setupBlock`; the measured block only taps a tab. No scrolling or
 * other work happens inside the measurement: a scroll would describe the list, not the switch (Codex review).
 *
 * Each switch is verified by the semantic *selected* state of the tab itself, which is what the target screen looks
 * like from the screen reader's point of view. Labels come from the app resources for the reference locale; a device
 * with another UI language can override them with
 * `-Pandroid.testInstrumentationRunnerArguments.tabChats=... -Pandroid.testInstrumentationRunnerArguments.tabSettings=...`.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class TabsBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = Benchmarks.targetPackage()
    private val args = InstrumentationRegistry.getArguments()
    private val tabChats = args.getString("tabChats") ?: "Сообщения"
    private val tabSettings = args.getString("tabSettings") ?: "Настройки"

    @Test
    fun switchToSettingsAndBack() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = ITERATIONS,
        setupBlock = {
            killApp()
            startAppAndWait()
            selectTab(tabChats)
        },
    ) {
        selectTab(tabSettings)
        selectTab(tabChats)
    }

    /**
     * Taps [label] and verifies that the tab reports itself as selected afterwards. Without this check a missed tap
     * would silently measure an unchanged screen.
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.selectTab(label: String) {
        val tab = device.findObject(By.text(label))
        check(tab != null) { "Tab '$label' was not found: pass tabChats/tabSettings arguments for this UI language" }
        tab.click()
        device.waitForIdle()
        check(tab.isSelected) { "Tab '$label' did not become the selected screen" }
    }

    private companion object {
        const val ITERATIONS = 5
    }
}