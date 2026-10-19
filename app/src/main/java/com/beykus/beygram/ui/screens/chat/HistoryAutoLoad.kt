package com.beykus.beygram.ui.screens.chat

private const val HISTORY_PREFETCH_DISTANCE = 6

/**
 * In a reverse-layout chat the OLDEST message has the largest LazyColumn index.
 * This pure predicate avoids requests before the unread boundary is positioned,
 * while a load is in progress, or after an error (retry remains explicit).
 */
internal fun shouldAutoLoadOlderMessages(
    lastVisibleItemIndex: Int,
    loadedItemCount: Int,
    hasOlderMessages: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
    openingSettled: Boolean
): Boolean =
    openingSettled && hasOlderMessages && !isLoading && !hasError &&
        loadedItemCount > 0 && lastVisibleItemIndex >= (loadedItemCount - 1 - HISTORY_PREFETCH_DISTANCE).coerceAtLeast(0)
