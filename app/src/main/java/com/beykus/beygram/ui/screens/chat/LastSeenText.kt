package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.ui.text.russianPlural
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/**
 * Текст «был(а)…» для времени последнего входа. В режиме [LastSeenStyle.Relative] до суток это «5 минут назад»,
 * за предыдущий календарный день «вчера в 14:30», дальше дата. Время в будущем (часы телефона отстают) считаем «только что».
 */
internal fun lastSeenText(wasOnlineMillis: Long, nowMillis: Long, style: LastSeenStyle): UiText {
    if (style == LastSeenStyle.Exact) return exactLastSeen(wasOnlineMillis)
    val ago = (nowMillis - wasOnlineMillis).coerceAtLeast(0L)
    return when {
        ago < MINUTE_MS -> UiText.Res(R.string.presence_last_seen_just_now)
        ago < HOUR_MS -> (ago / MINUTE_MS).toInt().let { minutes ->
            UiText.Res(russianPlural(minutes, R.string.presence_last_seen_minutes_one, R.string.presence_last_seen_minutes_few, R.string.presence_last_seen_minutes_many), listOf(minutes))
        }
        ago < DAY_MS -> (ago / HOUR_MS).toInt().let { hours ->
            UiText.Res(russianPlural(hours, R.string.presence_last_seen_hours_one, R.string.presence_last_seen_hours_few, R.string.presence_last_seen_hours_many), listOf(hours))
        }
        isYesterday(wasOnlineMillis, nowMillis) -> UiText.Res(R.string.presence_last_seen_yesterday, listOf(timeText(wasOnlineMillis)))
        else -> exactLastSeen(wasOnlineMillis)
    }
}

/** Дата и время, как было до настройки; формат берётся из локали телефона. */
private fun exactLastSeen(wasOnlineMillis: Long): UiText =
    UiText.Res(R.string.presence_last_seen_at, listOf(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(wasOnlineMillis))))

private fun timeText(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** Был ли момент в предыдущем календарном дне. Считаем по календарю телефона, а не по 24 часам. */
private fun isYesterday(wasOnlineMillis: Long, nowMillis: Long): Boolean {
    val todayStart = startOfDay(nowMillis)
    return wasOnlineMillis >= startOfDay(todayStart - 1) && wasOnlineMillis < todayStart
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
