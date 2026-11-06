package com.beykus.beygram.ui.design

import com.beykus.beygram.data.appearance.GlassQuality

/**
 * What the device and the system settings allow the glass material to do. Kept separate from Android APIs so the
 * choice is a pure function that can be tested.
 */
data class GlassCapabilities(
    /** An Android version and GPU that can blur a sampled backdrop in a render pass (Android 12 / API 31 and up). */
    val supportsBackdropBlur: Boolean,
    /** The system asks for reduced transparency or the animations are switched off entirely. */
    val reducedTransparency: Boolean,
    /** Battery saver, thermal throttling or a low-RAM device: the cheap path is used instead of the full one. */
    val constrainedPerformance: Boolean
)

/**
 * The level of glass used when the user leaves the choice on Auto. Anything that makes the full effect unsafe - an
 * old Android version, reduced transparency, power saving or weak hardware - steps down, and a device that cannot
 * blur at all still gets the glass geometry with a tint instead of an unreadable transparent panel.
 */
fun autoGlassQuality(capabilities: GlassCapabilities): GlassQuality = when {
    !capabilities.supportsBackdropBlur -> GlassQuality.Balanced
    capabilities.reducedTransparency -> GlassQuality.Lite
    capabilities.constrainedPerformance -> GlassQuality.Balanced
    else -> GlassQuality.High
}

/**
 * The glass level the interface really draws for the user's [requested] choice. An explicit [GlassQuality.High] on a
 * device that cannot blur a backdrop is shown as [GlassQuality.Balanced]: the material never claims a blur the device
 * does not render, and the saved choice is kept, so the same setting turns into the real blur on a capable device.
 */
fun effectiveGlassQuality(requested: GlassQuality, capabilities: GlassCapabilities): GlassQuality {
    val resolved = resolveGlassQuality(requested, autoGlassQuality(capabilities))
    return if (resolved == GlassQuality.High && !capabilities.supportsBackdropBlur) GlassQuality.Balanced else resolved
}
