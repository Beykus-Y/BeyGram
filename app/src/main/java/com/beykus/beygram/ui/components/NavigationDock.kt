package com.beykus.beygram.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The space the main navigation actually takes on this screen, measured rather than assumed. The main screen and the
 * mini-player read it, so a large font scale, a three-button navigation bar or a different style cannot push the create
 * button on top of the dock or hide the last conversation under it.
 *
 * [height] is the measured panel, without the margins around it. Everything below the content is expressed relative to
 * it, which keeps one source of truth for the dock geometry.
 */
@Immutable
data class NavigationDockGeometry(
    val floating: Boolean,
    val height: Dp = 0.dp,
    val horizontalMargin: Dp = 0.dp,
    val bottomMargin: Dp = 0.dp,
    val contentGap: Dp = DefaultNavigationDockGap,
    /**
     * How much of the bottom of the content the floating chrome covers: the mini-player, the capsule, its margin, the
     * system navigation inset and [contentGap]. Only a screen that draws its list under the dock gets a value here; it
     * pads its scrolling content and its own buttons by it. Everywhere else the content already ends above the dock.
     */
    val contentInset: Dp = 0.dp
) {
    /** Space a floating dock needs under the content; a pinned bar already occupies layout space and needs none. */
    val reservedHeight: Dp get() = if (floating) height + bottomMargin + contentGap else 0.dp
}

/**
 * Bottom padding a scrolling main tab adds so its last row clears a floating glass dock the tab draws under. Zero in
 * every layout where the content already ends above the dock.
 */
val NavigationDockGeometry.scrollClearance: Dp get() = contentInset

/** Air between the content area (list, mini-player) and the floating capsule. */
internal val DefaultNavigationDockGap: Dp = 8.dp

/**
 * The default is a pinned bar of an unknown height: screens still outside the scaffold (previews, debug harnesses,
 * tests) keep the ordinary bottom layout instead of reserving a capsule that is not there.
 */
val LocalNavigationDock = staticCompositionLocalOf { NavigationDockGeometry(floating = false) }

/**
 * Where the create-chat button sits. It follows the placement instead of one fixed 20/16 pair:
 *
 * * a floating capsule already keeps the content above it, so the button keeps a small gap above the dock and lines up
 *   with the capsule's side margin;
 * * a pinned bar occupies its own slot, so the button keeps the classic bottom-end position.
 */
@Immutable
data class CreateButtonPlacement(val endPadding: Dp, val bottomPadding: Dp) {
    /** Extra list padding that lets the last conversation scroll clear of the button. */
    val listClearance: Dp get() = CreateButtonSize + bottomPadding + 32.dp
}

internal val CreateButtonSize: Dp = 56.dp

internal fun createButtonPlacement(dock: NavigationDockGeometry): CreateButtonPlacement =
    if (dock.floating) {
        CreateButtonPlacement(
            endPadding = dock.horizontalMargin + 4.dp,
            bottomPadding = dock.contentInset + dock.contentGap + 4.dp
        )
    } else {
        CreateButtonPlacement(endPadding = 20.dp, bottomPadding = 16.dp)
    }


