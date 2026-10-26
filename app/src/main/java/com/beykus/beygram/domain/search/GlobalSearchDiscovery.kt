package com.beykus.beygram.domain.search

/**
 * A value typed into the single chat-list search field. [raw] is kept for display and for message
 * search, while [publicText] is the normalized text sent to TDLib's chat, contact and public-chat
 * lookups: `@durov`, `t.me/durov` and `durov` all mean the same public username.
 *
 * [username] is set only for an explicit username form (`@name` or a t.me/telegram.me profile link).
 * Plain text is still searched publicly, but it is not advertised as a username target.
 */
data class GlobalSearchQuery(
    val raw: String,
    val publicText: String,
    val username: String?
) {
    val hasText: Boolean get() = raw.isNotBlank()
}

private val AT_USERNAME = Regex("^@([A-Za-z][A-Za-z0-9_]{0,63})$")
private val TELEGRAM_PROFILE_LINK = Regex(
    "(?i)^(?:https?://)?(?:www\\.)?(?:t\\.me|telegram\\.me)/(?:@)?([A-Za-z][A-Za-z0-9_]{0,63})/?$"
)

/**
 * Normalizes `@username`, `username` and `t.me/username` (with an optional scheme, www and trailing
 * slash). Link suffixes such as `?single` are ignored; deeper paths (a message link) are not treated
 * as a public profile and keep the raw query.
 */
fun parseGlobalSearchQuery(raw: String): GlobalSearchQuery {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return GlobalSearchQuery(raw = "", publicText = "", username = null)
    val linkCandidate = trimmed.substringBefore('?').substringBefore('#')
    val username = AT_USERNAME.matchEntire(trimmed)?.groupValues?.get(1)
        ?: TELEGRAM_PROFILE_LINK.matchEntire(linkCandidate)?.groupValues?.get(1)
    return GlobalSearchQuery(
        raw = trimmed,
        publicText = username ?: trimmed,
        username = username
    )
}

/**
 * The visible "Find in Telegram" action is never hidden below the local result list. It appears
 * for every non-blank query because chat/user/public search needs a network request.
 */
fun networkSearchAction(raw: String): GlobalSearchQuery? =
    parseGlobalSearchQuery(raw).takeIf { it.hasText }

/** The four explicit result groups; local and public rows never share a group. */
enum class GlobalSearchSection { Chats, PeopleAndBots, PublicResults, GlobalMessages }

/** Which source produced one row. The source decides both its section and its tap target. */
enum class GlobalSearchResultSource(val section: GlobalSearchSection) {
    LocalChat(GlobalSearchSection.Chats),
    LocalContact(GlobalSearchSection.PeopleAndBots),
    PublicResult(GlobalSearchSection.PublicResults),
    GlobalMessage(GlobalSearchSection.GlobalMessages)
}

/** Local chats open from cache; public rows need the explicit join/open step on the server. */
enum class GlobalSearchChatScope { Local, Public }

sealed interface GlobalSearchChatTarget {
    data class Local(val chatId: Long) : GlobalSearchChatTarget
    data class Public(val chatId: Long) : GlobalSearchChatTarget
}

fun GlobalSearchChatScope.openTarget(chatId: Long): GlobalSearchChatTarget = when (this) {
    GlobalSearchChatScope.Local -> GlobalSearchChatTarget.Local(chatId)
    GlobalSearchChatScope.Public -> GlobalSearchChatTarget.Public(chatId)
}
