package com.beykus.beygram.data.appearance

import java.util.Locale

/**
 * How a new message enters the list. [None] shows it immediately; [Fade], [Slide] and [Spring] are tuned presets;
 * [Custom] combines the controls below. The sent and received directions are stored independently.
 */
enum class MessageAnimationPreset { None, Fade, Slide, Spring, Custom }

/** User-adjustable values shared by every preset. They are clamped before they reach Compose. */
data class MessageAnimationTuning(
    /** 0.5x to 2x. Higher values make the entrance shorter. */
    val speed: Float = 1f,
    /** 0 to 1. Controls how far the preset travels from its starting alpha, scale and offset. */
    val intensity: Float = 0.55f,
    /** 0 to 24 dp. Used by slide and custom entrances. */
    val offsetDp: Int = 12,
    /** 0.80 to 1. The scale an entrance starts from, before intensity is applied. */
    val scale: Float = 0.94f
)

/** One direction's preset and tuning. */
data class MessageAnimationDirection(
    val preset: MessageAnimationPreset = MessageAnimationPreset.Fade,
    val tuning: MessageAnimationTuning = MessageAnimationTuning()
)

/** Message entrance animation settings saved with the rest of the appearance choice. */
data class MessageAnimationConfig(
    val enabled: Boolean = true,
    val sent: MessageAnimationDirection = MessageAnimationDirection(),
    val received: MessageAnimationDirection = MessageAnimationDirection()
) {
    fun direction(outgoing: Boolean): MessageAnimationDirection = if (outgoing) sent else received
}

const val MIN_MESSAGE_ANIMATION_SPEED = 0.5f
const val MAX_MESSAGE_ANIMATION_SPEED = 2f
const val MIN_MESSAGE_ANIMATION_INTENSITY = 0f
const val MAX_MESSAGE_ANIMATION_INTENSITY = 1f
const val MIN_MESSAGE_ANIMATION_OFFSET_DP = 0
const val MAX_MESSAGE_ANIMATION_OFFSET_DP = 24
const val MIN_MESSAGE_ANIMATION_SCALE = 0.80f
const val MAX_MESSAGE_ANIMATION_SCALE = 1f

fun validMessageAnimationSpeed(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_MESSAGE_ANIMATION_SPEED, MAX_MESSAGE_ANIMATION_SPEED) else 1f

fun validMessageAnimationIntensity(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_MESSAGE_ANIMATION_INTENSITY, MAX_MESSAGE_ANIMATION_INTENSITY) else MessageAnimationTuning().intensity

fun validMessageAnimationOffsetDp(value: Int): Int =
    value.coerceIn(MIN_MESSAGE_ANIMATION_OFFSET_DP, MAX_MESSAGE_ANIMATION_OFFSET_DP)

fun validMessageAnimationScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_MESSAGE_ANIMATION_SCALE, MAX_MESSAGE_ANIMATION_SCALE) else MessageAnimationTuning().scale

fun messageAnimationPresetFromStored(value: String?): MessageAnimationPreset =
    MessageAnimationPreset.entries.firstOrNull { it.name == value } ?: MessageAnimationPreset.Fade

fun MessageAnimationTuning.validated(): MessageAnimationTuning = copy(
    speed = validMessageAnimationSpeed(speed),
    intensity = validMessageAnimationIntensity(intensity),
    offsetDp = validMessageAnimationOffsetDp(offsetDp),
    scale = validMessageAnimationScale(scale)
)

fun MessageAnimationDirection.validated(): MessageAnimationDirection = copy(tuning = tuning.validated())

fun MessageAnimationConfig.validated(): MessageAnimationConfig = copy(sent = sent.validated(), received = received.validated())

/**
 * One compact DataStore value. `|` separates the enabled flag, the sent direction and the received direction;
 * each direction is preset, speed, intensity, offset and scale in that order.
 */
fun MessageAnimationConfig.encode(): String {
    val value = validated()
    fun direction(direction: MessageAnimationDirection) = listOf(
        direction.preset.name,
        formatAnimationNumber(direction.tuning.speed),
        formatAnimationNumber(direction.tuning.intensity),
        direction.tuning.offsetDp.toString(),
        formatAnimationNumber(direction.tuning.scale)
    )
    return (listOf(if (value.enabled) "1" else "0") + direction(value.sent) + direction(value.received)).joinToString("|")
}

/** Missing or damaged data reads as the default choice; every parsed value is bounded again. */
fun messageAnimationConfigFromStored(value: String?): MessageAnimationConfig {
    if (value.isNullOrBlank()) return MessageAnimationConfig()
    val parts = value.split('|')
    if (parts.size != 11) return MessageAnimationConfig()
    fun direction(offset: Int): MessageAnimationDirection {
        val preset = messageAnimationPresetFromStored(parts[offset])
        val tuning = MessageAnimationTuning(
            speed = parts[offset + 1].toFloatOrNull() ?: 1f,
            intensity = parts[offset + 2].toFloatOrNull() ?: 0.55f,
            offsetDp = parts[offset + 3].toIntOrNull() ?: 12,
            scale = parts[offset + 4].toFloatOrNull() ?: 0.94f
        ).validated()
        return MessageAnimationDirection(preset, tuning)
    }
    return MessageAnimationConfig(
        enabled = parts[0] == "1",
        sent = direction(1),
        received = direction(6)
    ).validated()
}

private fun formatAnimationNumber(value: Float): String = String.format(Locale.ROOT, "%.3f", value)
