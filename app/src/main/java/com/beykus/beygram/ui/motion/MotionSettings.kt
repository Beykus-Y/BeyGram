package com.beykus.beygram.ui.motion

import androidx.compose.runtime.Immutable

const val MOTION_SPEED_MIN = 0.5f
const val MOTION_SPEED_MAX = 2f
const val MOTION_INTENSITY_MIN = 0
const val MOTION_INTENSITY_MAX = 150

/** Groups of decorative motion that can be switched off together without touching functional transitions. */
enum class MotionCategory {
    Navigation,
    Tabs,
    Panels,
    Lists,
    Media,
    Chats,
    Micro
}

/**
 * Named starting points. Choosing one fills [MotionSettings.speed] and [MotionSettings.intensityPercent]; [Custom]
 * keeps whatever the user set. [Off] is the master switch: it keeps the speed and intensity so they come back on enable.
 */
enum class MotionPreset(val speed: Float, val intensityPercent: Int) {
    Off(speed = 1f, intensityPercent = 0),
    Subtle(speed = 1.25f, intensityPercent = 50),
    Balanced(speed = 1f, intensityPercent = 100),
    Expressive(speed = 0.75f, intensityPercent = 150),
    Custom(speed = 1f, intensityPercent = 100)
}

@Immutable
data class MotionSettings(
    val preset: MotionPreset = MotionPreset.Balanced,
    val speed: Float = 1f,
    val intensityPercent: Int = 100,
    val disabledCategories: Set<MotionCategory> = emptySet()
) {
    val enabled: Boolean
        get() = preset != MotionPreset.Off

    /** Applies a preset. Off keeps the current values; the other fixed presets copy theirs; Custom changes nothing. */
    fun withPreset(next: MotionPreset): MotionSettings = when (next) {
        MotionPreset.Off, MotionPreset.Custom -> copy(preset = next)
        else -> copy(preset = next, speed = next.speed, intensityPercent = next.intensityPercent)
    }

    /** Any manual change moves the settings to [MotionPreset.Custom], so the preset label stays truthful. */
    fun withSpeed(value: Float): MotionSettings = copy(preset = MotionPreset.Custom, speed = value)

    fun withIntensity(percent: Int): MotionSettings = copy(preset = MotionPreset.Custom, intensityPercent = percent)

    fun withCategoryEnabled(category: MotionCategory, enabled: Boolean): MotionSettings {
        val next = if (enabled) disabledCategories - category else disabledCategories + category
        return copy(disabledCategories = next)
    }

    /** Clamps values read from storage or set by a slider to the supported ranges. */
    fun normalized(): MotionSettings = copy(
        speed = speed.coerceIn(MOTION_SPEED_MIN, MOTION_SPEED_MAX),
        intensityPercent = intensityPercent.coerceIn(MOTION_INTENSITY_MIN, MOTION_INTENSITY_MAX)
    )
}
