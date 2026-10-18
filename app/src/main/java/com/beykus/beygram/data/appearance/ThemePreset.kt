package com.beykus.beygram.data.appearance

/** The colour family of the theme. Brightness is chosen separately, with [ThemeMode]. [MaterialYou] takes its colours from the system wallpaper (Android 12 and newer). */
enum class ThemePreset { Warm, Graphite, MaterialYou }

/** Reads a stored [ThemePreset] name. Missing or unknown values fall back to [ThemePreset.Warm], the look BeyGram has always had. */
internal fun themePresetFromStored(stored: String?): ThemePreset =
    ThemePreset.entries.firstOrNull { it.name == stored } ?: ThemePreset.Warm
