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
        Button(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text("Управление аккаунтами и маршрутом") }
    }
}
