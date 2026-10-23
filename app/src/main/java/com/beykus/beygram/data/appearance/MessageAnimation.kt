package com.beykus.beygram.data.appearance

import java.util.Locale

/**
 * How a new message enters the list. [None] shows it immediately, the named presets are tuned BeyGram effects, and
 * [Custom] combines the contextual controls. [ComposerLaunch] is an outgoing-only spatial transition from the composer;
 * incoming messages fall back to [Flow] when it is selected in the linked mode.
 */
enum class MessageAnimationPreset {
    None, Fade, Slide, Spring, Pop, Flow, Drift, Quick, Tilt, Sway, ComposerLaunch, Custom
}

/** Easing used by tween-based entrances. Spring presets expose [MessageAnimationTuning.springiness] instead. */
enum class MessageAnimationEasing { Smooth, Linear, EaseIn, EaseOut, Expressive }

/** An optional override of the mirrored sent/received direction. */
enum class MessageAnimationMotionDirection { Auto, FromStart, FromEnd, FromTop, FromBottom }

/** User-adjustable values shared by every preset. They are clamped before they reach Compose. */
data class MessageAnimationTuning(
    /** 0.5x to 2x. Higher values make the entrance shorter. */
    val speed: Float = 1f,
    /** 0 to 1. Controls how far the preset travels from its starting alpha, scale and offset. */
    val intensity: Float = 0.55f,
    /** 0 to 32 dp. Used by presets that move the bubble. */
    val offsetDp: Int = 12,
    /** 0.80 to 1. The scale an entrance starts from, before intensity is applied. */
    val scale: Float = 0.94f,
    val easing: MessageAnimationEasing = MessageAnimationEasing.Smooth,
    /** 0 to 1. Higher values make spring presets bouncier. */
    val springiness: Float = 0.4f,
    val motionDirection: MessageAnimationMotionDirection = MessageAnimationMotionDirection.Auto
)

/** One direction's preset and tuning. */
data class MessageAnimationDirection(
    val preset: MessageAnimationPreset = MessageAnimationPreset.Fade,
    val tuning: MessageAnimationTuning = MessageAnimationTuning()
)

/**
 * Message entrance animation settings saved with the rest of the appearance choice.
 *
 * [shared] is used while [linkedDirections] is true. [sent] and [received] remain intact behind it, so turning the link
 * off restores the last independent choices without copying values on every edit.
 */
data class MessageAnimationConfig(
    val enabled: Boolean = true,
    val linkedDirections: Boolean = true,
    val shared: MessageAnimationDirection = MessageAnimationDirection(),
    val sent: MessageAnimationDirection = MessageAnimationDirection(),
    val received: MessageAnimationDirection = MessageAnimationDirection()
) {
    /** The effective direction, including the incoming fallback for an outgoing-only preset. */
    fun direction(outgoing: Boolean): MessageAnimationDirection {
        val selected = if (linkedDirections) shared else if (outgoing) sent else received
        return if (!outgoing && selected.preset == MessageAnimationPreset.ComposerLaunch) {
            // Composer Launch has no Direction control; its incoming Flow fallback must not inherit a hidden override.
            selected.copy(
                preset = MessageAnimationPreset.Flow,
                tuning = selected.tuning.copy(motionDirection = MessageAnimationMotionDirection.Auto)
            )
        } else selected
    }

    /** Selects a direction without touching the hidden individual settings. */
    fun withDirection(outgoing: Boolean, direction: MessageAnimationDirection): MessageAnimationConfig =
        if (linkedDirections) copy(shared = direction.validated(outgoing = true))
        else if (outgoing) copy(sent = direction.validated(outgoing = true))
        else copy(received = direction.validated(outgoing = false))

    /** Turning the link on seeds the shared choice from the current outgoing one; turning it off keeps both sides. */
    fun withLinkedDirections(linked: Boolean): MessageAnimationConfig =
        if (linked) copy(linkedDirections = true, shared = sent.validated(outgoing = true))
        else copy(linkedDirections = false)
}

const val MIN_MESSAGE_ANIMATION_SPEED = 0.5f
const val MAX_MESSAGE_ANIMATION_SPEED = 2f
const val MIN_MESSAGE_ANIMATION_INTENSITY = 0f
const val MAX_MESSAGE_ANIMATION_INTENSITY = 1f
const val MIN_MESSAGE_ANIMATION_OFFSET_DP = 0
const val MAX_MESSAGE_ANIMATION_OFFSET_DP = 32
const val MIN_MESSAGE_ANIMATION_SCALE = 0.80f
const val MAX_MESSAGE_ANIMATION_SCALE = 1f
const val MIN_MESSAGE_ANIMATION_SPRINGINESS = 0f
const val MAX_MESSAGE_ANIMATION_SPRINGINESS = 1f

fun validMessageAnimationSpeed(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_MESSAGE_ANIMATION_SPEED, MAX_MESSAGE_ANIMATION_SPEED) else 1f

fun validMessageAnimationIntensity(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_MESSAGE_ANIMATION_INTENSITY, MAX_MESSAGE_ANIMATION_INTENSITY)
    else MessageAnimationTuning().intensity

fun validMessageAnimationOffsetDp(value: Int): Int =
    value.coerceIn(MIN_MESSAGE_ANIMATION_OFFSET_DP, MAX_MESSAGE_ANIMATION_OFFSET_DP)

fun validMessageAnimationScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_MESSAGE_ANIMATION_SCALE, MAX_MESSAGE_ANIMATION_SCALE)
    else MessageAnimationTuning().scale

fun validMessageAnimationSpringiness(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_MESSAGE_ANIMATION_SPRINGINESS, MAX_MESSAGE_ANIMATION_SPRINGINESS)
    else MessageAnimationTuning().springiness

fun messageAnimationPresetFromStored(value: String?): MessageAnimationPreset =
    MessageAnimationPreset.entries.firstOrNull { it.name == value } ?: MessageAnimationPreset.Fade

fun messageAnimationEasingFromStored(value: String?): MessageAnimationEasing =
    MessageAnimationEasing.entries.firstOrNull { it.name == value } ?: MessageAnimationEasing.Smooth

fun messageAnimationMotionDirectionFromStored(value: String?): MessageAnimationMotionDirection =
    MessageAnimationMotionDirection.entries.firstOrNull { it.name == value } ?: MessageAnimationMotionDirection.Auto

/** Controls that actually change the selected preset. The settings screen uses this to avoid irrelevant sliders. */
enum class MessageAnimationControl { Speed, Intensity, Scale, Offset, Direction, Easing, Springiness }

fun MessageAnimationPreset.controls(): Set<MessageAnimationControl> = when (this) {
    MessageAnimationPreset.None -> emptySet()
    MessageAnimationPreset.Fade -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity,
        MessageAnimationControl.Scale, MessageAnimationControl.Easing
    )
    MessageAnimationPreset.Slide -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity,
        MessageAnimationControl.Offset, MessageAnimationControl.Easing, MessageAnimationControl.Direction
    )
    MessageAnimationPreset.Spring -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity,
        MessageAnimationControl.Scale, MessageAnimationControl.Springiness
    )
    MessageAnimationPreset.Pop -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity,
        MessageAnimationControl.Scale, MessageAnimationControl.Springiness
    )
    MessageAnimationPreset.Flow -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity, MessageAnimationControl.Scale,
        MessageAnimationControl.Offset, MessageAnimationControl.Easing, MessageAnimationControl.Direction
    )
    MessageAnimationPreset.Drift -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity, MessageAnimationControl.Scale,
        MessageAnimationControl.Offset, MessageAnimationControl.Direction
    )
    MessageAnimationPreset.Quick -> setOf(MessageAnimationControl.Speed, MessageAnimationControl.Intensity)
    MessageAnimationPreset.Tilt -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity,
        MessageAnimationControl.Scale, MessageAnimationControl.Easing
    )
    MessageAnimationPreset.Sway -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity,
        MessageAnimationControl.Scale, MessageAnimationControl.Easing
    )
    MessageAnimationPreset.ComposerLaunch -> setOf(MessageAnimationControl.Speed)
    MessageAnimationPreset.Custom -> setOf(
        MessageAnimationControl.Speed, MessageAnimationControl.Intensity, MessageAnimationControl.Scale,
        MessageAnimationControl.Offset, MessageAnimationControl.Easing, MessageAnimationControl.Direction
    )
}

fun MessageAnimationPreset.supportsIncoming(): Boolean = this != MessageAnimationPreset.ComposerLaunch

fun MessageAnimationTuning.validated(): MessageAnimationTuning = copy(
    speed = validMessageAnimationSpeed(speed),
    intensity = validMessageAnimationIntensity(intensity),
    offsetDp = validMessageAnimationOffsetDp(offsetDp),
    scale = validMessageAnimationScale(scale),
    springiness = validMessageAnimationSpringiness(springiness)
)

/** Incoming messages cannot keep an outgoing-only preset; such a choice falls back to the BeyGram Flow entrance. */
fun MessageAnimationDirection.validated(outgoing: Boolean): MessageAnimationDirection {
    val safePreset = if (!outgoing && preset == MessageAnimationPreset.ComposerLaunch) MessageAnimationPreset.Flow else preset
    val safeTuning = if (safePreset != preset) tuning.copy(motionDirection = MessageAnimationMotionDirection.Auto) else tuning
    return copy(preset = safePreset, tuning = safeTuning.validated())
}

fun MessageAnimationConfig.validated(): MessageAnimationConfig = copy(
    shared = shared.validated(outgoing = true),
    sent = sent.validated(outgoing = true),
    received = received.validated(outgoing = false)
)

/**
 * A versioned DataStore value. Version 2 stores the linked flag and all three direction records; version 1
 * (`enabled | sent | received`, 11 parts) is still accepted so existing choices survive the update.
 */
fun MessageAnimationConfig.encode(): String {
    val value = validated()
    fun direction(direction: MessageAnimationDirection) = listOf(
        direction.preset.name,
        formatAnimationNumber(direction.tuning.speed),
        formatAnimationNumber(direction.tuning.intensity),
        direction.tuning.offsetDp.toString(),
        formatAnimationNumber(direction.tuning.scale),
        direction.tuning.easing.name,
        formatAnimationNumber(direction.tuning.springiness),
        direction.tuning.motionDirection.name
    )
    return (listOf(STORAGE_VERSION.toString(), if (value.enabled) "1" else "0", if (value.linkedDirections) "1" else "0") +
        direction(value.shared) + direction(value.sent) + direction(value.received)).joinToString("|")
}

/** Missing or damaged data reads as the default choice; every parsed value is bounded again. */
fun messageAnimationConfigFromStored(value: String?): MessageAnimationConfig {
    if (value.isNullOrBlank()) return MessageAnimationConfig()
    val parts = value.split('|')
    if (parts.size == LEGACY_PART_COUNT) return legacyConfig(parts)
    if (parts.size != STORAGE_PART_COUNT || parts[0] != STORAGE_VERSION.toString()) return MessageAnimationConfig()
    fun direction(offset: Int): MessageAnimationDirection {
        val tuning = MessageAnimationTuning(
            speed = parts[offset + 1].toFloatOrNull() ?: 1f,
            intensity = parts[offset + 2].toFloatOrNull() ?: 0.55f,
            offsetDp = parts[offset + 3].toIntOrNull() ?: 12,
            scale = parts[offset + 4].toFloatOrNull() ?: 0.94f,
            easing = messageAnimationEasingFromStored(parts[offset + 5]),
            springiness = parts[offset + 6].toFloatOrNull() ?: 0.4f,
            motionDirection = messageAnimationMotionDirectionFromStored(parts[offset + 7])
        ).validated()
        return MessageAnimationDirection(messageAnimationPresetFromStored(parts[offset]), tuning)
    }
    return MessageAnimationConfig(
        enabled = parts[1] == "1",
        linkedDirections = parts[2] == "1",
        shared = direction(3),
        sent = direction(11),
        received = direction(19).validated(outgoing = false)
    ).validated()
}

private fun legacyConfig(parts: List<String>): MessageAnimationConfig {
    fun direction(offset: Int): MessageAnimationDirection {
        val tuning = MessageAnimationTuning(
            speed = parts[offset + 1].toFloatOrNull() ?: 1f,
            intensity = parts[offset + 2].toFloatOrNull() ?: 0.55f,
            offsetDp = parts[offset + 3].toIntOrNull() ?: 12,
            scale = parts[offset + 4].toFloatOrNull() ?: 0.94f
        ).validated()
        return MessageAnimationDirection(messageAnimationPresetFromStored(parts[offset]), tuning)
    }
    val sent = direction(1)
    val received = direction(6).validated(outgoing = false)
    return MessageAnimationConfig(
        enabled = parts[0] == "1",
        linkedDirections = sent == received,
        shared = sent,
        sent = sent,
        received = received
    ).validated()
}

private const val STORAGE_VERSION = 2
private const val LEGACY_PART_COUNT = 11
private const val STORAGE_PART_COUNT = 27

private fun formatAnimationNumber(value: Float): String = String.format(Locale.ROOT, "%.3f", value)
