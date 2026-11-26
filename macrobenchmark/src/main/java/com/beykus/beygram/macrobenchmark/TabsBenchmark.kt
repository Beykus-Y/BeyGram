package com.beykus.beygram.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
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
 * like from the screen reader's point of view.
 *
 * Defaults follow the reference locale of the base APK: the chats tab is `tabs_chats` and the settings tab is
 * `settings_title` in `res/values/strings.xml`. Both can be overridden for a device with another UI language with
 * `-Pandroid.testInstrumentationRunnerArguments.tabChats=... -Pandroid.testInstrumentationRunnerArguments.tabSettings=...`.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class TabsBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = Benchmarks.targetPackage()
    private val args = InstrumentationRegistry.getArguments()
    private val tabChats = args.getString("tabChats") ?: "Чаты"
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
     * Taps the tab labelled [label] and verifies that it reports itself as selected afterwards. Without this check a
     * missed tap would silently measure an unchanged screen.
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.selectTab(label: String) {
        val tab = findTab(label)
        tab.click()
        device.waitForIdle()
        check(tab.isSelected) { "Tab '$label' did not become the selected screen" }
    }

    /**
     * Compose exposes the dock as one accessibility node per tab with `selected` semantics; when the label is hidden
     * (narrow window, dock label off) the same node carries the title in `contentDescription`, so both are tried.
     *
     * A tab title may also appear in the screen header (Settings), and Compose merges the tab's children, so several
     * nodes can carry the text. The navigation dock sits below the content, hence the lowest candidate is the tab.
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.findTab(label: String): UiObject2 {
        val candidates = device.findObjects(By.text(label)).ifEmpty { device.findObjects(By.desc(label)) }
        check(candidates.isNotEmpty()) {
            "Tab '$label' was not found: pass tabChats/tabSettings arguments for this UI language"
        }
        return candidates.maxByOrNull { it.visibleBounds.bottom }
            ?: error("Tab '$label' was not found")
    }

    private companion object {
        const val ITERATIONS = 5
    }
}