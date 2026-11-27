package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier

/**
 * Keep the chat viewport open to the navigation bar. The IME is different: it is
 * an opaque, transient surface, so it still resizes the chat and its overlays.
 */
internal fun Modifier.chatViewportInsets(safeDrawing: WindowInsets, ime: WindowInsets): Modifier =
    windowInsetsPadding(safeDrawing.only(WindowInsetsSides.Horizontal))
        .windowInsetsPadding(ime.only(WindowInsetsSides.Bottom))

/**
 * Applied inside the bottom chrome's existing background, or to an empty spacer
 * when no composer is present. Compose subtracts the IME already consumed by
 * the viewport; the two owners reserve max(system bottom, IME), not their sum.
 */
internal fun Modifier.chatBottomInsets(safeDrawing: WindowInsets): Modifier =
    windowInsetsPadding(safeDrawing.only(WindowInsetsSides.Bottom))

/** The picker replaces the part of the keyboard above the system navigation area. */
internal fun chatKeyboardReplacementHeight(imePx: Int, systemBottomPx: Int): Int =
    (imePx - systemBottomPx).coerceAtLeast(0)

/** Reverse timeline: top chrome covers the end offset and bottom chrome the start. */
internal fun chatVisibleItemHeight(
    offset: Int,
    size: Int,
    viewportStart: Int,
    viewportEnd: Int,
    coveredTop: Int,
    coveredBottom: Int
): Int = (minOf(offset + size, viewportEnd - coveredTop) -
    maxOf(offset, viewportStart + coveredBottom)).coerceAtLeast(0)

internal fun chatUncoveredViewportHeight(viewportHeight: Int, coveredTop: Int, coveredBottom: Int): Int =
    (viewportHeight - coveredTop - coveredBottom).coerceAtLeast(0)
