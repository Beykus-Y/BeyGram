package com.beykus.beygram.data.appearance

/**
 * How much of the glass rendering the interface may use. [Auto] picks a level from the device capabilities and the
 * system accessibility settings; the other values are an explicit user override for weaker hardware or for contrast.
 */
enum class GlassQuality {
    /** Chosen at runtime from the Android version, window blur availability and reduced-transparency settings. */
    Auto,
    /** Full effect: sampled backdrop, real blur, rims and specular highlights. */
    High,
    /** Simplified: sampled tint and rims without the costly blur pass. */
    Balanced,
    /** Matte: an opaque, high-contrast surface that keeps the layout but drops translucency. */
    Lite
}

/** The stored glass quality, or [GlassQuality.Auto] for a missing, damaged or unknown value. */
fun glassQualityFromStored(raw: String?): GlassQuality =
    GlassQuality.entries.firstOrNull { it.name == raw } ?: GlassQuality.Auto
