package com.beykus.beygram.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.CallRecord
import com.beykus.beygram.domain.model.CallStatus
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** История звонков только для чтения. Звонить и принимать звонки приложение пока не умеет, поэтому кнопка неактивна. */
@Composable
fun CallsScreen(
    loadCalls: suspend (offset: String) -> Result<CallHistoryPage>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var nextOffset by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(offset: String) {
        scope.launch {
            loading = true
            error = null
            loadCalls(offset)
                .onSuccess { page ->
                    calls = if (offset.isEmpty()) page.calls else calls + page.calls
                    nextOffset = page.nextOffset
                }
                .onFailure { error = it.message ?: context.getString(R.string.calls_load_failed) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load("") }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        Text(
            stringResource(R.string.calls_title), color = palette.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        when {
            calls.isEmpty() && loading -> Spacer(Modifier.weight(1f))
            calls.isEmpty() && error != null -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error.orEmpty(), color = palette.danger, textAlign = TextAlign.Center)
                TextButton(onClick = { load("") }) { Text(stringResource(R.string.common_retry)) }
            }
            calls.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.calls_empty), color = palette.textSecondary)
            }
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(calls, key = { "${it.chatId}:${it.messageId}" }) { call ->
                    CallRow(call, observeFile, downloadFile)
                }
                if (nextOffset.isNotEmpty()) item {
                    TextButton(onClick = { load(nextOffset) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) stringResource(R.string.calls_loading) else stringResource(R.string.calls_show_more))
                    }
                }
                error?.let { message ->
                    item { Text(message, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CallRow(call: CallRecord, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit) {
    val palette = beyGramPalette()
    val missed = call.status == CallStatus.Missed
    val statusTint = if (missed) palette.danger else palette.textSecondary
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(call.initials, Color(call.avatarColorArgb), 48.dp, call.photo, observeFile, downloadFile)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                call.title, color = if (missed) palette.danger else palette.textPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(call.directionIcon(), null, tint = statusTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(call.summary(), color = statusTint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCallDate(call.dateSeconds), color = palette.textSecondary, fontSize = 12.sp)
            IconButton(onClick = {}, enabled = false) { Icon(Icons.Rounded.Call, stringResource(R.string.calls_call_description), tint = palette.textSecondary) }
        }
    }
}

@Composable private fun CallRecord.summary(): String {
    val kind = if (isVideo) stringResource(R.string.calls_kind_video) else stringResource(R.string.calls_kind_call)
    val direction = if (isOutgoing) stringResource(R.string.calls_direction_outgoing) else stringResource(R.string.calls_direction_incoming)
    return when (status) {
        CallStatus.Answered -> listOf(direction, kind, formatDuration(durationSeconds)).joinToString(" · ")
        CallStatus.Missed -> stringResource(R.string.calls_summary_missed, kind)
        CallStatus.Declined -> stringResource(R.string.calls_summary_declined, kind)
        CallStatus.NoAnswer -> stringResource(R.string.calls_summary_no_answer, direction)
    }
}

private fun CallRecord.directionIcon(): ImageVector = when (status) {
    CallStatus.Answered -> if (isOutgoing) Icons.Rounded.CallMade else Icons.Rounded.CallReceived
    CallStatus.Missed -> Icons.Rounded.CallMissed
    CallStatus.Declined -> Icons.Rounded.CallEnd
    CallStatus.NoAnswer -> if (isOutgoing) Icons.Rounded.CallMade else Icons.Rounded.CallReceived
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}

/** Сегодняшние звонки показываем со временем, остальные — с датой. */
private fun formatCallDate(seconds: Long): String {
    val date = Date(seconds * 1000)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val format = if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.SHORT)
    return format.format(date)
}
