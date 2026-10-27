package com.beykus.beygram.domain.model

/** One command advertised by a bot for the current account. TDLib returns the text without a leading slash. */
data class BotCommand(val command: String, val description: String)

/**
 * Commands of a private bot chat. A null menu means the chat is not a bot or the bot has no commands. This TDLib
 * build has no `GetBotCommands`/`UpdateBotCommands`; `BotCommandScope`/`GetCommands` are owner-side APIs and cannot
 * resolve another bot's group-scoped commands, so groups and channels deliberately get no command menu.
 */
data class BotCommandMenu(val botUserId: Long, val commands: List<BotCommand>)

/** A draft that starts with `@bot` and optionally a query. */
data class ParsedInlineQuery(val username: String, val query: String)

// Legacy inline bots such as @gif, @pic and @wiki have short usernames, so length alone must not hide them.
private val INLINE_QUERY = Regex("^@([A-Za-z][A-Za-z0-9_]{2,31})(?:\\s+([\\s\\S]*))?$")

/**
 * A mention of 3-32 characters starts with a letter and contains letters, digits and underscores. The mention must be
 * the first thing in the composer; an ordinary sentence containing @name stays a message. TDLib verifies that the
 * resolved user is an inline bot before any query is sent.
 */
fun parseInlineQuery(text: String): ParsedInlineQuery? {
    if (!text.startsWith("@")) return null
    val match = INLINE_QUERY.matchEntire(text) ?: return null
    return ParsedInlineQuery(match.groupValues[1], match.groupValues[2].trim())
}

enum class InlineBotResultKind { Article, Photo, Video, Animation, Audio, Document, Voice, Sticker, Contact, Location, Venue, Game, Other }

/** Display-only projection of TdApi.InlineQueryResult; sending always goes back through TDLib by [id]. */
data class InlineBotResult(
    val id: String,
    val title: String,
    val description: String,
    val kind: InlineBotResultKind,
    val thumbnailFileId: Int? = null,
    /** Each paginated request has its own TDLib query id; keep it with the result instead of using the last page's. */
    val queryId: Long = 0L
)

data class InlineBotResults(
    val queryId: Long,
    val botUserId: Long,
    val results: List<InlineBotResult>,
    /** TDLib's cursor for the next page; blank means this is the last page. */
    val nextOffset: String = "",
    val buttonText: String = "",
    /** Only a validated https URL of an inline-results Web App button; never a t.me link or an arbitrary scheme. */
    val buttonWebAppUrl: String = ""
)

/** Appends a page while keeping the first occurrence of every result id, even if TDLib repeats an id across pages. */
fun mergeInlineBotResults(existing: List<InlineBotResult>, page: List<InlineBotResult>): List<InlineBotResult> {
    val seen = HashSet<String>(existing.size + page.size)
    return (existing + page).filter { seen.add(it.id) }
}

/** Stops pagination when TDLib reports no cursor or repeats the cursor that produced the same page. */
fun nextInlineQueryOffset(previousOffset: String, nextOffset: String): String =
    if (nextOffset.isBlank() || nextOffset == previousOffset) "" else nextOffset

/** A music bot track downloaded to a local file. The temporary chat message it came from is already removed. */
data class MusicBotTrack(
    val path: String,
    val title: String,
    val performer: String,
    val durationSeconds: Int,
    val mimeType: String,
    val sizeBytes: Long
)
