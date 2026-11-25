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
    private val fallbacks = AtomicInteger()

    fun recordShaderCreated() {
        shadersCreated.incrementAndGet()
    }

    fun recordCompileFailure() {
        compileFailures.incrementAndGet()
    }

    fun recordLensBuilt() {
        lensEffectsBuilt.incrementAndGet()
    }

    /** A panel asked for the lens but drew the plain blur (no size yet, or the effect could not be built). */
    fun recordFallback() {
        fallbacks.incrementAndGet()
    }

    fun snapshot(): Snapshot = Snapshot(
        shadersCreated = shadersCreated.get(),
        compileFailures = compileFailures.get(),
        lensEffectsBuilt = lensEffectsBuilt.get(),
        fallbacks = fallbacks.get()
    )

    data class Snapshot(
        val shadersCreated: Int,
        val compileFailures: Int,
        val lensEffectsBuilt: Int,
        val fallbacks: Int
    ) {
        override fun toString(): String =
            "shaders=$shadersCreated compileFailures=$compileFailures lensEffects=$lensEffectsBuilt fallbacks=$fallbacks"
    }
}
