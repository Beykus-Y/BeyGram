package com.beykus.beygram.domain.model

/**
 * One page of a people/chat search source. TDLib's chat and contact search has no offset, so a
 * larger limit is requested for the next page and the rows are merged by their stable ids.
 * [totalCount] is the server's estimate for the whole source and lets the UI decide whether more
 * rows are available.
 */
data class GlobalSearchPage<T>(
    val items: List<T>,
    val totalCount: Int
)

/** Result of the explicit tap on a public search result. */
sealed interface PublicChatOpenResult {
    /** The chat can be opened without an additional step. */
    data class Opened(val chatId: Long) : PublicChatOpenResult

    /** The group requires approval; the request was sent and the screen should explain that. */
    data object JoinRequestSent : PublicChatOpenResult
}
