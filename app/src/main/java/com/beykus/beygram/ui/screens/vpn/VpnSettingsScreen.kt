package com.beykus.beygram.ui.screens.vpn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.data.vpn.AutoSelectEvent
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.data.vpn.ProxyRuntimeStatus
import com.beykus.beygram.diagnostics.copyConnectionDiagnosticsToClipboardOrError
import com.beykus.beygram.diagnostics.shareConnectionDiagnosticsOrError
import com.beykus.beygram.domain.model.MihomoConnection
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.VpnTransport
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

@Composable
internal fun VpnSettingsScreen(
    backend: VpnBackend,
    connectionState: TelegramConnectionState,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by backend.settings.collectAsStateWithLifecycle()
    val network by backend.network.collectAsStateWithLifecycle()
    val connections by backend.connections.collectAsStateWithLifecycle()
    val probes by backend.probes.collectAsStateWithLifecycle()
    val probeProgress by backend.probeProgress.collectAsStateWithLifecycle()
    val busy by backend.busy.collectAsStateWithLifecycle()
    val runtime by backend.runtimeState.collectAsStateWithLifecycle()
    val autoEvent by backend.autoSelectEvents.collectAsStateWithLifecycle()
    var subscriptionUrl by remember(settings.subscriptionUrl) { mutableStateOf(settings.subscriptionUrl) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.screen).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Назад") }
            Text("Mihomo proxy (dev)", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            "Тестовый локальный SOCKS-прокси Mihomo. Он используется только сетевыми соединениями внутри BeyGram; системный VPN не создаётся.",
            color = palette.textSecondary,
            fontSize = 13.sp
        )

        Surface(shape = RoundedCornerShape(14.dp), color = palette.surface) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Сеть", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("Активный маршрут: ${network.activeTransport.asDisplayName()}", color = palette.textSecondary, fontSize = 13.sp)
                Text("Wi-Fi: ${if (network.wifiAvailable) "доступен" else "недоступен"}", color = palette.textSecondary, fontSize = 13.sp)
                Text("Мобильная сеть: ${if (network.cellularAvailable) "доступна" else "недоступна"}", color = palette.textSecondary, fontSize = 13.sp)
                Text("Интернет: ${if (network.internetAvailable) "проверен Android" else "не подтверждён"}", color = palette.textSecondary, fontSize = 13.sp)
            }
        }

        Surface(shape = RoundedCornerShape(14.dp), color = palette.surface) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Состояние прокси Mihomo", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(runtime.status.asDisplayName(runtime.message), color = palette.textSecondary, fontSize = 13.sp)
                Button(
                    onClick = {
                        scope.launch {
                            val result = if (runtime.status == ProxyRuntimeStatus.RUNNING) backend.stopProxy() else backend.startProxy()
                            result.onSuccess { actionMessage = if (runtime.status == ProxyRuntimeStatus.RUNNING) "Прокси остановлен" else "Mihomo подключён к TDLib" }
                                .onFailure { actionMessage = it.message ?: "Ошибка управления прокси" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = runtime.status !in setOf(ProxyRuntimeStatus.STARTING, ProxyRuntimeStatus.STOPPING)
                ) {
                    Text(if (runtime.status == ProxyRuntimeStatus.RUNNING) "Остановить прокси" else "Запустить Mihomo proxy (dev)")
                }
            }
        }

        Surface(shape = RoundedCornerShape(14.dp), color = palette.surface) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Подписка Mihomo", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = subscriptionUrl,
                    onValueChange = { subscriptionUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTPS URL") },
                    placeholder = { Text("https://provider.example/sub/…") },
                    singleLine = true
                )
                Text("При запросе отправляется случайный ID установки X-BeyGram-HWID. IMEI и серийные номера не используются.", color = palette.textSecondary, fontSize = 12.sp)
                Button(
                    onClick = {
                        scope.launch {
                            actionMessage = null
                            backend.fetchSubscription(subscriptionUrl)
                                .onSuccess { actionMessage = "Подписка загружена: ${it.size} подключений" }
                                .onFailure { actionMessage = it.message ?: "Не удалось загрузить подписку" }
                        }
                    },
                    enabled = !busy && subscriptionUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Загружаем и проверяем…" else "Получить список подключений") }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Автоматический выбор (dev)", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("Если Telegram остаётся на Connecting 8 секунд, проверять серверы при работающем локальном прокси.", color = palette.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = settings.autoSelectEnabled, onCheckedChange = backend::updateAutoSelect)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                scope.launch {
                    backend.refreshConnections()
                        .onSuccess { actionMessage = "Список подключений обновлён: ${it.size}" }
                        .onFailure { actionMessage = it.message ?: "Не удалось обновить список подключений" }
                }
            }) {
                Text("Обновить список")
            }
            TextButton(onClick = {
                scope.launch {
                    backend.probeAllConnections().onSuccess {
                        val reachable = it.count { result -> result.telegramReachable }
                        actionMessage = "Telegram доступен через $reachable из ${it.size} подключений"
                    }.onFailure { actionMessage = it.message }
                }
            }, enabled = connections.isNotEmpty() && probeProgress == null) {
                Text(probeProgress?.let { "Проверка ${it.completed}/${it.total} · по 4" } ?: "Проверить Telegram через все")
            }
        }
        probeProgress?.let { progress ->
            Text("Проверка серверов партиями: ${progress.completed} из ${progress.total} (одновременно до 4)", color = palette.textSecondary, fontSize = 12.sp)
        }

        connections.forEach { connection ->
            ConnectionRow(
                connection = connection,
                probeDescription = probes[connection.name]?.let { result ->
                    if (result.telegramReachable) "Telegram доступен · ${result.delayMillis} мс" else "Telegram недоступен"
                },
                onClick = {
                    scope.launch {
                        backend.selectConnection(connection.name)
                            .onSuccess { actionMessage = "Выбрано подключение: ${connection.name}" }
                            .onFailure { actionMessage = it.message }
                    }
                }
            )
            HorizontalDivider(color = palette.divider)
        }

        actionMessage?.let { Text(it, color = palette.accent, fontSize = 13.sp) }
        if (BuildConfig.DEBUG && BuildConfig.FLAVOR == "dev") {
            TextButton(onClick = {
                scope.launch {
                    actionMessage = copyConnectionDiagnosticsToClipboardOrError(context, connectionState)
                        ?: "Логи скопированы. Вставь их сюда сообщением."
                }
            }) { Text("Скопировать логи (dev)") }
            TextButton(onClick = {
                scope.launch {
                    actionMessage = shareConnectionDiagnosticsOrError(context, connectionState)
                        ?: "Отчёт с логами подписки готов к отправке"
                }
            }) { Text("Поделиться логами подписки (dev)") }
        }
        when (val event = autoEvent) {
            is AutoSelectEvent.Switched -> Text("Автовыбор: ${event.connectionName}, ${event.delayMillis} мс", color = palette.successText, fontSize = 13.sp)
            is AutoSelectEvent.NoReachableConnection -> Text("Автовыбор: Telegram не ответил через проверенные подключения", color = palette.warningText, fontSize = 13.sp)
            is AutoSelectEvent.Failed -> Text("Автовыбор: ${event.message}", color = palette.warningText, fontSize = 13.sp)
            else -> Unit
        }
    }
}

@Composable
private fun ConnectionRow(connection: MihomoConnection, probeDescription: String?, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = connection.selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(connection.name, color = palette.textPrimary, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(connection.type, connection.delayMillis?.let { "$it мс" }, probeDescription).joinToString(" · "),
                color = palette.textSecondary,
                fontSize = 12.sp
            )
        }
        TextButton(onClick = onClick) { Text("Выбрать") }
    }
}

private fun VpnTransport.asDisplayName(): String = when (this) {
    VpnTransport.WIFI -> "Wi-Fi"
    VpnTransport.CELLULAR -> "мобильная сеть"
    VpnTransport.ETHERNET -> "Ethernet"
    VpnTransport.VPN -> "VPN Android"
    VpnTransport.OTHER -> "другая сеть"
    VpnTransport.NONE -> "нет"
}

private fun ProxyRuntimeStatus.asDisplayName(message: String?): String = when (this) {
    ProxyRuntimeStatus.STOPPED -> "Остановлен"
    ProxyRuntimeStatus.STARTING -> "Запускается…"
    ProxyRuntimeStatus.RUNNING -> "Локальный SOCKS5 активен для BeyGram"
    ProxyRuntimeStatus.STOPPING -> "Останавливается…"
    ProxyRuntimeStatus.FAILED -> "Ошибка: ${message.orEmpty()}"
}
