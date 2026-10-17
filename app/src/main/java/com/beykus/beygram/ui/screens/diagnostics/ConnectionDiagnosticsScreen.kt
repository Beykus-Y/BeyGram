package com.beykus.beygram.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.diagnostics.ConnectionCheck
import com.beykus.beygram.diagnostics.ConnectionFailure
import com.beykus.beygram.diagnostics.ConnectionFailureEntry
import com.beykus.beygram.diagnostics.ConnectionFailureLog
import com.beykus.beygram.diagnostics.LatencyCheck
import com.beykus.beygram.diagnostics.NetworkKind
import com.beykus.beygram.diagnostics.runConnectionCheck
import com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.notifications.statusLabelRes
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.screens.proxy.ProxyCard
import com.beykus.beygram.ui.screens.proxy.ProxyPrimaryButton
import com.beykus.beygram.ui.screens.proxy.ProxySectionLabel
import com.beykus.beygram.ui.screens.proxy.label
import com.beykus.beygram.ui.screens.proxy.latencyStyle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FAILURE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault())

/**
 * Экран «Диагностика подключения» из настроек уведомлений. Проверки запускаются при открытии
 * и по кнопке «Проверить снова». Макет: BeyGram Scenarios 2, раздел 5.
 */
@Composable
fun ConnectionDiagnosticsScreen(
    repository: TelegramRepository,
    vpnBackend: VpnBackend,
    connectionState: TelegramConnectionState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var check by remember { mutableStateOf<ConnectionCheck?>(null) }
    var checking by remember { mutableStateOf(false) }
    var failures by remember { mutableStateOf<List<ConnectionFailureEntry>>(emptyList()) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    val runChecks: () -> Unit = {
        scope.launch {
            checking = true
            try {
                check = runConnectionCheck(context, repository, vpnBackend)
                failures = ConnectionFailureLog.shared.snapshot()
            } finally {
                checking = false
            }
        }
    }
    LaunchedEffect(Unit) { runChecks() }

    val copy: (String) -> Unit = { text ->
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("BeyGram", text))
        Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }
    val share: () -> Unit = {
        scope.launch {
            sharing = true
            shareError = null
            try { shareError = shareConnectionDiagnosticsOrError(context, connectionState) }
            finally { sharing = false }
        }
    }

    val current = check
    val address = current?.address?.getOrNull()
    val network = current?.network
    val route = current?.route?.getOrNull()
    val checkingText = stringResource(R.string.diag_checking)
    val unknownText = stringResource(R.string.diag_address_failed)

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.notif_diagnostics_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ProxySectionLabel(stringResource(R.string.diag_section_address))
            ProxyCard {
                InfoRow(
                    title = stringResource(R.string.diag_ip),
                    value = when {
                        current == null -> checkingText
                        address == null -> unknownText
                        else -> address.ip
                    },
                    note = stringResource(R.string.diag_address_note),
                    copyText = address?.let { "${it.ip} (${it.country.ifBlank { stringResource(R.string.diag_unknown) }})" },
                    onCopy = copy
                )
                HorizontalDivider(color = palette.divider)
                InfoRow(
                    title = stringResource(R.string.diag_country),
                    value = when {
                        current == null -> checkingText
                        address == null -> unknownText
                        address.country.isBlank() -> stringResource(R.string.diag_unknown)
                        else -> address.country
                    }
                )
            }

            ProxySectionLabel(stringResource(R.string.diag_section_network))
            ProxyCard {
                InfoRow(
                    title = stringResource(R.string.diag_vpn),
                    value = when {
                        network == null -> checkingText
                        network.vpnActive -> stringResource(R.string.diag_vpn_on)
                        else -> stringResource(R.string.diag_vpn_off)
                    }
                )
                HorizontalDivider(color = palette.divider)
                InfoRow(
                    title = stringResource(R.string.diag_network),
                    value = when (network?.kind) {
                        null -> checkingText
                        NetworkKind.Wifi -> stringResource(R.string.diag_network_wifi)
                        NetworkKind.Mobile -> stringResource(R.string.diag_network_mobile)
                        NetworkKind.Other -> stringResource(R.string.diag_network_other)
                        NetworkKind.None -> stringResource(R.string.diag_network_none)
                    }
                )
            }

            ProxySectionLabel(stringResource(R.string.diag_section_proxy))
            ProxyCard {
                InfoRow(
                    title = stringResource(R.string.diag_proxy),
                    value = when {
                        current == null -> checkingText
                        route == null -> stringResource(R.string.diag_route_unknown)
                        route == TelegramRoute.Direct -> stringResource(R.string.diag_proxy_none)
                        route is TelegramRoute.Proxy -> route.kind.label
                        else -> stringResource(R.string.diag_proxy_subscription)
                    }
                )
            }

            ProxySectionLabel(stringResource(R.string.diag_section_telegram))
            ProxyCard {
                InfoRow(
                    title = stringResource(R.string.diag_state),
                    value = stringResource(connectionState.statusLabelRes())
                )
                HorizontalDivider(color = palette.divider)
                InfoRow(
                    title = stringResource(R.string.diag_route),
                    value = when {
                        current == null -> checkingText
                        route == null -> stringResource(R.string.diag_route_unknown)
                        route == TelegramRoute.Direct -> stringResource(R.string.diag_route_direct)
                        route is TelegramRoute.Proxy -> stringResource(R.string.diag_route_proxy, route.kind.label)
                        else -> stringResource(R.string.diag_route_subscription)
                    }
                )
                HorizontalDivider(color = palette.divider)
                val (latencyText, latencyColor) = latencyLabel(current?.latency)
                InfoRow(
                    title = stringResource(R.string.diag_latency),
                    value = latencyText,
                    valueColor = latencyColor,
                    note = when (route) {
                        null -> null
                        TelegramRoute.Direct -> stringResource(R.string.diag_latency_note_direct)
                        is TelegramRoute.Proxy -> stringResource(R.string.diag_latency_note_proxy)
                        TelegramRoute.Subscription -> stringResource(R.string.diag_latency_note_subscription)
                    }
                )
            }

            ProxySectionLabel(stringResource(R.string.diag_section_errors))
            ProxyCard {
                if (failures.isEmpty()) {
                    Text(
                        stringResource(R.string.diag_errors_empty),
                        color = palette.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                failures.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(color = palette.divider)
                    InfoRow(
                        title = stringResource(entry.failure.labelRes()),
                        value = FAILURE_TIME.format(Instant.ofEpochMilli(entry.atMillis))
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProxyPrimaryButton(
                    text = stringResource(if (checking) R.string.diag_checking else R.string.diag_check_again),
                    onClick = runChecks,
                    enabled = !checking
                )
                OutlinedButton(
                    onClick = share,
                    enabled = !sharing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(if (sharing) R.string.auth_share_logs_progress else R.string.diag_share)
                    )
                }
                Text(stringResource(R.string.notif_diagnostics_note), color = palette.textSecondary, fontSize = 13.sp)
                shareError?.let { Text(it, color = palette.danger) }
            }
        }
    }
}

/** Строка «название — значение», необязательная подсказка под ней и кнопка копирования. */
@Composable
private fun InfoRow(
    title: String,
    value: String,
    valueColor: Color? = null,
    note: String? = null,
    copyText: String? = null,
    onCopy: (String) -> Unit = {}
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(value, color = valueColor ?: palette.textSecondary, fontSize = 15.sp, textAlign = TextAlign.End)
            copyText?.let { text ->
                TextButton(onClick = { onCopy(text) }) { Text(stringResource(R.string.diag_copy), fontWeight = FontWeight.SemiBold) }
            }
        }
        note?.let { Text(it, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun latencyLabel(latency: LatencyCheck?): Pair<String, Color> {
    val palette = beyGramPalette()
    return when (latency) {
        null -> stringResource(R.string.diag_checking) to palette.textSecondary
        is LatencyCheck.Measured -> latencyStyle(latency.millis, palette)
        LatencyCheck.NoAnswer -> stringResource(R.string.diag_latency_no_answer) to palette.danger
        LatencyCheck.NotMeasured -> stringResource(R.string.diag_latency_not_measured) to palette.textSecondary
    }
}

@StringRes
private fun ConnectionFailure.labelRes(): Int = when (this) {
    ConnectionFailure.Timeout -> R.string.diag_failure_timeout
    ConnectionFailure.Proxy -> R.string.diag_failure_proxy
    ConnectionFailure.FloodWait -> R.string.diag_failure_flood_wait
}
