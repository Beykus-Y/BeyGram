package com.beykus.beygram.ui.screens.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.analytics.AnalyticsSync
import com.beykus.beygram.diagnostics.connectionDiagnosticsText
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.launch

/**
 * Раздел «Диагностика»: вход в отправку отчёта и ручной экспорт.
 * Ручные способы («Посмотреть данные», «Поделиться логами») всегда доступны,
 * даже когда отправка отчётов на сервер не настроена или Telegram не авторизован.
 */
@Composable
fun DiagnosticsScreen(
    connectionState: TelegramConnectionState,
    onBack: () -> Unit,
    onReportProblem: () -> Unit,
    onResources: () -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var viewingData by remember { mutableStateOf<String?>(null) }
    var viewLoading by remember { mutableStateOf(false) }
    var viewError by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        ProfileToolbar(stringResource(R.string.diagnostics_title), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)) {
                AnalyticsSwitchRow()
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
                DiagnosticsLink(
                    icon = Icons.Rounded.BugReport,
                    title = stringResource(R.string.diagnostics_report_problem),
                    hint = stringResource(R.string.diagnostics_report_problem_hint),
                    onClick = onReportProblem
                )
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
                DiagnosticsLink(
                    icon = Icons.Rounded.Speed,
                    title = stringResource(R.string.diagnostics_resources),
                    hint = stringResource(R.string.diagnostics_resources_hint),
                    onClick = onResources
                )
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
                DiagnosticsLink(
                    icon = Icons.Rounded.Description,
                    title = stringResource(R.string.diagnostics_view_data),
                    hint = stringResource(R.string.diagnostics_view_data_hint),
                    enabled = !viewLoading && !sharing
                ) {
                    scope.launch {
                        viewLoading = true
                        viewError = false
                        try {
                            viewingData = connectionDiagnosticsText(context, connectionState)
                        } catch (_: Exception) {
                            viewError = true
                        } finally {
                            viewLoading = false
                        }
                    }
                }
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
                DiagnosticsLink(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.diagnostics_share_logs),
                    hint = stringResource(R.string.diagnostics_share_logs_hint),
                    enabled = !viewLoading && !sharing
                ) {
                    scope.launch {
                        sharing = true
                        shareError = null
                        try {
                            shareError = com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError(context, connectionState)
                        } finally {
                            sharing = false
                        }
                    }
                }
            }
            shareError?.let { Text(it, color = palette.danger, fontSize = 14.sp) }
            if (viewError) Text(stringResource(R.string.diagnostics_view_failed), color = palette.danger, fontSize = 14.sp)
        }
    }

    viewingData?.let { text ->
        AlertDialog(
            onDismissRequest = { viewingData = null },
            title = { Text(stringResource(R.string.report_preview_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text, fontSize = 12.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingData = null }) { Text(stringResource(R.string.diagnostics_view_close)) }
            }
        )
    }
}

@Composable
private fun AnalyticsSwitchRow() {
    val palette = beyGramPalette()
    var enabled by remember { mutableStateOf(AnalyticsSync.isEnabled()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.analytics_title), color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            // Turning it off also deletes this installation's record on the server.
            Switch(checked = enabled, onCheckedChange = { enabled = it; AnalyticsSync.setEnabled(it) })
        }
        Text(stringResource(R.string.analytics_description), color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DiagnosticsLink(
    icon: ImageVector,
    title: String,
    hint: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp)
            Text(hint, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}
