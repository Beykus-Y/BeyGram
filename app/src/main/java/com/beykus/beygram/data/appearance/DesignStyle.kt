package com.beykus.beygram.data.appearance

/**
 * The visual language of the whole interface. It is a separate axis from the colour preset and brightness:
 * any style can be combined with any palette and with light, dark or AMOLED, and it never changes what is stored
 * for colours, wallpapers, message geometry or motion.
 *
 * The order is the order of the selector in Appearance.
 */
enum class DesignStyle {
    /** The interface BeyGram has always had. It stays the default so an update never changes a look by itself. */
    Default,
    /** Classic Material Design: strict shapes, flat app bars, visible elevation and the 2014 type scale. */
    Material2,
    /** Material 3 / Material You: tonal surfaces, the current type and shape scales, dynamic colour where available. */
    Material3,
    /** Android rendition of translucent floating glass: sampled backdrop blur, light rims and depth. */
    LiquidGlass,
    /** BeyGram's own visual language: chat-first hierarchy, soft depth and its own navigation and composer. */
    Signature
}

/** The stored style, or [DesignStyle.Default] for a missing, damaged or unknown value. */
fun designStyleFromStored(raw: String?): DesignStyle =
    DesignStyle.entries.firstOrNull { it.name == raw } ?: DesignStyle.Default
