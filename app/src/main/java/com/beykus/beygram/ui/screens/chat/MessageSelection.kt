package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageCapabilities

/** Delete scopes supported by every message in a selection. An empty selection supports neither. */
internal data class BatchDeleteRights(val canDeleteForSelf: Boolean, val canDeleteForAll: Boolean) {
    val canDelete: Boolean get() = canDeleteForSelf || canDeleteForAll
}

internal fun batchDeleteRights(capabilities: List<MessageCapabilities>): BatchDeleteRights =
    if (capabilities.isEmpty()) BatchDeleteRights(canDeleteForSelf = false, canDeleteForAll = false)
    else BatchDeleteRights(
        canDeleteForSelf = capabilities.all { it.canDeleteForSelf },
        canDeleteForAll = capabilities.all { it.canDeleteForAll }
    )

/**
 * Telegram albums are selected as one unit: tapping any member selects every loaded member, and tapping a selected
 * member clears the whole album. A message without a nonzero mediaAlbumId is its own group.
 */
internal fun selectionGroupIds(messages: List<ChatMessage>, messageId: Long): Set<Long> {
    val message = messages.firstOrNull { it.id == messageId } ?: return setOf(messageId)
    val albumId = message.mediaAlbumId
    if (albumId == 0L || message.media == null) return setOf(messageId)
    return messages.asSequence()
        .filter { it.mediaAlbumId == albumId && it.media != null }
        .mapTo(linkedSetOf()) { it.id }
        .ifEmpty { setOf(messageId) }
}

/**
 * One ordered ID list for ForwardMessages. Album members are emitted together at the album's first selected position,
 * so TDLib sees a complete contiguous album even when another selected message sat between its members.
 */
internal fun forwardIdsForSelection(messages: List<ChatMessage>, selected: Set<Long>): List<Long> {
    if (selected.isEmpty()) return emptyList()
    val chosen = messages.filter { it.id in selected }
    val emittedAlbums = mutableSetOf<Long>()
    val result = mutableListOf<Long>()
    for (message in chosen) {
        val albumId = message.mediaAlbumId.takeIf { it != 0L && message.media != null }
        if (albumId == null) result += message.id
        else if (emittedAlbums.add(albumId)) {
            chosen.asSequence()
                .filter { it.mediaAlbumId == albumId && it.media != null }
                .mapTo(result) { it.id }
        }
    }
    return result.distinct()
}

/** Text of the selected messages, oldest first, captions included; blank messages are skipped. */
internal fun selectedText(messages: List<ChatMessage>, selected: Set<Long>): String =
    messages.asSequence()
        .filter { it.id in selected }
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
