package com.beykus.beygram.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import android.util.Log
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Produces the Baseline Profile (#1119). This test writes the profile; it does not measure anything and its numbers, if
 * any, must never be reported as a performance result.
 *
 * The generated file is what `profileinstaller` compiles ahead of the first launch. It is produced only by running this
 * on a physical device — no profile is committed to the repository before that run.
 *
 * Run:
 * ```
 * gradlew :app:generateBenchmarkDevBaselineProfile
 * ```
 * or directly (with a device connected):
 * ```
 * gradlew :macrobenchmark:connectedBenchmarkDevAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.macrobenchmark.BaselineProfileGenerator
 * ```
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val packageName = Benchmarks.targetPackage()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(
        packageName = packageName,
        // The generator keeps calling the block until the profile stops growing; these bounds keep a broken UI from
        // burning device time forever.
        maxIterations = 12,
        stableIterations = 3,
        includeInStartupProfile = true,
    ) {
        // Cold start is the single most valuable part of the profile: it gates the first frame after an update.
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // The chat list is where every session starts.
        Benchmarks.chatList(device)?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            repeat(2) { list.scroll(androidx.test.uiautomator.Direction.DOWN, 0.8f) }
            device.waitForIdle()
            repeat(2) { list.scroll(androidx.test.uiautomator.Direction.UP, 0.8f) }
        }

        // Opening a chat and coming back exercises the composer, the message list and the transition back.
        if (openFirstChat()) {
            repeat(2) { Benchmarks.swipeVertically(device, up = false) }
            repeat(2) { Benchmarks.swipeVertically(device, up = true) }
            device.waitForIdle()
            goBackFromChat()
        } else {
            Log.i("BeyGramProfile", "No chat row found; the profile covers startup and the chat list only.")
        }
    }
}