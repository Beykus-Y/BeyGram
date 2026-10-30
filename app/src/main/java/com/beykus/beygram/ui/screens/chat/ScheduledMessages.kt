package com.beykus.beygram.ui.screens.chat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One-shot send choices for the composer. Selecting an option sends the current draft/media immediately
 * with that option; nothing is remembered for a later send.
 */
@Composable
internal fun SendOptionsDialog(
    canSchedule: Boolean,
    onDismiss: () -> Unit,
    onSendNow: () -> Unit,
    onSendSilently: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val timeZone = remember {
        val zone = TimeZone.getDefault()
        zone.getDisplayName(zone.inDaylightTime(Date()), TimeZone.SHORT)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_send_options), color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chat_send_options_timezone, timeZone), color = palette.textSecondary, fontSize = 12.sp)
                SendOption(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.chat_send_now), onClick = onSendNow)
                SendOption(Icons.Rounded.VolumeOff, stringResource(R.string.chat_send_silently), onClick = onSendSilently)
                if (canSchedule) {
                    SendOption(Icons.Rounded.Schedule, stringResource(R.string.chat_send_later), onClick = {
                        showSchedulePicker(context) { at ->
                            if (!at.isFuture()) error = context.getString(R.string.err_schedule_time_future)
                            else onSchedule(at)
                        }
                    })
                } else {
                    Text(
                        stringResource(R.string.chat_schedule_secret_unavailable),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).errorAnnouncement()) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun SendOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = palette.textPrimary, fontSize = 15.sp, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
    }
}

/** Shows the platform date picker followed by the platform time picker in the device time zone. */
private fun showSchedulePicker(context: Context, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance()
    val datePicker = DatePickerDialog(context, { _, year, month, day ->
        val clock = Calendar.getInstance()
        TimePickerDialog(context, { _, hour, minute ->
            val chosen = Calendar.getInstance().apply {
                set(year, month, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPicked(chosen.timeInMillis / 1000L)
        }, clock.get(Calendar.HOUR_OF_DAY), clock.get(Calendar.MINUTE), true).show()
    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
    datePicker.datePicker.minDate = now.timeInMillis
    datePicker.show()
}

private fun Long.isFuture(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
    this > nowSeconds + MessageSendOptions.MIN_SCHEDULE_LEAD_SECONDS

private const val SCHEDULED_REFRESH_MS = 5_000L

/** TDLib is the source of truth: this sheet always reloads the queue from the server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduledMessagesDialog(
    chatId: Long,
    conversation: ConversationRepository,
    onClose: () -> Unit,
    onCompose: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember(chatId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember(chatId) { mutableStateOf(true) }
    var busy by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var reload by remember(chatId) { mutableIntStateOf(0) }
    var editing by remember(chatId) { mutableStateOf<ChatMessage?>(null) }

    // TDLib is the source of truth: delivery, another client's edit/cancel and upload failures all change the queue.
    // Keep the open dialog in sync without flickering the initial spinner.
    LaunchedEffect(chatId, reload, conversation) {
        loading = true
        error = null
        while (true) {
            val result = conversation.scheduledMessages(chatId)
            result.onSuccess {
                messages = it
                error = null
            }.onFailure {
                if (messages.isEmpty()) messages = emptyList()
                error = it.userMessage(context, R.string.chat_scheduled_load_failed)
            }
            loading = false
            delay(SCHEDULED_REFRESH_MS)
        }
    }

    fun runAction(action: suspend () -> Result<Unit>, failure: Int) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                val result = action()
                if (result.isSuccess) reload++ else error = result.exceptionOrNull().userMessage(context, failure)
            } finally {
                busy = false
            }
        }
    }

    val editingMessage = editing
    if (editingMessage != null) {
        ScheduledMessageEditDialog(
            chatId = chatId,
            message = editingMessage,
            conversation = conversation,
            onClose = { editing = null },
            onSaved = { editing = null; reload++ }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onClose() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        contentColor = palette.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.textSecondary) }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.chat_scheduled_title),
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose, enabled = !busy) {
                    Text(stringResource(R.string.common_close))
                }
            }
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(bottom = 12.dp))
            error?.let {
                Text(
                    it,
                    color = palette.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp).errorAnnouncement()
                )
            }
            when {
                loading -> CircularProgressIndicator(
                    Modifier.size(24.dp).padding(bottom = 4.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
                messages.isEmpty() -> if (error == null) {
                    Text(
                        stringResource(R.string.chat_scheduled_empty),
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    if (onCompose != null) {
                        Text(
                            stringResource(R.string.chat_scheduled_compose_hint),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        ScheduledMessageRow(
                            message = message,
                            busy = busy,
                            onEdit = { editing = message },
                            onReschedule = {
                                showSchedulePicker(context) { at ->
                                    if (!at.isFuture()) error = context.getString(R.string.err_schedule_time_future)
                                    else runAction(
                                        { conversation.rescheduleMessage(chatId, message.id, at) },
                                        R.string.chat_scheduled_reschedule_failed
                                    )
                                }
                            },
                            onSendNow = {
                                runAction(
                                    { conversation.sendScheduledNow(chatId, message.id) },
                                    R.string.chat_scheduled_send_now_failed
                                )
                            },
                            onRetry = {
                                runAction(
                                    { conversation.retryMessages(chatId, listOf(message.id)) },
                                    R.string.chat_scheduled_retry_failed
                                )
                            },
                            onCancel = {
                                runAction(
                                    { conversation.deleteScheduledMessage(chatId, message.id) },
                                    R.string.chat_scheduled_cancel_failed
                                )
                            }
                        )
                        HorizontalDivider(color = palette.divider)
                    }
                }
            }
            if (!loading && !busy && error == null && messages.isEmpty() && onCompose != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCompose) {
                        Text(stringResource(R.string.chat_scheduled_compose_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledMessageRow(
    message: ChatMessage,
    busy: Boolean,
    onEdit: () -> Unit,
    onReschedule: () -> Unit,
    onSendNow: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = beyGramPalette()
    val failed = message.sendState == MessageSendState.Failed
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(message.summary(), color = palette.textPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(scheduledTimeLabel(message), color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        if (failed) Text(stringResource(R.string.chat_scheduled_failed), color = palette.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            if (failed) TextButton(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.common_retry)) }
            TextButton(onClick = onReschedule, enabled = !busy) { Text(stringResource(R.string.chat_scheduled_reschedule)) }
            TextButton(onClick = onSendNow, enabled = !busy) { Text(stringResource(R.string.chat_send_now)) }
            TextButton(onClick = onEdit, enabled = !busy) { Text(stringResource(R.string.common_edit)) }
            TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.chat_scheduled_cancel)) }
        }
    }
}

@Composable
private fun ScheduledMessageEditDialog(
    chatId: Long,
    message: ChatMessage,
    conversation: ConversationRepository,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(message.id) { mutableStateOf(message.text) }
    var entities by remember(message.id) { mutableStateOf(message.entities) }
    var saving by remember(message.id) { mutableStateOf(false) }
    var error by remember(message.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(stringResource(R.string.common_edit), color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp).errorAnnouncement()) }
                FormattingTextField(
                    value = text,
                    entities = entities,
                    onChange = { text = it },
                    onEntitiesChange = { entities = it },
                    placeholder = stringResource(R.string.chat_message),
                    locked = saving,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && (text != message.text || entities != message.entities),
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        try {
                            val result = conversation.editMessage(chatId, message.id, text, entities)
                            if (result.isSuccess) onSaved() else error = result.exceptionOrNull().userMessage(context, R.string.chat_scheduled_edit_failed)
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !saving) { Text(stringResource(R.string.common_cancel)) } }
    )
}

private fun scheduledTimeLabel(message: ChatMessage): String =
    message.scheduledAtSeconds?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000L))
    } ?: message.time
