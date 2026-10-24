package com.beykus.beygram.ui.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Android's minimum recommended touch target. A visual control may stay smaller, but the area that
 * accepts a tap must be at least this size.
 */
internal val MinimumTouchTarget: Dp = 48.dp

/** Grows a control's interactive box to [minimum] without shrinking controls that are already large enough. */
internal fun accessibleTouchTarget(size: Dp, minimum: Dp = MinimumTouchTarget): Dp = maxOf(size, minimum)

/**
 * Joins the parts of one spoken label. Blank parts and repeated parts are dropped so TalkBack does
 * not read the same title, time or status twice when a row is merged into one focus stop.
 */
internal fun accessibleDescription(vararg parts: String?): String {
    val result = mutableListOf<String>()
    parts.forEach { part ->
        val value = part?.trim().orEmpty()
        if (value.isNotEmpty() && result.none { it.equals(value, ignoreCase = true) }) result += value
    }
    return result.joinToString(", ")
}

/**
 * Adds a send/upload state to visible text only when the screen does not already show it.
 * Visible status labels stay in the row; this helper prevents an extra copy in the spoken label.
 */
internal fun accessibleStatusDescription(base: String, status: String?): String {
    val visible = base.trim()
    val state = status?.trim().orEmpty()
    return when {
        state.isEmpty() -> visible
        visible.isEmpty() -> state
        visible.contains(state, ignoreCase = true) -> visible
        else -> "$visible, $state"
    }
}

/** One spoken label for a chat row instead of separate stops for title, preview, time, badge and status icon. */
internal fun accessibleChatRowDescription(
    title: String,
    preview: String,
    time: String,
    unreadLabel: String?,
    mutedLabel: String?,
    statusLabel: String?
): String = accessibleDescription(title, preview, time, unreadLabel, mutedLabel, statusLabel)

/** Announces a newly shown error or upload state without moving focus away from the current control. */
internal fun Modifier.errorAnnouncement(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
