package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ForumTopic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * TDLib sends a topic icon colour as 0xRRGGBB, not as an ARGB colour. Zero means that the topic has no server colour;
 * the row then uses the palette's tonal tile instead.
 */
internal fun forumTopicIconColor(colorArgb: Int): Color? {
    val rgb = colorArgb and 0x00FFFFFF
    if (rgb == 0) return null
    return Color(0xFF000000.toInt() or rgb)
}

/** WCAG relative contrast between two opaque colours. Used to pick readable ink over a server-provided tile colour. */
internal fun forumTopicContrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    val lighter = maxOf(firstLuminance, secondLuminance)
    val darker = minOf(firstLuminance, secondLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Black or white, whichever is easier to read on [background]. The server picks the tile colour, so neither palette ink
 * is guaranteed to contrast with it; black and white cover both light and dark server colours.
 */
internal fun forumTopicIconInk(background: Color): Color =
    if (forumTopicContrastRatio(background, Color.Black) >= forumTopicContrastRatio(background, Color.White)) {
        Color.Black
    } else {
        Color.White
    }

/** The General topic is the forum's root and cannot be deleted, even by someone who may manage topics. */
internal fun canDeleteForumTopic(topic: ForumTopic): Boolean = topic.canManage && !topic.isGeneral

/** A row-sized representation of the last message time. [Empty] is a topic whose last message time is unknown. */
internal sealed interface ForumTopicMoment {
    data object Empty : ForumTopicMoment
    data class Clock(val text: String) : ForumTopicMoment
    data object Yesterday : ForumTopicMoment
    data class Date(val text: String) : ForumTopicMoment
}

/**
 * Today shows the local time, yesterday uses the localized word, and anything older a short date. The helper is pure so
 * the boundary cases (zero seconds, midnight, yesterday) can be tested without a device.
 */
internal fun forumTopicMoment(
    dateSeconds: Long,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): ForumTopicMoment {
    if (dateSeconds <= 0L) return ForumTopicMoment.Empty
    val moment = Instant.ofEpochSecond(dateSeconds).atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val date = moment.toLocalDate()
    return when {
        date == today -> ForumTopicMoment.Clock(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(moment)
        )
        date == today.minusDays(1) -> ForumTopicMoment.Yesterday
        else -> ForumTopicMoment.Date(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(date)
        )
    }
}

/** The compact right-hand label of a topic row; empty when the topic has no usable date. */
@Composable
internal fun forumTopicTimeLabel(dateSeconds: Long): String {
    // Date/time formatting allocates; a row must not redo it for every unrelated recomposition.
    val moment = remember(dateSeconds) { forumTopicMoment(dateSeconds) }
    return when (moment) {
        ForumTopicMoment.Empty -> ""
        is ForumTopicMoment.Clock -> moment.text
        ForumTopicMoment.Yesterday -> stringResource(R.string.forum_topic_time_yesterday)
        is ForumTopicMoment.Date -> moment.text
    }
}
