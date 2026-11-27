package com.beykus.beygram.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Shared navigation for the BeyGram benchmarks (#1119).
 *
 * The app is Compose-only, so there are no `android:id` selectors to click by name. The helpers find the chat list as
 * the first scrollable container and pick the first clickable row inside it. They are heuristics on purpose: a device
 * run is the only way to confirm them, and the header/row constants are the expected place to adjust if the layout
 * changes. Nothing here edits account data; only the UI is driven, and nothing is sent anywhere.
 */
internal object Benchmarks {
    /**
     * The package the benchmark runs against, passed explicitly:
     * `-Pandroid.testInstrumentationRunnerArguments.targetPackage=com.beykus.beygram.dev.benchmark`.
     *
     * Neither the Gradle runner nor `targetContext` is trusted for this: a self-instrumenting `com.android.test`
     * module can resolve them to the test APK, and a wrong value would silently measure the owner's Dev or Stable
     * build. The argument is therefore mandatory and must name an isolated benchmark package; anything else fails
     * before a single frame is measured.
     */
    fun targetPackage(): String {
        val explicit = InstrumentationRegistry.getArguments().getString("targetPackage")
            ?.takeIf { it.isNotBlank() }
            ?: error(
                "targetPackage is required: pass " +
                    "-Pandroid.testInstrumentationRunnerArguments.targetPackage=com.beykus.beygram.dev.benchmark " +
                    "(or the .benchmark package of the product flavour under test)."
            )
        require(explicit.endsWith(BENCHMARK_PACKAGE_SUFFIX)) {
            "Refusing to benchmark '$explicit': only the isolated '*$BENCHMARK_PACKAGE_SUFFIX' package may be measured."
        }
        return explicit
    }

    /** The suffix every benchmark build type carries; Dev and Stable never have it. */
    const val BENCHMARK_PACKAGE_SUFFIX = ".benchmark"

    /** A silent skip used to hide an empty benchmark package; a run without a chat must fail with this text instead. */
    const val NO_ACCOUNT_MESSAGE =
        "No chat is visible in the benchmark package. The benchmark build has its own empty TDLib storage: " +
            "sign in to com.beykus.beygram.dev.benchmark before measuring, or use a synthetic fixture " +
            "(see docs/performance-2.0-audit.md, section 4.6). A skipped measurement is not a green result."

    /** The chat list: the first scrollable container on screen. */
    fun chatList(device: UiDevice): UiObject2? = device.findObject(By.scrollable(true))

    /**
     * First chat row. Compose merges each row into one clickable node; the topmost clickable node below the header is
     * the first conversation. Null means the list is empty or still loading, and the caller decides what to do.
     */
    fun firstChatRow(device: UiDevice): UiObject2? {
        val list = chatList(device) ?: return null
        val headerBottom = (device.displayHeight * HEADER_HEIGHT_FRACTION).toInt()
        val minRowHeight = (device.displayHeight * MIN_ROW_HEIGHT_FRACTION).toInt()
        return device.findObjects(By.clickable(true))
            .asSequence()
            .filter { node -> node.visibleBounds.height() >= minRowHeight }
            .filter { node -> node.visibleBounds.top >= headerBottom }
            .filter { node -> node.visibleBounds.top >= list.visibleBounds.top }
            .minByOrNull { node -> node.visibleBounds.top }
    }

    /**
     * A stable identifier of the first visible row: its text. Used only to prove that a scroll really moved the list,
     * not to navigate. Compose has no android:id, so the row text is the strongest available anchor.
     */
    fun firstRowSignature(device: UiDevice): String? = firstChatRow(device)?.text?.toString()?.takeIf { it.isNotBlank() }

    /** Fails the measurement instead of reporting frames for a list that never moved. */
    fun requireListMoved(device: UiDevice, before: String?) {
        val after = firstRowSignature(device)
        check(before != null && after != null && before != after) {
            "The chat list did not move: the measurement would describe a static screen"
        }
    }

    /** A vertical swipe over the screen, expressed in fractions so it works on any density. */
    fun swipeVertically(device: UiDevice, up: Boolean) {
        val width = device.displayWidth
        val height = device.displayHeight
        val x = width / 2
        val from = if (up) (height * 0.72f).toInt() else (height * 0.32f).toInt()
        val to = if (up) (height * 0.32f).toInt() else (height * 0.72f).toInt()
        device.swipe(x, from, x, to, SWIPE_STEPS)
        device.waitForIdle()
    }

    internal const val HEADER_HEIGHT_FRACTION = 0.22f
    private const val MIN_ROW_HEIGHT_FRACTION = 0.045f
    private const val SWIPE_STEPS = 12
}

internal const val LAUNCH_TIMEOUT_MS = 10_000L
internal const val OPEN_CHAT_TIMEOUT_MS = 5_000L

/** A cold start must not come from a task snapshot, so the process is stopped and the launcher is shown first. */
internal fun MacrobenchmarkScope.killApp() {
    pressHome()
    device.executeShellCommand("am force-stop $packageName")
    device.waitForIdle()
    Thread.sleep(1_000)
}

/** Starts the app and waits until the chat list exists, so navigation timing does not include the splash. */
internal fun MacrobenchmarkScope.startAppAndWait() {
    startActivityAndWait()
    device.wait(Until.hasObject(By.scrollable(true)), LAUNCH_TIMEOUT_MS)
}

/** Opens the first conversation. Returns false when no row could be found, so scenarios can skip instead of failing. */
internal fun MacrobenchmarkScope.openFirstChat(): Boolean {
    val row = Benchmarks.firstChatRow(device) ?: return false
    row.click()
    device.waitForIdle()
    return device.wait(Until.hasObject(By.clazz("android.widget.EditText")), OPEN_CHAT_TIMEOUT_MS) ||
        device.wait(Until.hasObject(By.focused(true)), OPEN_CHAT_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.goBackFromChat() {
    device.pressBack()
    device.waitForIdle()
    device.wait(Until.hasObject(By.scrollable(true)), OPEN_CHAT_TIMEOUT_MS)
}