package com.beykus.beygram.ui.screens.chat

/**
 * Keep the viewer on the same media ID when possible; if that media is removed,
 * the caller may select the first surviving item. Only call with a nonempty list.
 */
internal fun mediaViewerSelectedIndex(pageIds: List<Long>, selectedId: Long): Int =
    pageIds.indexOf(selectedId).takeIf { it >= 0 } ?: 0

/**
 * Compose can briefly request a stale index after the list shrinks.
 * A distinct, temporary key prevents an out-of-bounds crash; no page content is
 * rendered for that index, and real items retain their message ID keys.
 */
internal fun mediaViewerPageKey(pageIds: List<Long>, index: Int): Any =
    pageIds.getOrNull(index) ?: "unavailable-page-$index"
