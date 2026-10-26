package com.beykus.beygram.playback

import android.media.audiofx.Equalizer
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Frequency anchors used by BeyGram's presets. They are only a description of the desired curve: every anchor is
 * mapped onto the bands the current device actually reports, so a five-band phone never renders ten fake sliders.
 */
internal val EqualizerPresetAnchorFrequenciesHz = listOf(31, 63, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

private fun flatCurveDb() = List(EqualizerPresetAnchorFrequenciesHz.size) { 0f }

/**
 * Preset gains in decibels. Boosts stay at or below [EQUALIZER_MAX_BOOST_MB] so a preset cannot ask the platform
 * equalizer for a much hotter signal than the safety limit allows on its own.
 */
private val EqualizerPresetCurvesDb: Map<EqualizerPreset, List<Float>> = mapOf(
    EqualizerPreset.Flat to flatCurveDb(),
    EqualizerPreset.Bass to listOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
    EqualizerPreset.Vocal to listOf(-2f, -1f, 0f, 1f, 2f, 3f, 4f, 2f, 1f, 0f),
    EqualizerPreset.Pop to listOf(-1f, 0f, 1f, 2f, 2f, 1f, 1f, 2f, 2f, 1f),
    EqualizerPreset.Rock to listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 1f, 2f, 3f),
    EqualizerPreset.Electronic to listOf(5f, 4f, 2f, 0f, -1f, 1f, 2f, 3f, 4f, 4f),
    EqualizerPreset.Classical to listOf(3f, 2f, 1f, 0f, 0f, 0f, 1f, 2f, 3f, 3f),
    EqualizerPreset.Jazz to listOf(3f, 2f, 1f, 1f, 0f, 1f, 2f, 2f, 2f, 1f)
)

/** The platform range is respected, but positive gain is capped to avoid clipping when several bands boost together. */
internal const val EQUALIZER_MAX_BOOST_MB = 600

/** Band frequencies, the real hardware range and system preset names reported by one attached effect. */
data class EqualizerCapabilities(
    val frequenciesHz: List<Int>,
    val hardwareMinLevelMb: Int,
    val hardwareMaxLevelMb: Int,
    val systemPresets: List<String> = emptyList()
) {
    init {
        require(frequenciesHz.isNotEmpty()) { "An equalizer without bands cannot be controlled" }
        require(hardwareMaxLevelMb > hardwareMinLevelMb) { "Invalid equalizer level range" }
        require(minOf(hardwareMaxLevelMb, EQUALIZER_MAX_BOOST_MB) > hardwareMinLevelMb) {
            "Equalizer level range has no usable span after the clipping guard"
        }
    }

    val numberOfBands: Int get() = frequenciesHz.size
    val minLevelMb: Int get() = hardwareMinLevelMb
    val maxLevelMb: Int get() = minOf(hardwareMaxLevelMb, EQUALIZER_MAX_BOOST_MB)

    /** True when the platform could boost more than BeyGram deliberately allows. */
    val boostIsLimited: Boolean get() = hardwareMaxLevelMb > maxLevelMb

    fun clampLevelMb(levelMb: Int): Int = levelMb.coerceIn(minLevelMb, maxLevelMb)

    /** The effective levels represented by [settings], one entry per real band, always in millibels. */
    fun levelsFor(settings: EqualizerSettings): List<Int> {
        val levelsMb = when (settings.selectedPreset) {
            EqualizerPreset.Custom -> customLevelsMb(frequenciesHz, settings.customLevelsMb)
            else -> presetLevelsMb(settings.selectedPreset, frequenciesHz)
        }
        return levelsMb.map(::clampLevelMb)
    }
}

/** Resolves a preset curve against actual device frequencies. */
internal fun presetLevelsMb(preset: EqualizerPreset, frequenciesHz: List<Int>): List<Int> {
    if (preset == EqualizerPreset.Custom) return List(frequenciesHz.size) { 0 }
    val curve = EqualizerPresetCurvesDb[preset] ?: flatCurveDb()
    val anchors = EqualizerPresetAnchorFrequenciesHz.zip(curve)
    return frequenciesHz.map { frequency -> (interpolateLog(anchors, frequency) * 100f).roundToInt() }
}

/** Resolves a saved frequency-keyed custom curve against a possibly different band layout. */
internal fun customLevelsMb(frequenciesHz: List<Int>, customLevelsMb: Map<Int, Int>): List<Int> {
    if (customLevelsMb.isEmpty()) return List(frequenciesHz.size) { 0 }
    val points = customLevelsMb.entries
        .filter { (frequency, level) -> frequency > 0 && level in EQUALIZER_MIN_STORED_LEVEL_MB..EQUALIZER_MAX_STORED_LEVEL_MB }
        .sortedBy { it.key }
        .map { (frequency, level) -> frequency to level.toFloat() }
    if (points.isEmpty()) return List(frequenciesHz.size) { 0 }
    return frequenciesHz.map { frequency -> interpolateLog(points, frequency).roundToInt() }
}

/** Interpolates in log-frequency space; that is how octave-spaced bands and the preset anchors line up. */
private fun interpolateLog(points: List<Pair<Int, Float>>, frequencyHz: Int): Float {
    if (points.isEmpty()) return 0f
    if (points.size == 1) return points.first().second
    val frequency = frequencyHz.coerceAtLeast(1)
    val x = log10(frequency.toFloat())
    val first = points.first()
    if (x <= log10(first.first.toFloat())) return first.second
    for (index in 0 until points.lastIndex) {
        val left = points[index]
        val right = points[index + 1]
        val leftX = log10(left.first.toFloat())
        val rightX = log10(right.first.toFloat())
        if (x <= rightX) {
            if (rightX == leftX) return right.second
            val fraction = (x - leftX) / (rightX - leftX)
            return left.second + (right.second - left.second) * fraction
        }
    }
    return points.last().second
}

internal interface EqualizerEffect : AutoCloseable {
    val numberOfBands: Int
    val numberOfPresets: Int
    fun centerFrequencyHz(band: Int): Int
    fun presetName(index: Int): String?
    fun bandLevelRangeMb(): IntRange
    fun hasControl(): Boolean = true
    fun setEnabled(enabled: Boolean)
    fun setBandLevel(band: Int, levelMb: Int)
}

internal fun interface EqualizerEffectFactory {
    fun create(audioSessionId: Int): EqualizerEffect
}

internal object PlatformEqualizerEffectFactory : EqualizerEffectFactory {
    override fun create(audioSessionId: Int): EqualizerEffect = PlatformEqualizerEffect(audioSessionId)
}

private class PlatformEqualizerEffect(audioSessionId: Int) : EqualizerEffect {
    private val effect = Equalizer(0, audioSessionId)

    init {
        // Apply the stored curve before the effect can colour the first audible frame.
        runCatching { effect.setEnabled(false) }
    }

    override val numberOfBands: Int get() = effect.numberOfBands.toInt()
    override val numberOfPresets: Int get() = effect.numberOfPresets.toInt()

    // Equalizer reports this in milliHertz; the UI and persistence use whole Hertz.
    override fun centerFrequencyHz(band: Int): Int =
        (effect.getCenterFreq(band.toShort()) / 1_000).coerceAtLeast(1)

    override fun presetName(index: Int): String? = effect.getPresetName(index.toShort())

    override fun hasControl(): Boolean = effect.hasControl()

    override fun bandLevelRangeMb(): IntRange {
        val range = effect.bandLevelRange
        return range[0].toInt()..range[1].toInt()
    }

    override fun setEnabled(enabled: Boolean) {
        effect.setEnabled(enabled)
    }

    override fun setBandLevel(band: Int, levelMb: Int) {
        effect.setBandLevel(
            band.toShort(),
            levelMb.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        )
    }

    override fun close() {
        runCatching { effect.setEnabled(false) }
        runCatching { effect.release() }
    }
}

internal sealed interface EqualizerAttachResult {
    data class Attached(val capabilities: EqualizerCapabilities) : EqualizerAttachResult
    data object Unsupported : EqualizerAttachResult
    data object Failed : EqualizerAttachResult
}

/**
 * Owns one platform Equalizer for one audio session. Every failure is contained here: music keeps playing with the
 * platform's untouched sound when the effect is missing, busy or rejects a parameter.
 */
internal class EqualizerController(private val factory: EqualizerEffectFactory) {
    private var effect: EqualizerEffect? = null
    var capabilities: EqualizerCapabilities? = null
        private set

    fun attach(audioSessionId: Int): EqualizerAttachResult {
        release()
        if (audioSessionId <= 0) return EqualizerAttachResult.Unsupported
        val created = try {
            factory.create(audioSessionId)
        } catch (_: UnsupportedOperationException) {
            return EqualizerAttachResult.Unsupported
        } catch (_: RuntimeException) {
            return EqualizerAttachResult.Failed
        }
        return try {
            if (!created.hasControl()) {
                created.closeQuietly()
                return EqualizerAttachResult.Failed
            }
            val bandCount = created.numberOfBands
            if (bandCount <= 0) {
                created.closeQuietly()
                return EqualizerAttachResult.Unsupported
            }
            val frequencies = List(bandCount) { created.centerFrequencyHz(it) }
            val range = created.bandLevelRangeMb()
            if (range.last <= range.first) {
                created.closeQuietly()
                return EqualizerAttachResult.Failed
            }
            val presets = runCatching {
                List(created.numberOfPresets.coerceAtLeast(0)) { index -> created.presetName(index) }
                    .filterNotNull()
                    .filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
            val caps = EqualizerCapabilities(
                frequenciesHz = frequencies,
                hardwareMinLevelMb = range.first,
                hardwareMaxLevelMb = range.last,
                systemPresets = presets
            )
            effect = created
            capabilities = caps
            EqualizerAttachResult.Attached(caps)
        } catch (_: UnsupportedOperationException) {
            created.closeQuietly()
            EqualizerAttachResult.Unsupported
        } catch (_: RuntimeException) {
            created.closeQuietly()
            EqualizerAttachResult.Failed
        }
    }

    /** Applies levels and the enabled state. Returns false when the effect has gone away. */
    fun apply(settings: EqualizerSettings): Boolean {
        val current = effect ?: return false
        val caps = capabilities ?: return false
        return try {
            val levels = caps.levelsFor(settings)
            // A flat curve bypasses the effect completely. That is the only way to guarantee an unprocessed signal
            // on every vendor implementation, not merely a line that looks level.
            val flatRequested = settings.selectedPreset == EqualizerPreset.Flat
            val audible = settings.enabled && !flatRequested && levels.any { it != 0 }
            if (!audible) current.setEnabled(false)
            levels.forEachIndexed { band, levelMb -> current.setBandLevel(band, levelMb) }
            if (audible) current.setEnabled(true)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun release() {
        val current = effect
        effect = null
        capabilities = null
        current?.closeQuietly()
    }

    private fun EqualizerEffect.closeQuietly() {
        runCatching { close() }
    }
}
