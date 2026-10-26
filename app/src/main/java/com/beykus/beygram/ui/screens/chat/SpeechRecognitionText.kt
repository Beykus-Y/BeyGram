package com.beykus.beygram.ui.screens.chat

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Localised date for a Telegram quota reset. Kept pure so the formatting rules are unit-testable. */
internal fun formatTranscriptionResetDate(
    seconds: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    val format = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    format.timeZone = timeZone
    return format.format(Date(seconds.coerceAtLeast(0L) * 1000L))
}

/** Compact duration for "free transcription up to ..." explanations, for example 0:59 or 1:30. */
internal fun formatTranscriptionDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, safe / 60, safe % 60)
}
