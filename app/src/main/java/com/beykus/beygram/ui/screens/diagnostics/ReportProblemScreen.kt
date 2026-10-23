package com.beykus.beygram.ui.screens.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.report.BugReportPayload
import com.beykus.beygram.diagnostics.report.ReportFailureReason
import com.beykus.beygram.diagnostics.report.ReportFlowState
import com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.launch

/**
 * Форма отчёта о проблеме: описание, точный состав данных, отправка и статус.
 * Ничего не отправляется до явного нажатия «Отправить отчёт».
 */
@Composable
fun ReportProblemScreen(
    repository: TelegramRepository,
    connectionState: TelegramConnectionState,
    onBack: () -> Unit,
    accountId: String = "legacy"
) {
    val context = LocalContext.current
    val viewModel: ReportProblemViewModel = viewModel(key = "report-problem:$accountId", factory = viewModelFactory {
        initializer { ReportProblemViewModel(repository, context.applicationContext) }
    })
    val coordinator = viewModel.coordinator
    val state by coordinator.state.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var showPreview by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var manualShareError by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(coordinator) {
        coordinator.onScreenResumed()
        onPauseOrDispose { }
    }
    // Экран покидается только явным «назад»; активная операция продолжается, статус сохраняется.
    BackHandler { onBack() }

    Column(
        Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileToolbar(stringResource(R.string.report_title), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val current = state) {
                is ReportFlowState.Draft -> DraftForm(
                    state = current,
                    onDescriptionChange = coordinator::onDescriptionChange,
                    onIncludeDiagnosticsChange = coordinator::onIncludeDiagnosticsChange,
                    onPreview = {
                        scope.launch {
                            previewText = coordinator.previewText()
                            if (previewText != null) showPreview = true
                        }
                    },
                    onSubmit = coordinator::submit,
                    botUsername = viewModel.config.botUsername
                )
                is ReportFlowState.Preparing -> InProgress(
                    text = stringResource(R.string.report_state_preparing),
                    onCancel = coordinator::cancel
                )
                is ReportFlowState.AwaitingTelegram -> InProgress(
                    text = stringResource(R.string.report_state_awaiting_telegram, current.botUsername),
                    onCancel = coordinator::cancel
                )
                is ReportFlowState.AwaitingBotConfirmation -> InProgress(
                    text = stringResource(R.string.report_state_awaiting_bot, current.botUsername),
                    detail = stringResource(
                        R.string.report_state_awaiting_bot_timer, current.elapsedSeconds, current.timeoutSeconds
                    ),
                    onCancel = coordinator::cancel
                )
                is ReportFlowState.Uploading -> InProgress(
                    text = stringResource(R.string.report_state_uploading),
                    detail = null,
                    onCancel = null
                )
                is ReportFlowState.Queued -> QueuedView(
                    state = current,
                    onRefresh = coordinator::pollOnce,
                    onShareManually = {
                        scope.launch {
                            manualShareError = shareConnectionDiagnosticsOrError(context, connectionState)
                        }
                    }
                )
                is ReportFlowState.Delivered -> {
                    StatusHeader(text = stringResource(R.string.report_state_delivered, current.reportId))
                    TextButton(onClick = coordinator::startNewReport) {
                        Text(stringResource(R.string.report_new_report))
                    }
                }
                is ReportFlowState.DeliveryFailed -> {
                    StatusHeader(text = stringResource(R.string.report_state_delivery_failed, current.reportId))
                    if (current.detail.isNotBlank()) {
                        Text(current.detail, color = palette.textSecondary, fontSize = 14.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManualShareButton {
                            scope.launch {
                                manualShareError = shareConnectionDiagnosticsOrError(context, connectionState)
                            }
                        }
                        TextButton(onClick = coordinator::startNewReport) {
                            Text(stringResource(R.string.report_new_report))
                        }
                    }
                }
                is ReportFlowState.Failed -> FailedView(
                    state = current,
                    onRetry = coordinator::retry,
                    onShareManually = {
                        scope.launch {
                            manualShareError = shareConnectionDiagnosticsOrError(context, connectionState)
                        }
                    },
                    onNewReport = coordinator::startNewReport
                )
            }
            manualShareError?.let { Text(it, color = palette.danger, fontSize = 14.sp) }
        }
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text(stringResource(R.string.report_preview_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.report_preview_note), color = palette.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(previewText.orEmpty(), fontSize = 12.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreview = false }) { Text(stringResource(R.string.diagnostics_view_close)) }
            }
        )
    }
}

@Composable
private fun DraftForm(
    state: ReportFlowState.Draft,
    onDescriptionChange: (String) -> Unit,
    onIncludeDiagnosticsChange: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onSubmit: () -> Unit,
    botUsername: String
) {
    val palette = beyGramPalette()
    OutlinedTextField(
        value = state.description,
        onValueChange = onDescriptionChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.report_description_label)) },
        placeholder = { Text(stringResource(R.string.report_description_placeholder)) },
        isError = state.descriptionError,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        supportingText = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (state.descriptionError) stringResource(R.string.report_description_error)
                    else stringResource(R.string.report_include_diagnostics_hint),
                    color = if (state.descriptionError) palette.danger else palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.height(0.dp))
                Text(
                    stringResource(R.string.report_description_counter, state.description.length, BugReportPayload.MAX_DESCRIPTION_CHARS),
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }
        },
        minLines = 3
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.report_include_diagnostics), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.report_include_diagnostics_hint), color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Switch(checked = state.includeDiagnostics, onCheckedChange = onIncludeDiagnosticsChange)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(16.dp)) {
        Text(stringResource(R.string.report_attached_title), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        AttachedItem(stringResource(R.string.report_attached_description))
        AttachedItem(stringResource(R.string.report_attached_version))
        AttachedItem(stringResource(R.string.report_attached_device))
        AttachedItem(stringResource(R.string.report_attached_connection))
        if (state.includeDiagnostics) {
            AttachedItem(stringResource(R.string.report_attached_events, state.eventCount))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onPreview, enabled = state.description.isNotBlank()) {
            Text(stringResource(R.string.report_preview))
        }
        if (state.description.isBlank()) {
            Text(stringResource(R.string.report_preview_unavailable), color = palette.textSecondary, fontSize = 12.sp)
        }
    }
    if (!state.configReady) {
        Text(stringResource(R.string.report_config_missing), color = palette.warningText, fontSize = 14.sp)
    }
    if (!state.telegramReady) {
        Text(stringResource(R.string.report_telegram_unavailable), color = palette.warningText, fontSize = 14.sp)
    }
    Button(
        onClick = onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(stringResource(R.string.report_submit), fontSize = 16.sp)
    }
    if (state.configReady && state.telegramReady) {
        Text(
            stringResource(R.string.report_submit_hint, botUsername),
            color = palette.textSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun AttachedItem(text: String) {
    val palette = beyGramPalette()
    Text("• $text", color = palette.textSecondary, fontSize = 14.sp, lineHeight = 18.sp)
}

@Composable
private fun InProgress(text: String, detail: String? = null, onCancel: (() -> Unit)?) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, color = palette.textPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
        detail?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        if (onCancel != null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.report_cancel_hint), color = palette.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.report_cancel))
            }
        }
    }
}

@Composable
private fun StatusHeader(text: String) {
    val palette = beyGramPalette()
    Text(text, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun QueuedView(
    state: ReportFlowState.Queued,
    onRefresh: () -> Unit,
    onShareManually: () -> Unit
) {
    val palette = beyGramPalette()
    StatusHeader(text = stringResource(R.string.report_state_queued, state.reportId))
    Text(stringResource(R.string.report_state_queued_detail), color = palette.textSecondary, fontSize = 14.sp)
    if (state.attempts > 0) {
        Text(stringResource(R.string.report_state_queued_attempts, state.attempts), color = palette.textSecondary, fontSize = 14.sp)
    }
    if (state.detail.isNotBlank()) {
        Text(state.detail, color = palette.textSecondary, fontSize = 13.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.report_refresh_status)) }
        OutlinedButton(onClick = onShareManually) { Text(stringResource(R.string.report_share_manually)) }
    }
}

@Composable
private fun ManualShareButton(onShare: () -> Unit) {
    OutlinedButton(onClick = onShare) { Text(stringResource(R.string.report_share_manually)) }
}

@Composable
private fun FailedView(
    state: ReportFlowState.Failed,
    onRetry: () -> Unit,
    onShareManually: () -> Unit,
    onNewReport: () -> Unit
) {
    val palette = beyGramPalette()
    Text(failureText(state.reason, state.retryAfterSeconds), color = palette.danger, fontSize = 16.sp, lineHeight = 21.sp)
    state.serverMessage?.takeIf { it.isNotBlank() }?.let {
        Text(stringResource(R.string.report_error_server_detail, it), color = palette.textSecondary, fontSize = 13.sp)
    }
    state.reportId?.let {
        Text(stringResource(R.string.report_state_queued, it), color = palette.textSecondary, fontSize = 13.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onRetry,
            enabled = !state.reason.isTerminal,
            shape = RoundedCornerShape(16.dp)
        ) { Text(stringResource(R.string.report_retry)) }
        OutlinedButton(onClick = onShareManually) { Text(stringResource(R.string.report_share_manually)) }
    }
    TextButton(onClick = onNewReport) { Text(stringResource(R.string.report_new_report)) }
}

@Composable
private fun failureText(reason: ReportFailureReason, retryAfterSeconds: Int?): String = when (reason) {
    ReportFailureReason.TELEGRAM_UNAVAILABLE -> stringResource(R.string.report_error_telegram_unavailable)
    ReportFailureReason.BOT_UNAVAILABLE_OR_BLOCKED -> stringResource(R.string.report_error_bot_unavailable)
    ReportFailureReason.NOT_ALLOWED -> stringResource(R.string.report_error_not_allowed)
    ReportFailureReason.CONFIRMATION_TIMEOUT -> stringResource(R.string.report_error_confirmation_timeout)
    ReportFailureReason.CONFIRMATION_REJECTED -> stringResource(R.string.report_error_confirmation_rejected)
    ReportFailureReason.NONCE_EXPIRED -> stringResource(R.string.report_error_nonce_expired)
    ReportFailureReason.RATE_LIMITED ->
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            stringResource(R.string.report_error_rate_limited, retryAfterSeconds)
        } else {
            stringResource(R.string.report_error_rate_limited_plain)
        }
    ReportFailureReason.INVALID_PAYLOAD -> stringResource(R.string.report_error_invalid_payload)
    ReportFailureReason.CONFLICT -> stringResource(R.string.report_error_conflict)
    ReportFailureReason.API_UNAVAILABLE -> stringResource(R.string.report_error_api_unavailable)
    ReportFailureReason.PAYLOAD_TOO_LARGE -> stringResource(R.string.report_error_payload_too_large)
    ReportFailureReason.FAILED_DELIVERY -> stringResource(R.string.report_error_delivery_failed)
}
