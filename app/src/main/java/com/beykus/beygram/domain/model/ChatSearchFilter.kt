package com.beykus.beygram.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * In-chat search filters. [fromDateSeconds] is the inclusive start of the first chosen day, while
 * [toDateSeconds] is the exclusive start of the day after the last chosen day, both in the device time zone.
 * TDLib has no date range in SearchChatMessages, so the date part is applied to every returned page locally.
 */
data class ChatSearchFilter(
    val senderId: Long? = null,
    val kind: SharedMediaKind? = null,
    val fromDateSeconds: Long? = null,
    val toDateSeconds: Long? = null
) {
    val isActive: Boolean
        get() = senderId != null || kind != null || fromDateSeconds != null || toDateSeconds != null

    val hasDateRange: Boolean
        get() = fromDateSeconds != null || toDateSeconds != null

    /** Equal bounds describe an empty half-open range, so the end day must be strictly after the start day. */
    val hasValidDateRange: Boolean
        get() = fromDateSeconds == null || toDateSeconds == null || fromDateSeconds < toDateSeconds

    /**
     * Pure client-side check used for date filtering of TDLib pages and as the single definition of the filter
     * semantics. [query] is optional: the server performs the text match, but tests and previews can combine it.
     */
    fun matches(message: ChatMessage, query: String = ""): Boolean {
        val needle = query.trim()
        if (needle.isNotEmpty() && !message.text.contains(needle, ignoreCase = true)) return false
        if (senderId != null && message.senderId != senderId) return false
        if (kind != null && !kind.matches(message)) return false
        return matchesDate(message)
    }

    /** Only the part TDLib cannot do: the returned page is filtered without changing its pagination cursor. */
    fun matchesDate(message: ChatMessage): Boolean {
        if (!hasDateRange) return true
        val date = message.dateSeconds
        if (date <= 0L) return false
        if (fromDateSeconds != null && date < fromDateSeconds) return false
        if (toDateSeconds != null && date >= toDateSeconds) return false
        return true
    }
}

private val URL_LIKE = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")

private fun SharedMediaKind.matches(message: ChatMessage): Boolean = when (this) {
    SharedMediaKind.Media -> message.media?.kind == MediaKind.Photo || message.media?.kind == MediaKind.Video
    SharedMediaKind.Files -> message.media?.kind == MediaKind.Document
    SharedMediaKind.Links -> message.linkPreview != null ||
        message.entities.any { it.kind == TextStyleKind.Url } ||
        URL_LIKE.containsMatchIn(message.text)
    SharedMediaKind.Voice -> message.media?.kind == MediaKind.Voice
    SharedMediaKind.Music -> message.media?.kind == MediaKind.Audio
}

/** Inclusive Unix timestamp of the start of [date] in [zone]. */
fun startOfDayEpochSeconds(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atStartOfDay(zone).toEpochSecond()

/** Exclusive Unix timestamp just after [date] in [zone], used as the inclusive end day's upper bound. */
fun exclusiveEndOfDayEpochSeconds(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.plusDays(1).atStartOfDay(zone).toEpochSecond()

/** The calendar day a search boundary belongs to; an exclusive end belongs to the previous day at the limit. */
fun searchBoundaryDate(seconds: Long, endExclusive: Boolean, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochSecond(if (endExclusive) seconds - 1 else seconds).atZone(zone).toLocalDate()
