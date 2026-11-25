package com.beykus.beygram.ui.design

import java.util.concurrent.atomic.AtomicInteger

/**
 * Counters for the Liquid Glass V2 lens, for diagnosis on a device. Only counts are kept: no content, no sizes of the
 * user's screens. Read them with [snapshot] or from logcat under [LogTag] (compile failures are logged there).
 */
internal object LiquidGlassMetrics {
    const val LogTag = "LiquidGlassV2"

    private val shadersCreated = AtomicInteger()
    private val compileFailures = AtomicInteger()
    private val lensEffectsBuilt = AtomicInteger()
    private val fallbackFrames = AtomicInteger()

    fun recordShaderCreated() {
        shadersCreated.incrementAndGet()
    }

    fun recordCompileFailure() {
        compileFailures.incrementAndGet()
    }

    fun recordLensBuilt() {
        lensEffectsBuilt.incrementAndGet()
    }

    /**
     * A frame in which a panel that asked for the lens drew the plain blur instead: no shader, no size yet, the panel is
     * still animating its size, or the effect could not be built. Counted per frame, so it shows how often the lens is
     * actually not on screen.
     */
    fun recordFallbackFrame() {
        fallbackFrames.incrementAndGet()
    }

    fun snapshot(): Snapshot = Snapshot(
        shadersCreated = shadersCreated.get(),
        compileFailures = compileFailures.get(),
        lensEffectsBuilt = lensEffectsBuilt.get(),
        fallbackFrames = fallbackFrames.get()
    )

    data class Snapshot(
        val shadersCreated: Int,
        val compileFailures: Int,
        val lensEffectsBuilt: Int,
        val fallbackFrames: Int
    ) {
        override fun toString(): String =
            "shaders=$shadersCreated compileFailures=$compileFailures lensEffects=$lensEffectsBuilt fallbackFrames=$fallbackFrames"
    }
}
