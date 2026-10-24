package com.beykus.beygram.domain.search

import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview

/**
 * Identifies one global search request. [generation] changes whenever the session is reset for a
 * new query or account, so a late answer from the previous owner can never be shown.
 */
data class GlobalSearchRequest(val query: String, val generation: Long)

/** True when [request] belongs to an older query or an older search/account generation. */
fun isStaleGlobalSearchRequest(
    request: GlobalSearchRequest,
    currentQuery: String,
    currentGeneration: Long
): Boolean = !isCurrentSearch(request.query, currentQuery) || request.generation != currentGeneration

/**
 * Appends a page to the rows already on screen. TDLib may overlap pages or repeat entries between
 * local and server search, so the first occurrence keeps its position and later duplicates are skipped.
 */
fun <T, K> mergeGlobalSearchResults(existing: List<T>, incoming: List<T>, key: (T) -> K): List<T> =
    appendDistinctBy(existing, incoming, key)

/** Chats are unique by their TDLib chat id; the first matching row keeps the source order. */
fun mergeGlobalChatResults(existing: List<ChatPreview>, incoming: List<ChatPreview>): List<ChatPreview> =
    mergeGlobalSearchResults(existing, incoming) { it.id }

/** Contacts are unique by user id; the first matching row keeps the source order. */
fun mergeGlobalContactResults(existing: List<ContactPreview>, incoming: List<ContactPreview>): List<ContactPreview> =
    mergeGlobalSearchResults(existing, incoming) { it.userId }
