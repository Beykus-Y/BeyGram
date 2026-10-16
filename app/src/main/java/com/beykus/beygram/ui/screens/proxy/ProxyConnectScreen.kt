package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.TelegramProxyDraft
import com.beykus.beygram.domain.model.TelegramProxyKind
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/** Вкладки «Подключение к Telegram»: три ручных типа и подписка. Порядок и подписи — как в макете. */
enum class ProxyConnectTab(val label: String) {
    MTPROTO("MTProto"),
    SOCKS5("SOCKS5"),
    WEB("Web"),
    SUBSCRIPTION("Подписка")
}

/** Порты, которые экран подставляет сам; при переключении вкладки заменяются на порт новой вкладки. */
private val DEFAULT_PORTS = setOf("443", "1080", "8080")

private fun defaultPort(tab: ProxyConnectTab): String = when (tab) {
    ProxyConnectTab.MTPROTO -> "443"
    ProxyConnectTab.SOCKS5 -> "1080"
    ProxyConnectTab.WEB -> "8080"
    ProxyConnectTab.SUBSCRIPTION -> ""
}

/**
 * Экран добавления прокси вручную и подключения по подписке. Диалогов нет: ошибки показываются на этом же экране.
 * Макет: «Подключение к Telegram» из раздела дизайн-системы про прокси.
 */
@Composable
fun ProxyConnectScreen(
    repository: TelegramRepository,
    vpnBackend: VpnBackend,
    connectionState: TelegramConnectionState,
    initialTab: ProxyConnectTab,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val settings by vpnBackend.settings.collectAsStateWithLifecycle()
    val connections by vpnBackend.connections.collectAsStateWithLifecycle()
    val busy by vpnBackend.busy.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(initialTab) }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(defaultPort(initialTab)) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var subscriptionUrl by remember { mutableStateOf(settings.subscriptionUrl) }
    var subscriptionError by remember { mutableStateOf<String?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    val effectiveSelected = selectedName ?: connections.firstOrNull { it.selected }?.name

    val draft: TelegramProxyDraft? = when (tab) {
        ProxyConnectTab.MTPROTO -> TelegramProxyDraft(TelegramProxyKind.MTPROTO, server.trim(), port.toIntOrNull() ?: 0, secret = secret)
        ProxyConnectTab.SOCKS5 -> TelegramProxyDraft(TelegramProxyKind.SOCKS5, server.trim(), port.toIntOrNull() ?: 0, username = login, password = password)
        ProxyConnectTab.WEB -> TelegramProxyDraft(TelegramProxyKind.HTTP, server.trim(), port.toIntOrNull() ?: 0, username = login, password = password)
        ProxyConnectTab.SUBSCRIPTION -> null
    }
    val canConnect = when {
        sending || busy -> false
        draft != null -> draft.server.isNotEmpty() && draft.port in 1..65_535 &&
            (draft.kind != TelegramProxyKind.MTPROTO || draft.secret.isNotBlank())
        else -> effectiveSelected != null && connections.isNotEmpty()
    }

    val selectTab: (ProxyConnectTab) -> Unit = { next ->
        if (port.isBlank() || port in DEFAULT_PORTS) port = defaultPort(next)
        error = null
        tab = next
    }
    val loadSubscription: () -> Unit = {
        scope.launch {
            subscriptionError = null
            vpnBackend.fetchSubscription(subscriptionUrl.trim())
                .onFailure { subscriptionError = it.message ?: "Не удалось загрузить подписку" }
        }
    }
    val connect: () -> Unit = {
        scope.launch {
            error = null
            val request = draft
            if (request != null) {
                sending = true
                repository.addProxy(request, enable = true)
                    .onSuccess {
                        // Прокси TDLib теперь свой, слушатель подписки больше не нужен.
                        vpnBackend.stopListener()
                        onBack()
                    }
                    .onFailure { error = it.message ?: "Не удалось добавить прокси" }
                sending = false
            } else {
                val name = effectiveSelected ?: return@launch
                vpnBackend.selectConnection(name)
                    .mapCatching { vpnBackend.startProxy().getOrThrow() }
                    .onSuccess { onBack() }
                    .onFailure { error = it.message ?: "Не удалось подключиться" }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        ProxyTopBar(
            title = "Подключение к Telegram",
            onBack = onBack,
            actions = {
                if (tab == ProxyConnectTab.SUBSCRIPTION) {
                    val canRefresh = !busy && subscriptionUrl.isNotBlank()
                    IconButton(onClick = loadSubscription, enabled = canRefresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Обновить подписку",
                            tint = if (canRefresh) palette.accent else palette.textSecondary
                        )
                    }
                }
            }
        )
        if (tab != ProxyConnectTab.SUBSCRIPTION) {
            Text(
                "Введите данные прокси. Их можно получить у провайдера или администратора сервера.",
                color = palette.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
            )
        }
        ProxyPillTabs(
            labels = ProxyConnectTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelect = { selectTab(ProxyConnectTab.entries[it]) },
            modifier = Modifier.padding(bottom = 14.dp)
        )

        when (tab) {
            ProxyConnectTab.SUBSCRIPTION -> ProxySubscriptionPane(
                vpnBackend = vpnBackend,
                connectionState = connectionState,
                url = subscriptionUrl,
                onUrlChange = { subscriptionUrl = it },
                error = subscriptionError,
                selectedName = effectiveSelected,
                onSelect = { selectedName = it }
            )
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProxyField(
                    label = "Сервер",
                    value = server,
                    onValueChange = { server = it }
                )
                ProxyField(
                    label = "Порт",
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    keyboardType = KeyboardType.Number
                )
                if (tab == ProxyConnectTab.MTPROTO) {
                    ProxyField(
                        label = "Секрет",
                        value = secret,
                        onValueChange = { secret = it.trim() }
                    )
                } else {
                    ProxyField(
                        label = "Логин (если нужен)",
                        value = login,
                        onValueChange = { login = it }
                    )
                    ProxyField(
                        label = "Пароль (если нужен)",
                        value = password,
                        onValueChange = { password = it },
                        secret = true
                    )
                }
                if (tab == ProxyConnectTab.WEB) {
                    Text(
                        "HTTP(S)-прокси. Доступен для выбора, если TDLib его поддерживает.",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                error?.let { Text(it, color = palette.danger, fontSize = 13.sp) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Отмена", color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = connect, enabled = canConnect) {
                Text(
                    "Подключить",
                    color = if (canConnect) palette.accent else palette.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
