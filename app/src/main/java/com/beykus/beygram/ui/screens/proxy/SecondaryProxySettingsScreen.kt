package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdSecondaryAccountController
import com.beykus.beygram.data.tdlib.TdSecondaryRoutePreference
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Secondary TDLib routing is owned by the process-wide native session
 * coordinator. Never pass the legacy VpnBackend into a secondary proxy editor.
 */
@Composable
internal fun SecondaryProxySettingsScreen(
    accountId: String,
    repository: TelegramRepository,
    controller: TdSecondaryAccountController,
    onManage: () -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val accounts by controller.coordinator.snapshot.collectAsState()
    val row = accounts.accounts.firstOrNull { it.accountId == accountId }
    var route by remember(accountId, repository) { mutableStateOf<TelegramRoute?>(null) }
    var failed by remember(accountId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var changing by remember(accountId) { mutableStateOf(false) }
    var changeFailed by remember(accountId) { mutableStateOf(false) }
    var desiredRoute by remember(accountId) { mutableStateOf<TdSecondaryRoutePreference?>(null) }
    LaunchedEffect(accountId, repository, row?.networkSuspended) {
        val result = repository.currentRoute()
        route = result.getOrNull()
        failed = result.isFailure
    }
    Column(
        Modifier.fillMaxSize().background(palette.screen).safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Text("Подключение аккаунта", fontSize = 22.sp, color = palette.textPrimary)
        }
        Column(
            Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = palette.accent)
            Text("Маршрут TDLib", color = palette.textPrimary, fontSize = 18.sp)
            Text(when (route) {
                TelegramRoute.Direct -> "Прямое соединение"
                TelegramRoute.Subscription -> "Общий Mihomo (SOCKS5)"
                is TelegramRoute.Proxy -> "Отдельный прокси Telegram"
                null -> if (failed) "Не удалось определить маршрут" else "Проверяем маршрут…"
            }, color = palette.textPrimary)
            if (row?.networkSuspended == true)
                Text("Подключение приостановлено: текущий маршрут не прошёл проверку безопасности.",
                    color = palette.danger)
        }
        Text(
            "Mihomo принадлежит всему приложению, а не отдельному Telegram-аккаунту. " +
                "Чтобы изменить маршрут этой сессии, остановите её в управлении аккаунтами " +
                "и подключите заново, выбрав «Наследовать», «Mihomo» или «Прямое». " +
                "Настройки первого аккаунта здесь не изменяются.",
            color = palette.textSecondary, fontSize = 14.sp
        )
        Text("Изменить маршрут", color = palette.textPrimary, fontSize = 18.sp)
        Text("Переключение остановит TDLib этого аккаунта и откроет её заново с проверкой нового маршрута. Авторизация останется на устройстве.",
            color = palette.textSecondary, fontSize = 13.sp)
        listOf(
            TdSecondaryRoutePreference.FOLLOW_PRIMARY to "Наследовать основной маршрут",
            TdSecondaryRoutePreference.SHARED_MIHOMO to "Общий Mihomo",
            TdSecondaryRoutePreference.EXPLICIT_DIRECT to "Прямое соединение"
        ).forEach { (preference, title) ->
            OutlinedButton(
                onClick = { desiredRoute = preference },
                enabled = !changing && row?.phase == com.beykus.beygram.data.tdlib.TdAccountAuthPhase.READY &&
                    row.routePreference != preference,
                modifier = Modifier.fillMaxWidth()
            ) { Text(title + if (row?.routePreference == preference) " ✓" else "") }
        }
        if (changing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (changeFailed) Text(
            "Переключить маршрут не удалось. Предыдущая авторизация сохранена; повторите подключение в управлении аккаунтами.",
            color = palette.danger, fontSize = 13.sp
        )
        Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text("Управление аккаунтами") }
    }
    desiredRoute?.let { preference ->
        AlertDialog(
            onDismissRequest = { if (!changing) desiredRoute = null },
            title = { Text("Переподключить Telegram-аккаунт?") },
            text = { Text("Текущие отправки могут прерваться. Общий Mihomo не будет остановлен, настройки первого аккаунта не изменятся.") },
            confirmButton = {
                TextButton(onClick = {
                    desiredRoute = null
                    changing = true
                    changeFailed = false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                controller.coordinator.reconnectWithRoute(accountId, preference)
                            }
                        } catch (_: Exception) {
                            changeFailed = true
                        } finally { changing = false }
                    }
                }, enabled = !changing) { Text("Переподключить") }
            },
            dismissButton = {
                TextButton(onClick = { desiredRoute = null }, enabled = !changing) { Text("Отмена") }
            }
        )
    }
}
