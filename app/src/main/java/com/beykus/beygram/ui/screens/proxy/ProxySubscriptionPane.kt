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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/**
 * Вкладка «Подписка» экрана «Подключение к Telegram»: ссылка, режим выбора серверов и список с пингом.
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
    var actionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vpnBackend.refreshConnections() }

    val probing = progress != null
    val bestName = bestReachable(probes)?.first

    val probeAll: () -> Unit = {
        scope.launch {
            vpnBackend.probeAllConnections()
                .onSuccess { actionMessage = "Проверено серверов: ${it.size}" }
                .onFailure { actionMessage = it.message ?: "Не удалось проверить серверы" }
        }
    }
    val applySelected: () -> Unit = {
        val name = selectedName
        if (name != null) {
            scope.launch {
                vpnBackend.selectConnection(name)
                    .onSuccess { actionMessage = "Выбрано подключение: $name" }
                    .onFailure { actionMessage = it.message }
            }
        }
    }

    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
        ProxyField(
            label = "Ссылка на подписку",
            value = url,
            onValueChange = onUrlChange,
            keyboardType = KeyboardType.Uri
        )
        error?.let {
            Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(12.dp))
        // «Применить» включает автоматический выбор сервера, «Вручную» оставляет выбор за пользователем.
        ProxySegmented(
            labels = listOf("Применить", "Вручную"),
            selectedIndex = if (settings.autoSelectEnabled) 0 else 1,
            onSelect = { index -> vpnBackend.updateAutoSelect(index == 0) }
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Серверы · ${connections.size}", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("Пинг, мс", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        if (url.isBlank()) "Вставьте ссылку на подписку и нажмите обновление вверху." else "Серверы пока не загружены. Нажмите обновление вверху.",
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
                    probe != null && !probe.telegramReachable -> "нет связи" to palette.danger
                    delay == null -> "—" to palette.textSecondary
                    else -> latencyStyle(delay, palette)
                }
                ProxyRadioRow(
                    selected = connection.name == selectedName,
                    title = connection.name,
                    subtitle = null,
                    onClick = { onSelect(connection.name) },
                    badge = if (connection.name == bestName) "лучший" else null,
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
                text = "Проверить пинг",
                icon = Icons.Rounded.Speed,
                background = palette.surface2,
                enabled = !probing && !busy && connections.isNotEmpty(),
                onClick = probeAll,
                modifier = Modifier.weight(1f)
            )
            ProxyTonalButton(
                text = "Применить",
                icon = Icons.Rounded.Check,
                background = palette.accentSoft,
                enabled = selectedName != null && !busy,
                onClick = applySelected,
                modifier = Modifier.weight(1f)
            )
        }

        (progress?.let { "Проверяем: ${it.completed} из ${it.total}" } ?: actionMessage)?.let {
            Text(it, color = palette.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        TextButton(
            onClick = {
                scope.launch {
                    actionMessage = shareConnectionDiagnosticsOrError(context, connectionState)
                        ?: "Отчёт с логами подписки готов к отправке"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Поделиться логами", color = palette.accent, fontSize = 14.sp)
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
