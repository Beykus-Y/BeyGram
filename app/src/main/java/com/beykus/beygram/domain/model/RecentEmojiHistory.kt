package com.beykus.beygram.domain.model

/**
 * Emoji recovered from the account's own sent messages, newest first. Telegram has no server-side recent-emoji list:
 * each client keeps its own, so a fresh install can only rebuild one from local history. The lists are already
 * deduplicated and bounded by the caller's limits.
 */
data class RecentEmojiHistory(
    val unicode: List<String> = emptyList(),
    val customEmojiIds: List<Long> = emptyList()
) {
    val isEmpty: Boolean get() = unicode.isEmpty() && customEmojiIds.isEmpty()
}