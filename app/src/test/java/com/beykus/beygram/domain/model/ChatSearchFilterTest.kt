package com.beykus.beygram.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchFilterTest {
    private fun message(
        id: Long = 1L,
        text: String = "Quarterly report",
        senderId: Long = 7L,
        dateSeconds: Long = 1_000L,
        kind: MediaKind? = null,
        link: String? = null
    ) = ChatMessage(
        id = id,
        chatId = 10L,
        text = text,
        time = "12:00",
        isOutgoing = false,
        dateSeconds = dateSeconds,
        senderId = senderId,
        media = kind?.let { MessageMedia(it, fileId = 3) },
        linkPreview = link?.let { MessageLinkPreview(it, it) }
    )

    @Test
    fun defaultFilterMatchesEverything() {
        assertTrue(ChatSearchFilter().matches(message(kind = MediaKind.Photo), "report"))
        assertFalse(ChatSearchFilter().hasDateRange)
        assertFalse(ChatSearchFilter().isActive)
        assertTrue(ChatSearchFilter().hasValidDateRange)
    }

    @Test
    fun dateRangeIncludesStartAndExcludesExclusiveEnd() {
        val filter = ChatSearchFilter(fromDateSeconds = 1_000L, toDateSeconds = 2_000L)
        assertTrue(filter.matchesDate(message(dateSeconds = 1_000L)))
        assertTrue(filter.matchesDate(message(dateSeconds = 1_999L)))
        assertFalse(filter.matchesDate(message(dateSeconds = 999L)))
        assertFalse(filter.matchesDate(message(dateSeconds = 2_000L)))
        assertFalse(filter.matchesDate(message(dateSeconds = 0L)))
    }

    @Test
    fun onlyOneDateBoundStillFilters() {
        val after = ChatSearchFilter(fromDateSeconds = 1_000L)
        assertFalse(after.matchesDate(message(dateSeconds = 999L)))
        assertTrue(after.matchesDate(message(dateSeconds = 1_000L)))

        val before = ChatSearchFilter(toDateSeconds = 2_000L)
        assertTrue(before.matchesDate(message(dateSeconds = 1_999L)))
        assertFalse(before.matchesDate(message(dateSeconds = 2_000L)))
        assertFalse(before.matchesDate(message(dateSeconds = 0L)))
    }

    @Test
    fun combinedTextSenderTypeAndDateFilter() {
        val filter = ChatSearchFilter(
            senderId = 7L,
            kind = SharedMediaKind.Files,
            fromDateSeconds = 900L,
            toDateSeconds = 1_100L
        )
        val target = message(text = "Quarterly report", dateSeconds = 1_000L, kind = MediaKind.Document)
        assertTrue(filter.matches(target, "quarterly"))
        assertFalse(filter.matches(target, "invoice"))
        assertFalse(filter.matches(target.copy(senderId = 8L), "quarterly"))
        assertFalse(filter.matches(target.copy(media = MessageMedia(MediaKind.Photo, 3)), "quarterly"))
        assertFalse(filter.matches(target.copy(dateSeconds = 1_200L), "quarterly"))
    }

    @Test
    fun attachmentKindsMatchSharedMediaTabs() {
        assertTrue(ChatSearchFilter(kind = SharedMediaKind.Media).matches(message(kind = MediaKind.Photo)))
        assertTrue(ChatSearchFilter(kind = SharedMediaKind.Media).matches(message(kind = MediaKind.Video)))
        assertFalse(ChatSearchFilter(kind = SharedMediaKind.Media).matches(message(kind = MediaKind.Document)))
        assertTrue(ChatSearchFilter(kind = SharedMediaKind.Files).matches(message(kind = MediaKind.Document)))
        assertTrue(ChatSearchFilter(kind = SharedMediaKind.Voice).matches(message(kind = MediaKind.Voice)))
        assertTrue(ChatSearchFilter(kind = SharedMediaKind.Music).matches(message(kind = MediaKind.Audio)))
        assertTrue(ChatSearchFilter(kind = SharedMediaKind.Links).matches(message(link = "https://example.com/story")))
    }

    @Test
    fun sameDayIsValidButReversedDaysAreNot() {
        val zone = ZoneId.of("Europe/Moscow")
        val day = LocalDate.of(2026, 10, 24)
        val sameDay = ChatSearchFilter(
            fromDateSeconds = startOfDayEpochSeconds(day, zone),
            toDateSeconds = exclusiveEndOfDayEpochSeconds(day, zone)
        )
        assertTrue(sameDay.hasValidDateRange)

        val reversed = ChatSearchFilter(
            fromDateSeconds = startOfDayEpochSeconds(day.plusDays(1), zone),
            toDateSeconds = exclusiveEndOfDayEpochSeconds(day, zone)
        )
        assertFalse(reversed.hasValidDateRange)
    }

    @Test
    fun dateBoundariesUseTheGivenTimeZone() {
        val day = LocalDate.of(2026, 1, 15)
        val utc = ZoneId.of("UTC")
        assertEquals(Instant.parse("2026-01-15T00:00:00Z").epochSecond, startOfDayEpochSeconds(day, utc))
        assertEquals(Instant.parse("2026-01-16T00:00:00Z").epochSecond, exclusiveEndOfDayEpochSeconds(day, utc))
        assertEquals(day, searchBoundaryDate(Instant.parse("2026-01-16T00:00:00Z").epochSecond, endExclusive = true, zone = utc))
        assertEquals(day, searchBoundaryDate(Instant.parse("2026-01-15T00:00:00Z").epochSecond, endExclusive = false, zone = utc))
    }

    @Test
    fun aPageEmptiedByTheDateFilterStillExposesTheNextCursor() {
        assertTrue(MessageSearchPage(emptyList(), nextFromMessageId = 99L).hasMore)
        assertFalse(MessageSearchPage(emptyList(), nextFromMessageId = 0L).hasMore)
    }
}
