package com.beykus.beygram.data.appearance

/**
 * Where the floating panels of a phone sit: the main bottom dock and the chat's pinned bar follow it together. It is
 * an independent axis: it never changes the design style, the palette, the wallpaper, the messages or the motion, and
 * the style keeps deciding how the panels themselves look.
 *
 * [Auto] is the compatibility choice: an installation that never touched this setting keeps exactly the placement its
 * style has always used, so an update never moves a panel by itself.
 */
enum class NavigationPlacement {
    /** Use the placement the selected design style asks for: floating for Liquid Glass, attached for the rest. */
    Auto,
    /** Full-width panels in their natural slots: the dock on the bottom edge, the pinned bar one with the header. */
    Pinned,
    /** Panels that float above the content with side margins, safe insets and the style's rounded corners. */
    Floating;

    /** The placement the user can force, or null for [Auto], which is not a placement of its own. */
    val forced: NavigationPlacement? get() = takeUnless { it == Auto }
}

/** The stored placement, or [NavigationPlacement.Auto] for a missing, damaged or unknown value. */
fun navigationPlacementFromStored(raw: String?): NavigationPlacement =
    NavigationPlacement.entries.firstOrNull { it.name == raw } ?: NavigationPlacement.Auto
