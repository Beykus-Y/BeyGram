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

/** A gallery update must never interrupt an in-progress gesture. */
internal fun mediaViewerShouldScrollToSelection(
    isScrollInProgress: Boolean,
    settledPage: Int,
    selectedIndex: Int
): Boolean = !isScrollInProgress && settledPage != selectedIndex

/**
 * Resolves the active media viewer page list from [windowPages] and [historyPages].
 *
 * - Drops any stale [windowPages] entry that falls inside [historyMessageIdRange] but is absent
 *   from [liveHistoryIds] so deleted messages are never resurrected.
 * - Merges [historyPages] and filtered [windowPages] (preferring [historyPages] for freshest live
 *   state, deduplicated by `messageId`, sorted chronologically) ONLY when they overlap (share at
 *   least one `messageId`, or [windowPages] overlaps [historyMessageIdRange] while [selectedId] is
 *   in [historyPages]). Disjoint segments are never stitched across an unloaded gap.
 */
internal fun resolveMediaViewerPages(
    windowPages: List<MediaViewerItem>,
    historyPages: List<MediaViewerItem>,
    selectedId: Long?,
    fallbackItem: MediaViewerItem?,
    historyMessageIdRange: LongRange? = null,
    liveHistoryIds: Set<Long>? = null,
): List<MediaViewerItem> {
    val filteredWindow = if (historyMessageIdRange != null && liveHistoryIds != null) {
        windowPages.filter { item ->
            item.messageId !in historyMessageIdRange || item.messageId in liveHistoryIds
        }
    } else {
        windowPages
    }

    val historyIds = historyPages.mapTo(HashSet(historyPages.size)) { it.messageId }
    val windowIds = filteredWindow.mapTo(HashSet(filteredWindow.size)) { it.messageId }
    val sharesItem = filteredWindow.isNotEmpty() && historyPages.isNotEmpty() &&
        filteredWindow.any { it.messageId in historyIds }
    val touchesHistoryRange = filteredWindow.isNotEmpty() &&
        historyMessageIdRange != null &&
        selectedId != null &&
        selectedId in historyIds &&
        filteredWindow.any { it.messageId in historyMessageIdRange }

    if (sharesItem || touchesHistoryRange) {
        val byId = LinkedHashMap<Long, MediaViewerItem>(filteredWindow.size + historyPages.size)
        for (item in filteredWindow) byId[item.messageId] = item
        // Prefer live chat history entries when both sources contain the same message.
        for (item in historyPages) byId[item.messageId] = item
        return byId.values.sortedBy { it.messageId }
    }

    if (selectedId != null) {
        if (selectedId in windowIds) return filteredWindow
        if (selectedId in historyIds) return historyPages
    }
    return listOfNotNull(fallbackItem)
}
