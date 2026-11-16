package com.beykus.beygram.ui.screens.share

import com.beykus.beygram.domain.model.ChatPreview

/**
 * Offer the user's recent writable conversations, not alphabetical suggestions that bury
 * the intended chat. Saved Messages has a separate quick action and is never duplicated.
 */
internal fun shareTargetCandidates(
    chats: List<ChatPreview>, query: String, excludeSaved: Boolean = false
): List<ChatPreview> {
    val needle = query.trim()
    return chats.asSequence()
        .filter { it.canPost && it.title.isNotBlank() }
        .filter { !excludeSaved || !it.isSavedMessages }
        .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<ChatPreview> { it.isSavedMessages }
                .thenByDescending { it.order > 0L }
                .thenByDescending { it.order }
                .thenByDescending { it.archiveOrder }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        ).toList()
}

