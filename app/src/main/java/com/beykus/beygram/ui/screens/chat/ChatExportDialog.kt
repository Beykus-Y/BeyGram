package com.beykus.beygram.ui.screens.chat

import android.app.DatePickerDialog
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.R
import com.beykus.beygram.data.export.SafChatExportTarget
import com.beykus.beygram.data.export.safeExportFileNameWithExtension
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.domain.model.ChatExportFormat
import com.beykus.beygram.domain.model.ChatExportPhase
import com.beykus.beygram.domain.model.ChatExportProgress
import com.beykus.beygram.domain.model.ChatExportRequest
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.exclusiveEndOfDayEpochSeconds
import com.beykus.beygram.domain.model.searchBoundaryDate
import com.beykus.beygram.domain.model.startOfDayEpochSeconds
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Export setup, SAF picker, progress and cancellation for one chat. The export stays inside an app-scoped
 * ViewModel instead of a foreground service: it survives rotation but not process death; the file is deleted
 * or truncated on every cancel/error path.
 */
@Composable
internal fun ChatExportDialog(
    chat: ChatPreview,
    accountId: String,
    accountLabel: String,
    conversation: ConversationRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val model: ChatExportViewModel = viewModel(key = "chat-export-$accountId-${chat.id}")
    val state by model.state.collectAsStateWithLifecycle()
    var formatName by rememberSaveable(chat.id) { mutableStateOf(ChatExportFormat.Json.name) }
    val format = ChatExportFormat.entries.firstOrNull { it.name == formatName } ?: ChatExportFormat.Json
    var fromDateSeconds by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var toDateSeconds by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var includeMedia by rememberSaveable(chat.id) { mutableStateOf(false) }

    val primaryFallback = stringResource(R.string.secondary_accounts_primary)
    val secondaryFallback = stringResource(R.string.secondary_accounts_others)
    val sourceAccount = accountLabel.ifBlank {
        if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) primaryFallback else secondaryFallback
    }
    val zone = remember { ZoneId.systemDefault() }
    val mimeType = if (includeMedia) "application/zip" else when (format) {
        ChatExportFormat.Json -> "application/json"
        ChatExportFormat.Html -> "text/html"
    }
    val pickerContract = remember(mimeType) { ActivityResultContracts.CreateDocument(mimeType) }
    val picker = rememberLauncherForActivityResult(pickerContract) { uri ->
        if (uri != null) {
            model.start(
                conversation = conversation,
                chatId = chat.id,
                target = SafChatExportTarget(context.applicationContext, uri),
                request = ChatExportRequest(
                    format = format,
                    fromDateSeconds = fromDateSeconds,
                    toDateSeconds = toDateSeconds,
                    includeMedia = includeMedia,
                    accountLabel = sourceAccount
                )
            )
        }
    }

    val blockedReason = when {
        chat.isSecret -> R.string.chat_export_secret_blocked
        chat.hasProtectedContent -> R.string.chat_export_protected_blocked
        else -> null
    }
    val close = {
        model.reset()
        onDismiss()
    }
    when (val current = state) {
        ChatExportUiState.Idle -> {
            if (blockedReason != null) {
                ExportBlockedDialog(sourceAccount, blockedReason, close)
            } else {
                val from = fromDateSeconds
                val to = toDateSeconds
                val rangeValid = from == null || to == null || from < to
                val anyDate = stringResource(R.string.chat_export_any_date)
                AlertDialog(
                    onDismissRequest = close,
                    title = { Text(stringResource(R.string.chat_export_title)) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                stringResource(R.string.chat_export_source_account, sourceAccount),
                                color = palette.textSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.chat_export_format),
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = format == ChatExportFormat.Json,
                                    onClick = { formatName = ChatExportFormat.Json.name },
                                    label = { Text(stringResource(R.string.chat_export_format_json)) }
                                )
                                FilterChip(
                                    selected = format == ChatExportFormat.Html,
                                    onClick = { formatName = ChatExportFormat.Html.name },
                                    label = { Text(stringResource(R.string.chat_export_format_html)) }
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.chat_export_range),
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExportDateButton(
                                    label = stringResource(R.string.chat_export_date_from),
                                    value = from?.let { formatExportDate(it, endExclusive = false, zone) } ?: anyDate,
                                    onClick = {
                                        val initial = from?.let { searchBoundaryDate(it, false, zone) } ?: LocalDate.now(zone)
                                        DatePickerDialog(context, { _, year, month, day ->
                                            fromDateSeconds = startOfDayEpochSeconds(LocalDate.of(year, month + 1, day), zone)
                                        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                ExportDateButton(
                                    label = stringResource(R.string.chat_export_date_to),
                                    value = to?.let { formatExportDate(it, endExclusive = true, zone) } ?: anyDate,
                                    onClick = {
                                        val initial = to?.let { searchBoundaryDate(it, true, zone) } ?: LocalDate.now(zone)
                                        DatePickerDialog(context, { _, year, month, day ->
                                            toDateSeconds = exclusiveEndOfDayEpochSeconds(LocalDate.of(year, month + 1, day), zone)
                                        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (from != null || to != null) {
                                TextButton(onClick = { fromDateSeconds = null; toDateSeconds = null }) {
                                    Text(stringResource(R.string.chat_export_clear_dates))
                                }
                            }
                            if (!rangeValid) {
                                Text(
                                    stringResource(R.string.chat_export_date_invalid),
                                    color = palette.danger,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable { includeMedia = !includeMedia }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = includeMedia, onCheckedChange = null)
                                Column {
                                    Text(stringResource(R.string.chat_export_include_media), fontSize = 14.sp)
                                    Text(
                                        stringResource(R.string.chat_export_media_note),
                                        color = palette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.warningSurface)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    stringResource(R.string.chat_export_warning),
                                    color = palette.warningText,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = rangeValid,
                            onClick = {
                                picker.launch(suggestedExportName(chat.title, format, includeMedia))
                            }
                        ) {
                            Text(stringResource(R.string.chat_export_start), color = palette.accent)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = close) { Text(stringResource(R.string.common_cancel)) }
                    }
                )
            }
        }
        is ChatExportUiState.Running -> ExportProgressDialog(sourceAccount, current, onCancel = model::cancel)
        is ChatExportUiState.Completed -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.chat_export_completed_title)) },
            text = {
                Column {
                    ExportSourceAccount(sourceAccount)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(
                            R.string.chat_export_completed_summary,
                            current.summary.messages,
                            current.summary.mediaFiles,
                            Formatter.formatShortFileSize(context, current.summary.bytesWritten)
                        )
                    )
                    if (current.summary.skippedSaveRestricted > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.chat_export_skipped_restricted, current.summary.skippedSaveRestricted),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (current.summary.skippedMedia > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.chat_export_skipped_media, current.summary.skippedMedia),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = close) { Text(stringResource(R.string.common_close), color = palette.accent) }
            }
        )
        is ChatExportUiState.Failed -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.chat_export_failed_title)) },
            text = {
                Column {
                    ExportSourceAccount(sourceAccount)
                    Spacer(Modifier.height(10.dp))
                    Text(current.error.userMessage(context, R.string.err_chat_export_failed), color = palette.danger)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.chat_export_failed_note), color = palette.textSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = close) { Text(stringResource(R.string.common_close), color = palette.accent) }
            }
        )
        ChatExportUiState.Cancelled -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.chat_export_cancelled_title)) },
            text = {
                Column {
                    ExportSourceAccount(sourceAccount)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.chat_export_cancelled_note))
                }
            },
            confirmButton = {
                TextButton(onClick = close) { Text(stringResource(R.string.common_close), color = palette.accent) }
            }
        )
    }
}

@Composable
private fun ExportSourceAccount(account: String) {
    Text(
        stringResource(R.string.chat_export_source_account, account),
        color = beyGramPalette().textSecondary,
        fontSize = 13.sp
    )
}

@Composable
private fun ExportBlockedDialog(account: String, reason: Int, onClose: () -> Unit) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.chat_export_unavailable_title)) },
        text = {
            Column {
                ExportSourceAccount(account)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(reason))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.chat_export_blocked_note), color = palette.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_close), color = palette.accent) }
        }
    )
}

@Composable
private fun ExportProgressDialog(
    account: String,
    state: ChatExportUiState.Running,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val progress = state.progress
    val total = progress.totalMessages
    val fraction = if (progress.phase == ChatExportPhase.Messages && total != null && total > 0) {
        (progress.messagesWritten.toFloat() / total).coerceIn(0f, 1f)
    } else null
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.chat_export_title)) },
        text = {
            Column {
                ExportSourceAccount(account)
                Spacer(Modifier.height(14.dp))
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.accent
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                }
                Spacer(Modifier.height(12.dp))
                Text(ExportProgressLabel(progress, Formatter.formatShortFileSize(context, progress.bytesWritten)))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.chat_export_size_progress, Formatter.formatShortFileSize(context, progress.bytesWritten)),
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.chat_export_keep_open), color = palette.textSecondary, fontSize = 13.sp)
                if (state.cancelling) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.chat_export_cancelling), color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel, enabled = !state.cancelling) {
                Text(stringResource(R.string.chat_export_cancel), color = palette.danger)
            }
        }
    )
}

@Composable
private fun ExportProgressLabel(progress: ChatExportProgress, bytes: String): String {
    val total = progress.totalMessages
    return when (progress.phase) {
        ChatExportPhase.Preparing -> stringResource(R.string.chat_export_preparing)
        ChatExportPhase.Media -> stringResource(R.string.chat_export_media_progress, progress.mediaCopied, bytes)
        ChatExportPhase.Messages -> if (total != null && total > 0) {
            stringResource(R.string.chat_export_messages_total_progress, progress.messagesWritten, total)
        } else {
            stringResource(R.string.chat_export_messages_progress, progress.messagesWritten)
        }
        ChatExportPhase.Finishing -> stringResource(R.string.chat_export_finishing)
    }
}

@Composable
private fun ExportDateButton(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    val palette = beyGramPalette()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = palette.textSecondary, fontSize = 11.sp)
            Text(value, color = palette.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatExportDate(seconds: Long, endExclusive: Boolean, zone: ZoneId): String =
    searchBoundaryDate(seconds, endExclusive, zone).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    )

private fun suggestedExportName(title: String, format: ChatExportFormat, includeMedia: Boolean): String {
    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val extension = if (includeMedia) "zip" else format.extension
    return safeExportFileNameWithExtension("${title}_$date", "chat_export", extension)
}
