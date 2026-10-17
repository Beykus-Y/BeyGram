package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.domain.UiText
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class LastSeenTextTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val now = at(2026, Calendar.OCTOBER, 17, 12, 0)

    /** Момент по местному времени телефона, так же как его считает сама функция. */
    private fun at(year: Int, month: Int, day: Int, hourOfDay: Int, minuteOfHour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hourOfDay, minuteOfHour)
    }.timeInMillis

    private fun relativeAgo(millis: Long) = lastSeenText(now - millis, now, LastSeenStyle.Relative)

    @Test fun underAMinuteIsJustNow() {
        assertEquals(UiText.Res(R.string.presence_last_seen_just_now), relativeAgo(0))
        assertEquals(UiText.Res(R.string.presence_last_seen_just_now), relativeAgo(59_999))
    }

    @Test fun wasOnlineInTheFutureCountsAsJustNow() {
        assertEquals(UiText.Res(R.string.presence_last_seen_just_now), lastSeenText(now + 10_000, now, LastSeenStyle.Relative))
    }

    @Test fun minutesUseTheRussianForms() {
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_one, listOf(1)), relativeAgo(1 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_few, listOf(2)), relativeAgo(2 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_few, listOf(4)), relativeAgo(4 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_many, listOf(5)), relativeAgo(5 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_many, listOf(8)), relativeAgo(8 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_many, listOf(11)), relativeAgo(11 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_many, listOf(15)), relativeAgo(15 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_one, listOf(21)), relativeAgo(21 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_few, listOf(22)), relativeAgo(22 * minute))
        assertEquals(UiText.Res(R.string.presence_last_seen_minutes_many, listOf(59)), relativeAgo(59 * minute + 59_000))
    }

    @Test fun hoursUseTheRussianForms() {
        assertEquals(UiText.Res(R.string.presence_last_seen_hours_one, listOf(1)), relativeAgo(hour))
        assertEquals(UiText.Res(R.string.presence_last_seen_hours_few, listOf(2)), relativeAgo(2 * hour))
        assertEquals(UiText.Res(R.string.presence_last_seen_hours_many, listOf(5)), relativeAgo(5 * hour))
        assertEquals(UiText.Res(R.string.presence_last_seen_hours_many, listOf(12)), relativeAgo(12 * hour))
        assertEquals(UiText.Res(R.string.presence_last_seen_hours_one, listOf(21)), relativeAgo(21 * hour))
        assertEquals(UiText.Res(R.string.presence_last_seen_hours_few, listOf(23)), relativeAgo(23 * hour + 59 * minute))
    }

    @Test fun withinADayStaysRelativeEvenAcrossMidnight() {
        val earlyNow = at(2026, Calendar.OCTOBER, 17, 1, 0)
        val lateYesterday = at(2026, Calendar.OCTOBER, 16, 23, 0)
        assertEquals(UiText.Res(R.string.presence_last_seen_hours_few, listOf(2)), lastSeenText(lateYesterday, earlyNow, LastSeenStyle.Relative))
    }

    @Test fun moreThanADayAgoOnThePreviousDayShowsYesterdayAndTime() {
        val wasOnline = at(2026, Calendar.OCTOBER, 16, 9, 0)
        assertEquals(UiText.Res(R.string.presence_last_seen_yesterday, listOf(timeText(wasOnline))), lastSeenText(wasOnline, now, LastSeenStyle.Relative))
    }

    @Test fun olderThanYesterdayShowsDateAndTime() {
        val wasOnline = at(2026, Calendar.OCTOBER, 14, 9, 0)
        assertEquals(UiText.Res(R.string.presence_last_seen_at, listOf(dateTimeText(wasOnline))), lastSeenText(wasOnline, now, LastSeenStyle.Relative))
    }

    @Test fun exactStyleAlwaysShowsDateAndTime() {
        val fiveMinutesAgo = now - 5 * minute
        assertEquals(UiText.Res(R.string.presence_last_seen_at, listOf(dateTimeText(fiveMinutesAgo))), lastSeenText(fiveMinutesAgo, now, LastSeenStyle.Exact))
    }

    private fun timeText(millis: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
    private fun dateTimeText(millis: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}
