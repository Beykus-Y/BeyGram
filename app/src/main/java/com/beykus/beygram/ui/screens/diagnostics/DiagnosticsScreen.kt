package com.beykus.beygram.ui.screens.diagnostics

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.analytics.AnalyticsSync
import com.beykus.beygram.diagnostics.ConnectionFailureEntry
import com.beykus.beygram.diagnostics.ConnectionFailureLog
import com.beykus.beygram.diagnostics.MultiAccountDiagnostics
import com.beykus.beygram.diagnostics.connectionDiagnosticsText
import com.beykus.beygram.diagnostics.resources.ResourceSample
import com.beykus.beygram.diagnostics.resources.ResourceSampler
import com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.components.settingsIconTint
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.screens.notifications.statusLabelRes
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.screens.proxy.label
import com.beykus.beygram.ui.screens.settings.SettingsGroup
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import com.beykus.beygram.ui.screens.settings.SettingsLink
import com.beykus.beygram.ui.screens.settings.SettingsToggle
import com.beykus.beygram.playback.VideoStartBuffer
import com.beykus.beygram.playback.VideoStartBufferSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OVERVIEW_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault())

/**
 * Раздел «Диагностика». Сверху сводка только из того, что приложение уже знает или может прочитать без сети;
 * ниже действия по группам. Ручные способы («Посмотреть данные», «Поделиться логами») доступны всегда,
 * даже когда отправка отчётов не настроена или Telegram не авторизован.
 */
@Composable
fun DiagnosticsScreen(
    connectionState: TelegramConnectionState,
    repository: TelegramRepository,
    onBack: () -> Unit,
    onConnection: () -> Unit,
    onReportProblem: () -> Unit,
    onResources: () -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val scope = rememberCoroutineScope()
    var viewingData by remember { mutableStateOf<String?>(null) }
    var viewLoading by remember { mutableStateOf(false) }
    var viewError by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<Reading<TelegramRoute>>(Reading.Checking) }
    var memory by remember { mutableStateOf<Reading<ResourceSample>>(Reading.Checking) }
    // Errors recorded by this process so far; the overview reads them when the screen opens.
    val failures = remember { ConnectionFailureLog.shared.snapshot() }
    BackHandler(onBack = onBack)

    LaunchedEffect(repository) {
        route = repository.currentRoute().getOrNull()?.let { Reading.Known(it) } ?: Reading.Unknown
    }
    LaunchedEffect(Unit) {
        memory = try {
            Reading.Known(withContext(Dispatchers.Default) { ResourceSampler().sample() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Reading.Unknown
        }
    }

    Column(
        Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        ProfileToolbar(stringResource(R.string.diagnostics_title), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            DiagnosticsOverview(connectionState = connectionState, route = route, failures = failures, memory = memory)

            SettingsGroup(stringResource(R.string.diag_network)) {
                DiagnosticsLink(
                    icon = Icons.Rounded.Link,
                    title = stringResource(R.string.notif_diagnostics_title),
                    hint = stringResource(R.string.diag_connection_link_hint),
                    onClick = onConnection
                )
            }
            SettingsGroup(stringResource(R.string.diag_group_resources)) {
                DiagnosticsLink(
                    icon = Icons.Rounded.Speed,
                    title = stringResource(R.string.diagnostics_resources),
                    hint = stringResource(R.string.diagnostics_resources_hint),
                    onClick = onResources
                )
            }
            SettingsGroup(stringResource(R.string.diag_group_report)) {
                DiagnosticsLink(
                    icon = Icons.Rounded.BugReport,
                    title = stringResource(R.string.diagnostics_report_problem),
                    hint = stringResource(R.string.diagnostics_report_problem_hint),
                    onClick = onReportProblem
                )
            }
            SettingsGroup(stringResource(R.string.diag_group_tools)) {
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
                SettingsRowDivider()
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
                            shareError = shareConnectionDiagnosticsOrError(context, connectionState)
                        } finally {
                            sharing = false
                        }
                    }
                }
            }
            if (viewError) {
                Text(stringResource(R.string.diagnostics_view_failed), color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
            }
            shareError?.let { Text(it, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp)) }

            SettingsGroup(stringResource(R.string.diag_group_settings)) {
                AnalyticsToggle()
                SettingsRowDivider()
                MultiAccountTraceToggle()
                SettingsRowDivider()
                VideoStartBufferRow()
            }
            Text(
                stringResource(R.string.diagnostics_settings_note),
                color = palette.textSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }

    viewingData?.let { text ->
        BeyGramDialog(
            onDismissRequest = { viewingData = null },
            title = stringResource(R.string.report_preview_title),
            content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text, fontSize = 12.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(8.dp))
                }
            },
            scrollableContent = false,
            confirmText = stringResource(R.string.diagnostics_view_close),
            onConfirm = { viewingData = null }
        )
    }
}

/** Читается ли показатель: ещё проверяется, не удалось прочитать или известен. */
private sealed interface Reading<out T> {
    data object Checking : Reading<Nothing>
    data object Unknown : Reading<Nothing>
    data class Known<out T>(val value: T) : Reading<T>
}

private enum class OverviewLevel { Ok, Attention, Unknown }

/**
 * Сводка вверху. «Всё в порядке» только когда состояние Telegram подтверждено, маршрут известен и в этом запуске
 * ошибок не было. Всё остальное, включая неизвестное, не выглядит как успех.
 */
@Composable
private fun DiagnosticsOverview(
    connectionState: TelegramConnectionState,
    route: Reading<TelegramRoute>,
    failures: List<ConnectionFailureEntry>,
    memory: Reading<ResourceSample>
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val context = LocalContext.current
    val level = when {
        connectionState == TelegramConnectionState.WaitingForNetwork || failures.isNotEmpty() -> OverviewLevel.Attention
        connectionState == TelegramConnectionState.Connected && route is Reading.Known -> OverviewLevel.Ok
        else -> OverviewLevel.Unknown
    }
    val headline = when (level) {
        OverviewLevel.Ok -> stringResource(R.string.diag_overview_ok)
        OverviewLevel.Attention -> stringResource(R.string.diag_overview_attention)
        OverviewLevel.Unknown -> stringResource(R.string.diag_overview_unknown)
    }
    val headlineIcon = when (level) {
        OverviewLevel.Ok -> Icons.Rounded.CheckCircle
        OverviewLevel.Attention -> Icons.Rounded.WarningAmber
        OverviewLevel.Unknown -> Icons.Rounded.RadioButtonUnchecked
    }
    val headlineTint = when (level) {
        OverviewLevel.Ok -> palette.successText
        OverviewLevel.Attention -> palette.warningText
        OverviewLevel.Unknown -> palette.textSecondary
    }
    val telegramIcon = when (connectionState) {
        TelegramConnectionState.Connected -> Icons.Rounded.CheckCircle
        TelegramConnectionState.WaitingForNetwork -> Icons.Rounded.WarningAmber
        TelegramConnectionState.Connecting, TelegramConnectionState.Updating -> Icons.Rounded.Schedule
        TelegramConnectionState.Unknown -> Icons.Rounded.RadioButtonUnchecked
    }
    val telegramTint = when (connectionState) {
        TelegramConnectionState.Connected -> palette.successText
        TelegramConnectionState.WaitingForNetwork -> palette.warningText
        else -> palette.textSecondary
    }
    val routeValue = when (val current = route) {
        Reading.Checking -> stringResource(R.string.diag_checking)
        Reading.Unknown -> stringResource(R.string.diag_route_unknown)
        is Reading.Known -> routeLabel(current.value)
    }
    val latestFailure = failures.firstOrNull()
    val errorsValue = if (latestFailure == null) {
        stringResource(R.string.diag_overview_errors_none)
    } else {
        stringResource(
            R.string.diag_overview_errors_count,
            failures.size,
            OVERVIEW_TIME.format(Instant.ofEpochMilli(latestFailure.atMillis))
        )
    }
    val errorsIcon = if (latestFailure == null) null else Icons.Rounded.WarningAmber
    val memoryValue = when (val current = memory) {
        Reading.Checking -> stringResource(R.string.diag_checking)
        Reading.Unknown -> stringResource(R.string.resources_unavailable)
        is Reading.Known -> stringResource(
            R.string.diag_overview_memory,
            Formatter.formatShortFileSize(context, current.value.javaHeapUsedBytes),
            Formatter.formatShortFileSize(context, current.value.nativeHeapBytes)
        )
    }
    val memoryIcon = when (memory) {
        Reading.Checking -> Icons.Rounded.Schedule
        Reading.Unknown -> Icons.Rounded.RadioButtonUnchecked
        is Reading.Known -> null
    }

    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = 14.dp)
            .glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(headlineIcon, null, tint = headlineTint, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    headline,
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                if (level == OverviewLevel.Unknown) {
                    Text(
                        stringResource(R.string.diag_overview_unknown_note),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OverviewRow(
            label = stringResource(R.string.diag_overview_telegram),
            value = stringResource(connectionState.statusLabelRes()),
            icon = telegramIcon,
            tint = telegramTint
        )
        HorizontalDivider(color = palette.divider)
        OverviewRow(label = stringResource(R.string.diag_route), value = routeValue, icon = null, tint = palette.textSecondary)
        HorizontalDivider(color = palette.divider)
        OverviewRow(
            label = stringResource(R.string.diag_overview_errors),
            value = errorsValue,
            icon = errorsIcon,
            tint = palette.warningText
        )
        HorizontalDivider(color = palette.divider)
        OverviewRow(label = stringResource(R.string.resources_heap), value = memoryValue, icon = memoryIcon, tint = palette.textSecondary)
    }
}

/** Строка сводки: значок состояния (если он нужен), название и значение. Значок не даёт цвету быть единственным признаком. */
@Composable
private fun OverviewRow(label: String, value: String, icon: ImageVector?, tint: Color) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.size(18.dp))
        }
        Text(label, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(value, color = palette.textSecondary, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun routeLabel(route: TelegramRoute): String = when (route) {
    TelegramRoute.Direct -> stringResource(R.string.diag_route_direct)
    is TelegramRoute.Proxy -> stringResource(R.string.diag_route_proxy, route.kind.label)
    TelegramRoute.Subscription -> stringResource(R.string.diag_route_subscription)
}

@Composable
private fun AnalyticsToggle() {
    var enabled by remember { mutableStateOf(AnalyticsSync.isEnabled()) }
    SettingsToggle(
        icon = Icons.Rounded.Poll,
        title = stringResource(R.string.analytics_title),
        // Turning it off also deletes this installation's record on the server.
        subtitle = stringResource(R.string.analytics_description),
        checked = enabled,
        onCheckedChange = { enabled = it; AnalyticsSync.setEnabled(it) }
    )
}

/**
 * Выбор стартового буфера для замеров (Issue #1121). Каждое нажатие переключает вариант; новый вариант
 * применяется к следующему открытому видео. Значение по умолчанию не меняется, пока замеры не закрыты.
 */
@Composable
private fun VideoStartBufferRow() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(VideoStartBufferSettings.load(context)) }
    val value = when (mode) {
        VideoStartBuffer.DEFAULT -> stringResource(R.string.diag_video_buffer_value_default)
        else -> stringResource(R.string.diag_video_buffer_value_ms, mode.bufferForPlaybackMs ?: 0)
    }
    SettingsLink(
        icon = Icons.Rounded.History,
        title = stringResource(R.string.diag_video_buffer_title),
        subtitle = value,
        onClick = {
            val all = VideoStartBuffer.entries
            val next = all[(all.indexOf(mode) + 1) % all.size]
            mode = next
            VideoStartBufferSettings.save(context, next)
        }
    )
}

@Composable
private fun MultiAccountTraceToggle() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(MultiAccountDiagnostics.isVerbose()) }
    SettingsToggle(
        icon = Icons.Rounded.History,
        title = stringResource(R.string.diagnostics_account_trace_title),
        subtitle = stringResource(R.string.diagnostics_account_trace_description),
        checked = enabled,
        onCheckedChange = {
            enabled = it
            MultiAccountDiagnostics.setVerbose(context, it)
        }
    )
}

/** Строка группы: значок, название и пояснение. Вся строка нажимается, как строки настроек. */
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
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = settingsIconTint(), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = if (enabled) palette.textPrimary else palette.textSecondary, fontSize = 16.sp)
            Text(hint, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}
