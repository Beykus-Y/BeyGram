package com.beykus.beygram.domain.model

/** One command advertised by a bot for the current account. TDLib returns the text without a leading slash. */
data class BotCommand(val command: String, val description: String)

/** Commands of a private bot chat. A null menu means the chat is not a bot or the bot has no commands. */
data class BotCommandMenu(val botUserId: Long, val commands: List<BotCommand>)

/** A draft that starts with `@bot` and optionally a query. */
data class ParsedInlineQuery(val username: String, val query: String)

private val INLINE_QUERY = Regex("^@([A-Za-z][A-Za-z0-9_]{4,31})(?:\\s+([\\s\\S]*))?$")

/**
 * Telegram usernames are 5-32 characters, start with a letter and contain letters, digits and underscores.
 * The mention must be the first thing in the composer; an ordinary sentence containing @name stays a message.
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
    val thumbnailFileId: Int? = null
)

data class InlineBotResults(
    val queryId: Long,
    val botUserId: Long,
    val results: List<InlineBotResult>,
    val buttonText: String = "",
    /** Only a validated https URL of an inline-results Web App button; never a t.me link or an arbitrary scheme. */
    val buttonWebAppUrl: String = ""
)
