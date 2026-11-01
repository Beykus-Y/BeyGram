package com.beykus.beygram.data.appearance

/**
 * Where the main bottom navigation of a phone sits. It is an independent axis: it never changes the design style,
 * the palette, the wallpaper, the messages or the motion, and the style keeps deciding how the bar itself looks.
 *
 * [Auto] is the compatibility choice: an installation that never touched this setting keeps exactly the placement its
 * style has always used, so an update never moves the bar by itself.
 */
enum class NavigationPlacement {
    /** Use the placement the selected design style asks for. */
    Auto,
    /** A full-width bar in the normal bottom slot, the way BeyGram has always drawn it. */
    Pinned,
    /** A capsule that floats above the content with side margins and safe bottom insets. */
    Floating;

    /** The placement the user can force, or null for [Auto], which is not a placement of its own. */
    val forced: NavigationPlacement? get() = takeUnless { it == Auto }
}

/** The stored placement, or [NavigationPlacement.Auto] for a missing, damaged or unknown value. */
fun navigationPlacementFromStored(raw: String?): NavigationPlacement =
    NavigationPlacement.entries.firstOrNull { it.name == raw } ?: NavigationPlacement.Auto
