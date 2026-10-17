package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.snapshotFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val FLOATING_DATE_HIDE_DELAY_MS = 1500L

/**
 * Дата сообщения у верхнего края ленты: показывается во время прокрутки и гаснет через пару секунд после остановки.
 * День читается только из состояния списка, поэтому перерисовывается лишь при смене дня у верхнего сообщения.
 */
@Composable
internal fun FloatingMessageDate(listState: LazyListState, items: List<ChatMessageListItem>, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) visible = true
            else {
                delay(FLOATING_DATE_HIDE_DELAY_MS)
                visible = false
            }
        }
    }
    val day by remember(items) { derivedStateOf { topVisibleDay(listState, items) } }
    val shownDay = day
    if (visible && shownDay != null) Text(
        formatFloatingDay(context, shownDay, LocalDate.now(), stringResource(R.string.chat_date_today), stringResource(R.string.chat_date_yesterday)),
        color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(palette.surface)
            .border(1.dp, palette.divider, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Лента перевёрнута: сообщения с большим индексом выше на экране, поэтому самый верхний видимый элемент — с максимальным индексом. */
private fun topVisibleDay(listState: LazyListState, items: List<ChatMessageListItem>): LocalDate? {
    if (items.isEmpty()) return null
    val topIndex = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: return null
    // Служебные строки над историей (загрузка, «ещё») идут после сообщений; дата там — самого старого загруженного сообщения.
    val seconds = items[topIndex.coerceAtMost(items.lastIndex)].messages.firstOrNull()?.dateSeconds ?: return null
    if (seconds <= 0L) return null
    return Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun formatFloatingDay(context: Context, day: LocalDate, today: LocalDate, todayLabel: String, yesterdayLabel: String): String {
    val zone = ZoneId.systemDefault()
    return when (day) {
        today -> todayLabel
        today.minusDays(1) -> yesterdayLabel
        else -> {
            val millis = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val yearFlag = if (day.year != today.year) DateUtils.FORMAT_SHOW_YEAR else 0
            DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or yearFlag)
        }
    }
}
