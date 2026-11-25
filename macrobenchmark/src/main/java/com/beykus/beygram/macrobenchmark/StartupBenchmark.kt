package com.beykus.beygram.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start of BeyGram (#1119).
 *
 * `StartupTimingMetric` reports time-to-initial-display and time-to-full-display for each iteration; the benchmark
 * library prints P50/P90/P95/P99 and min/max, which is what the issue asks to record. Five iterations, identical build
 * type and device for every run.
 *
 * `CompilationMode.None()` measures the build as it arrives on the device, which is what a user gets right after an
 * update; `CompilationMode.Partial` is the "warmed" reading. Both are reported separately and never mixed.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = Benchmarks.targetPackage()

    @Test
    fun coldStartupNoCompilation() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { killApp() },
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun coldStartupWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(StartupTimingMetric()),
        // Requires a generated profile inside the APK; the run fails loudly instead of silently comparing two identical
        // builds, which is exactly the mistake this issue exists to prevent.
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { killApp() },
    ) {
        pressHome()
        startActivityAndWait()
    }

    /**
     * Warm start: the process is alive but the Activity is not, which is what a user gets after switching away and
     * coming back. It is a different number from cold start and must never be reported as one.
     */
    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        // WARM expects the activity to be created already: the setup opens and closes it once.
        setupBlock = {
            startActivityAndWait()
            pressHome()
            device.waitForIdle()
        },
    ) {
        pressHome()
        startActivityAndWait()
    }

    /**
     * Hot start: the Activity is still alive in the background. Measures only bringing it to the foreground.
     */
    @Test
    fun hotStartup() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.HOT,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            device.waitForIdle()
        },
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        /** The issue requires at least five comparable runs per variant. */
        const val ITERATIONS = 5
    }
}