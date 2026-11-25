package com.beykus.beygram.data.appearance

/**
 * The Liquid Glass V2 lens variant the user picked. Stored by name; an unknown or missing name reads as [Clear].
 * The numbers behind each variant live in `LensTuningPresets` (ui/design), not here, so the data layer stays free of
 * rendering values.
 */
enum class LiquidGlassLensPreset { Standard, Clear, Bold }

fun liquidGlassLensPresetFromStored(raw: String?): LiquidGlassLensPreset =
    LiquidGlassLensPreset.entries.firstOrNull { it.name == raw } ?: LiquidGlassLensPreset.Clear
