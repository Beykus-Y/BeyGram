package com.beykus.beygram.ui.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

/**
 * Semantic motion tokens. Screens name what happened (a push, a tab change, a sheet opening) and never hard-code a
 * duration; the durations here are the prototype defaults for the Balanced preset, before speed is applied.
 */
enum class MotionToken(val category: MotionCategory, val baseDurationMillis: Int, val easing: Easing) {
    NavigationPush(MotionCategory.Navigation, 260, FastOutSlowInEasing),
    NavigationPop(MotionCategory.Navigation, 240, FastOutSlowInEasing),
    TabSelect(MotionCategory.Tabs, 200, FastOutSlowInEasing),
    SheetEnter(MotionCategory.Panels, 260, FastOutSlowInEasing),
    SheetExit(MotionCategory.Panels, 200, FastOutLinearInEasing),
    PickerSwitch(MotionCategory.Panels, 200, FastOutSlowInEasing),
    BannerChange(MotionCategory.Chats, 190, FastOutSlowInEasing),
    ListReorder(MotionCategory.Lists, 220, FastOutSlowInEasing),
    MediaExpand(MotionCategory.Media, 280, FastOutSlowInEasing),
    MicroFeedback(MotionCategory.Micro, 150, FastOutSlowInEasing)
}

/**
 * The resolved timing for one token. [intensity] is 0..1.5 and scales only amplitudes (offsets, scale). Opacity still
 * goes to fully visible at intensity 0, so content is never hidden by the setting.
 */
@Immutable
data class MotionSpec(
    val durationMillis: Int,
    val intensity: Float,
    val easing: Easing
) {
    /** True when the change must happen on the first frame: motion is off, reduced, or the category is disabled. */
    val isInstant: Boolean
        get() = durationMillis == 0

    fun <T> animationSpec(): FiniteAnimationSpec<T> = tween(durationMillis, easing = easing)
}

/**
 * The only place where a token becomes numbers. Precedence: a master switch off, the system reduce-motion signal, or a
 * disabled category gives an instant spec. Otherwise the base duration is divided by the speed multiplier.
 */
fun resolveMotion(token: MotionToken, settings: MotionSettings, reduced: Boolean): MotionSpec {
    val active = settings.enabled && !reduced && token.category !in settings.disabledCategories
    if (!active) return MotionSpec(durationMillis = 0, intensity = 0f, easing = token.easing)
    val normalized = settings.normalized()
    val duration = (token.baseDurationMillis / normalized.speed).roundToInt().coerceAtLeast(1)
    return MotionSpec(
        durationMillis = duration,
        intensity = normalized.intensityPercent / 100f,
        easing = token.easing
    )
}
