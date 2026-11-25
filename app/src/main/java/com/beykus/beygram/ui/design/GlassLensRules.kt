package com.beykus.beygram.ui.design

/**
 * Pure decisions behind the Liquid Glass V2 lens (Issue #1173). No Android types here, so the rules are unit-tested on
 * the JVM. The shader itself is not run by these tests; it is checked on a device.
 */

/**
 * Whether a panel draws the refracting lens. The lens needs the style to ask for it and a panel that really samples the
 * backdrop. A floating pane ([GlassEdge.Rim]) always qualifies. Full-width chrome qualifies only when the caller opts in
 * with [lensOnFreeEdge]: its lens then bends only along the side that meets the content, see [freeEdgeSides].
 */
internal fun glassLensWanted(
    lensRefraction: Boolean,
    edge: GlassEdge,
    samplesBackdrop: Boolean,
    lensOnFreeEdge: Boolean
): Boolean {
    if (!lensRefraction || !samplesBackdrop) return false
    return edge == GlassEdge.Rim || (lensOnFreeEdge && edge == GlassEdge.BottomHairline)
}

/**
 * Which sides of a panel are open (`true`), in the order left, top, right, bottom. An open side lies on the screen edge,
 * so the lens must not bevel it: that bevel would draw a frame around the full-width chrome. A closed side is the one
 * the lens bends along.
 *
 * - [GlassEdge.Rim]: nothing is open; the whole outline is bent, as before.
 * - [GlassEdge.BottomHairline] (chrome at the top of a screen): the top, left and right are the window edges; the
 *   bottom meets the content.
 * - [GlassEdge.TopHairline] (chrome at the bottom of a screen): the bottom, left and right are open.
 */
internal fun freeEdgeSides(edge: GlassEdge): BooleanArray = when (edge) {
    GlassEdge.Rim -> booleanArrayOf(false, false, false, false)
    GlassEdge.BottomHairline -> booleanArrayOf(true, true, true, false)
    GlassEdge.TopHairline -> booleanArrayOf(true, false, true, true)
}
