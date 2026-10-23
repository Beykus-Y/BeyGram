package com.beykus.beygram.ui.screens.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.data.appearance.MessageAnimationEasing
import com.beykus.beygram.data.appearance.MessageAnimationMotionDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.MessageLineSpacing
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.ThemePreset
import com.beykus.beygram.data.appearance.WallpaperGradientChoices
import com.beykus.beygram.data.appearance.WallpaperPresets
import com.beykus.beygram.data.appearance.WallpaperSolidChoices

@StringRes
internal fun ThemePreset.labelRes(): Int = when (this) {
    ThemePreset.Warm -> R.string.theme_palette_warm
    ThemePreset.Graphite -> R.string.theme_palette_graphite
    ThemePreset.MaterialYou -> R.string.theme_palette_system
}

@StringRes
internal fun ThemePreset.descriptionRes(): Int = when (this) {
    ThemePreset.Warm -> R.string.appearance_palette_warm_detail
    ThemePreset.Graphite -> R.string.appearance_palette_graphite_detail
    ThemePreset.MaterialYou -> R.string.appearance_palette_system_detail
}

@StringRes
internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.FollowSystem -> R.string.theme_brightness_system
    ThemeMode.Light -> R.string.theme_brightness_light
    ThemeMode.Dark -> R.string.theme_brightness_dark
    ThemeMode.Amoled -> R.string.theme_brightness_amoled_short
}

@StringRes
internal fun ThemeMode.detailRes(): Int? = when (this) {
    ThemeMode.FollowSystem -> R.string.appearance_mode_system_detail
    ThemeMode.Light -> R.string.appearance_mode_light_detail
    ThemeMode.Dark -> R.string.appearance_mode_dark_detail
    ThemeMode.Amoled -> R.string.theme_brightness_amoled_detail
}

@StringRes
internal fun BubbleStyle.labelRes(): Int = when (this) {
    BubbleStyle.Rounded -> R.string.theme_editor_shape_rounded
    BubbleStyle.Soft -> R.string.theme_editor_shape_soft
    BubbleStyle.Square -> R.string.theme_editor_shape_square
}

@StringRes
internal fun ListDensity.labelRes(): Int = when (this) {
    ListDensity.Compact -> R.string.theme_editor_density_compact
    ListDensity.Comfortable -> R.string.theme_editor_density_comfortable
    ListDensity.Spacious -> R.string.theme_editor_density_spacious
}

@StringRes
internal fun MessageLineSpacing.labelRes(): Int = when (this) {
    MessageLineSpacing.Compact -> R.string.theme_editor_line_spacing_compact
    MessageLineSpacing.Normal -> R.string.theme_editor_line_spacing_normal
    MessageLineSpacing.Relaxed -> R.string.theme_editor_line_spacing_relaxed
}

@StringRes
internal fun MessageAnimationPreset.labelRes(): Int = when (this) {
    MessageAnimationPreset.None -> R.string.theme_animations_preset_none
    MessageAnimationPreset.Fade -> R.string.theme_animations_preset_fade
    MessageAnimationPreset.Slide -> R.string.theme_animations_preset_slide
    MessageAnimationPreset.Spring -> R.string.theme_animations_preset_spring
    MessageAnimationPreset.Pop -> R.string.theme_animations_preset_pop
    MessageAnimationPreset.Flow -> R.string.theme_animations_preset_flow
    MessageAnimationPreset.Drift -> R.string.theme_animations_preset_drift
    MessageAnimationPreset.Quick -> R.string.theme_animations_preset_quick
    MessageAnimationPreset.Tilt -> R.string.theme_animations_preset_tilt
    MessageAnimationPreset.Sway -> R.string.theme_animations_preset_sway
    MessageAnimationPreset.ComposerLaunch -> R.string.theme_animations_preset_composer
    MessageAnimationPreset.Custom -> R.string.theme_animations_preset_custom
}

@StringRes
internal fun MessageAnimationPreset.descriptionRes(): Int = when (this) {
    MessageAnimationPreset.None -> R.string.theme_animations_desc_none
    MessageAnimationPreset.Fade -> R.string.theme_animations_desc_fade
    MessageAnimationPreset.Slide -> R.string.theme_animations_desc_slide
    MessageAnimationPreset.Spring -> R.string.theme_animations_desc_spring
    MessageAnimationPreset.Pop -> R.string.theme_animations_desc_pop
    MessageAnimationPreset.Flow -> R.string.theme_animations_desc_flow
    MessageAnimationPreset.Drift -> R.string.theme_animations_desc_drift
    MessageAnimationPreset.Quick -> R.string.theme_animations_desc_quick
    MessageAnimationPreset.Tilt -> R.string.theme_animations_desc_tilt
    MessageAnimationPreset.Sway -> R.string.theme_animations_desc_sway
    MessageAnimationPreset.ComposerLaunch -> R.string.theme_animations_desc_composer
    MessageAnimationPreset.Custom -> R.string.theme_animations_desc_custom
}

@StringRes
internal fun MessageAnimationMotionDirection.labelRes(): Int = when (this) {
    MessageAnimationMotionDirection.Auto -> R.string.theme_animations_direction_auto
    MessageAnimationMotionDirection.FromStart -> R.string.theme_animations_direction_start
    MessageAnimationMotionDirection.FromEnd -> R.string.theme_animations_direction_end
    MessageAnimationMotionDirection.FromTop -> R.string.theme_animations_direction_top
    MessageAnimationMotionDirection.FromBottom -> R.string.theme_animations_direction_bottom
}

@StringRes
internal fun MessageAnimationEasing.labelRes(): Int = when (this) {
    MessageAnimationEasing.Smooth -> R.string.theme_animations_easing_smooth
    MessageAnimationEasing.Linear -> R.string.theme_animations_easing_linear
    MessageAnimationEasing.EaseIn -> R.string.theme_animations_easing_ease_in
    MessageAnimationEasing.EaseOut -> R.string.theme_animations_easing_ease_out
    MessageAnimationEasing.Expressive -> R.string.theme_animations_easing_expressive
}

@StringRes
internal fun wallpaperPresetNameRes(id: String): Int = when (id) {
    "lavender-flow" -> R.string.wallpaper_preset_lavender_flow
    "arctic-blue" -> R.string.wallpaper_preset_arctic_blue
    "mint-air" -> R.string.wallpaper_preset_mint_air
    "sunset-peach" -> R.string.wallpaper_preset_sunset_peach
    "soft-rose" -> R.string.wallpaper_preset_soft_rose
    "blue-notes" -> R.string.wallpaper_preset_blue_notes
    "graphite-wave" -> R.string.wallpaper_preset_graphite_wave
    "forest-whisper" -> R.string.wallpaper_preset_forest_whisper
    "deep-night" -> R.string.wallpaper_preset_deep_night
    "violet-mesh" -> R.string.wallpaper_preset_violet_mesh
    "ocean-current" -> R.string.wallpaper_preset_ocean_current
    "amber-glow" -> R.string.wallpaper_preset_amber_glow
    "sky-paper" -> R.string.wallpaper_preset_sky_paper
    "terracotta-dust" -> R.string.wallpaper_preset_terracotta_dust
    "silver-mist" -> R.string.wallpaper_preset_silver_mist
    "emerald-tide" -> R.string.wallpaper_preset_emerald_tide
    else -> R.string.wallpaper_option_none
}

/** A readable name for the selected background, used by the overview and the wallpaper page. */
@Composable
internal fun wallpaperLabel(wallpaper: ChatWallpaper): String = when (wallpaper) {
    ChatWallpaper.None -> stringResource(R.string.wallpaper_option_none)
    is ChatWallpaper.Solid -> {
        val index = WallpaperSolidChoices.indexOf(wallpaper.color)
        if (index >= 0) stringResource(R.string.wallpaper_option_solid, index + 1)
        else stringResource(R.string.wallpaper_option_solid_custom)
    }
    is ChatWallpaper.Gradient -> {
        val index = WallpaperGradientChoices.indexOfFirst { it.first == wallpaper.top && it.second == wallpaper.bottom }
        if (index >= 0) stringResource(R.string.wallpaper_option_gradient, index + 1)
        else stringResource(R.string.wallpaper_option_gradient_custom)
    }
    is ChatWallpaper.Preset -> {
        // A preset saved by a newer build may be unknown here; never show a raw storage id.
        val known = WallpaperPresets.any { it.id == wallpaper.id }
        if (known) stringResource(wallpaperPresetNameRes(wallpaper.id))
        else stringResource(R.string.wallpaper_option_none)
    }
    is ChatWallpaper.Photo -> stringResource(R.string.wallpaper_option_photo)
}
