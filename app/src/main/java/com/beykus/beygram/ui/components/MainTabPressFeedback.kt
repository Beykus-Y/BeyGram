package com.beykus.beygram.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Press feedback of the bottom navigation: a short capsule in the theme accent instead of Material's
 * rectangular ripple. The capsule repeats the geometry of the selected pill, so a touch reads as part
 * of the tab rather than as a flash over the whole cell.
 */
internal const val TabPressFadeMillis = 180

/** How much of the theme accent shows through the capsule of an unselected tab. */
internal const val TabPressAlpha = 0.14f

/** The selected tab already has the accent pill; pressing only deepens it a little. */
internal const val TabPressSelectedAlpha = 0.08f

/** Shrink of the icon pill while pressed: visible, but the label never moves under the finger. */
internal const val TabPressedScale = 0.96f

/** Test tag of the transient capsule; only present while the press feedback is visible. */
internal const val MainTabPressCapsuleTag = "MainTabPressCapsule"

/**
 * Colour of the pressed capsule at [progress], where 0 is released and 1 is fully pressed.
 * The hue comes from the accent of the theme in effect, so light, dark and AMOLED keep their own tint.
 * The selected tab gets a softer overlay: its pill stays the selected indicator, not a second background.
 */
internal fun tabPressCapsuleColor(accent: Color, selected: Boolean, progress: Float): Color =
    accent.copy(alpha = (if (selected) TabPressSelectedAlpha else TabPressAlpha) * progress.coerceIn(0f, 1f))

/** Scale of the icon pill at [progress]; drawn in a graphics layer, so the bar layout never changes. */
internal fun tabPressScale(progress: Float): Float =
    1f - (1f - TabPressedScale) * progress.coerceIn(0f, 1f)
