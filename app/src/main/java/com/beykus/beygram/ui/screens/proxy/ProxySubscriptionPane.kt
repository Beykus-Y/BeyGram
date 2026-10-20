package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.data.vpn.AutoSelectEvent
import com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/**
 * Вкладка «Подписка» экрана «Подключение к Telegram»: ссылка, режим выбора серверов и список с задержкой.
 * Выбор хранится в [selectedName] экрана; «Подключить» на экране запускает прокси с выбранным сервером.
 */
@Composable
internal fun ColumnScope.ProxySubscriptionPane(
    vpnBackend: VpnBackend,
    connectionState: TelegramConnectionState,
    url: String,
    onUrlChange: (String) -> Unit,
    error: String?,
    selectedName: String?,
    onSelect: (String) -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by vpnBackend.settings.collectAsStateWithLifecycle()
    val connections by vpnBackend.connections.collectAsStateWithLifecycle()
    val probes by vpnBackend.probes.collectAsStateWithLifecycle()
    val progress by vpnBackend.probeProgress.collectAsStateWithLifecycle()
    val busy by vpnBackend.busy.collectAsStateWithLifecycle()
    val autoEvent by vpnBackend.autoSelectEvents.collectAsStateWithLifecycle()
    var actionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vpnBackend.refreshConnections() }

    val probing = progress != null
    val bestName = bestReachable(probes)?.first

    val probeAll: () -> Unit = {
        scope.launch {
            vpnBackend.probeAllConnections()
                .onSuccess { actionMessage = context.getString(R.string.proxy_sub_probed_count, it.size) }
                .onFailure { actionMessage = it.userMessage(context, R.string.proxy_sub_probe_failed) }
        }
    }
    val applySelected: () -> Unit = {
        val name = selectedName
        if (name != null) {
            scope.launch {
                vpnBackend.selectConnection(name)
                    .onSuccess { actionMessage = context.getString(R.string.proxy_sub_selected_connection, name) }
                    .onFailure { actionMessage = it.userMessage(context, R.string.err_vpn_server_select_failed) }
            }
        }
    }

    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
        ProxyField(
            label = stringResource(R.string.proxy_sub_link_label),
            value = url,
            onValueChange = onUrlChange,
            keyboardType = KeyboardType.Uri
        )
        error?.let {
            Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(12.dp))
        // «Автоматически» включает автоматический выбор сервера, «Вручную» оставляет выбор за пользователем.
        ProxySegmented(
            labels = listOf(stringResource(R.string.proxy_sub_auto), stringResource(R.string.proxy_sub_manual)),
            selectedIndex = if (settings.autoSelectEnabled) 0 else 1,
            onSelect = { index -> if (!busy) vpnBackend.updateAutoSelect(index == 0) }
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.proxy_sub_servers_count, connections.size), color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.proxy_sub_latency_header), color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
        ) {
            if (connections.isEmpty()) {
                item {
                    Text(
                        if (url.isBlank()) stringResource(R.string.proxy_sub_paste_link_hint) else stringResource(R.string.proxy_sub_servers_not_loaded_hint),
                        color = palette.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            itemsIndexed(connections) { index, connection ->
                if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 50.dp))
                val probe = probes[connection.name]
                val delay = probe?.delayMillis ?: connection.delayMillis
                val (pingText, pingColor) = when {
                    probe != null && !probe.telegramReachable -> stringResource(R.string.proxy_no_connection) to palette.danger
                    delay == null -> "—" to palette.textSecondary
                    else -> latencyStyle(delay, palette)
                }
                ProxyRadioRow(
                    selected = connection.name == selectedName,
                    title = connection.name,
                    subtitle = null,
                    onClick = { onSelect(connection.name) },
                    badge = if (connection.name == bestName) stringResource(R.string.proxy_sub_best_badge) else null,
                    trailing = {
                        Text(
                            pingText,
                            color = pingColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProxyTonalButton(
                text = stringResource(R.string.proxy_sub_check_latency),
                icon = Icons.Rounded.Speed,
                background = palette.surface2,
                enabled = !probing && !busy && connections.isNotEmpty(),
                onClick = probeAll,
                modifier = Modifier.weight(1f)
            )
            ProxyTonalButton(
                text = stringResource(R.string.proxy_sub_select_server),
                icon = Icons.Rounded.Check,
                background = palette.accentSoft,
                enabled = selectedName != null && !busy,
                onClick = applySelected,
                modifier = Modifier.weight(1f)
            )
        }

        (autoEvent as? AutoSelectEvent.Failed)?.let { event ->
            Text(
                event.message.resolve(),
                color = palette.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        (progress?.let { stringResource(R.string.proxy_sub_probing_progress, it.completed, it.total) } ?: actionMessage)?.let {
            Text(it, color = palette.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        TextButton(
            onClick = {
                scope.launch {
                    actionMessage = shareConnectionDiagnosticsOrError(context, connectionState)
                        ?: context.getString(R.string.proxy_sub_logs_ready)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.proxy_sub_share_logs), color = palette.accent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProxyTonalButton(
    text: String,
    icon: ImageVector,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val tint = if (enabled) palette.accentInk else palette.textSecondary
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
