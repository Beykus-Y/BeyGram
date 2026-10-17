package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.vpn.ProxyRuntimeStatus
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.domain.model.MihomoConnection
import com.beykus.beygram.domain.model.MihomoProbeResult
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/**
 * Список прокси: подписка Mihomo и прокси, сохранённые вручную. Выбор применяется сразу.
 * Макет: «Прокси» из раздела дизайн-системы про прокси.
 */
@Composable
internal fun ProxyListScreen(
    repository: TelegramRepository,
    vpnBackend: VpnBackend,
    onBack: () -> Unit,
    onAddProxy: () -> Unit,
    onSubscriptionServers: () -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connections by vpnBackend.connections.collectAsStateWithLifecycle()
    val probes by vpnBackend.probes.collectAsStateWithLifecycle()
    val settings by vpnBackend.settings.collectAsStateWithLifecycle()
    val runtime by vpnBackend.runtimeState.collectAsStateWithLifecycle()
    var saved by remember { mutableStateOf<List<SavedTelegramProxy>>(emptyList()) }
    var mihomoEnabled by remember { mutableStateOf(false) }
    // Ключ отсутствует, пока пинг не измерен; null означает «нет связи».
    var latency by remember { mutableStateOf<Map<Int, Long?>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    val reload: () -> Unit = {
        scope.launch {
            saved = repository.loadSavedProxies().getOrElse { error -> message = error.userMessage(context, R.string.proxy_list_load_failed); emptyList() }
            // A failed query keeps the last known state: an error must not show the proxy as off.
            repository.isMihomoProxyEnabled().onSuccess { mihomoEnabled = it }
            latency = emptyMap()
            saved.forEach { proxy ->
                launch {
                    val delay = repository.measureProxyLatency(proxy.server, proxy.port).getOrNull()
                    latency = latency + (proxy.id to delay)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vpnBackend.refreshConnections()
        reload()
    }

    val selectSaved: (SavedTelegramProxy) -> Unit = { proxy ->
        scope.launch {
            repository.enableProxy(proxy.id)
                .onSuccess { vpnBackend.stopListener() }
                .onFailure { message = it.userMessage(context, R.string.proxy_list_switch_failed) }
            reload()
        }
    }
    val selectDirect: () -> Unit = {
        scope.launch {
            repository.disableProxy()
                .onSuccess { vpnBackend.stopListener() }
                .onFailure { message = it.userMessage(context, R.string.proxy_list_switch_failed) }
            reload()
        }
    }
    val selectSubscription: () -> Unit = {
        if (connections.isEmpty()) {
            onSubscriptionServers()
        } else {
            scope.launch {
                vpnBackend.startProxy().onFailure { message = it.userMessage(context, R.string.err_vpn_start_failed) }
                reload()
            }
        }
    }
    val removeSaved: (SavedTelegramProxy) -> Unit = { proxy ->
        scope.launch {
            repository.removeProxy(proxy.id).onFailure { message = it.userMessage(context, R.string.proxy_list_remove_failed) }
            reload()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        ProxyTopBar(
            title = stringResource(R.string.proxy_list_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = onAddProxy) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.proxy_list_add_proxy), tint = palette.accent)
                }
            }
        )
        Text(
            stringResource(R.string.proxy_list_hint),
            color = palette.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ProxySectionLabel(stringResource(R.string.proxy_list_section_subscriptions))
            ProxyCard {
                ProxyRadioRow(
                    selected = mihomoEnabled,
                    title = stringResource(R.string.proxy_list_subscription_main),
                    subtitle = subscriptionSubtitle(connections, probes, settings.subscriptionUrl),
                    onClick = selectSubscription,
                    trailing = {
                        Row(
                            Modifier.clickable(onClick = onSubscriptionServers).padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.proxy_list_servers), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = palette.accentInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }

            ProxySectionLabel(stringResource(R.string.proxy_list_section_manual))
            ProxyCard {
                ProxyRadioRow(
                    selected = !mihomoEnabled && saved.none { it.enabled },
                    title = stringResource(R.string.proxy_list_no_proxy),
                    subtitle = stringResource(R.string.proxy_list_direct_connection),
                    onClick = selectDirect
                )
                saved.forEach { proxy ->
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 50.dp))
                    ProxyRadioRow(
                        selected = !mihomoEnabled && proxy.enabled,
                        title = proxy.kind.label,
                        subtitle = "${proxy.server}:${proxy.port}",
                        onClick = { selectSaved(proxy) },
                        trailing = {
                            ProxyLatencyLabel(latency, proxy.id)
                            IconButton(onClick = { removeSaved(proxy) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.proxy_list_delete_proxy),
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                }
            }

            if (runtime.status == ProxyRuntimeStatus.FAILED) {
                runtime.message?.let {
                    Text(it.resolve(), color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
            message?.let {
                Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }

        ProxyPrimaryButton(
            text = stringResource(R.string.proxy_list_add_proxy),
            onClick = onAddProxy,
            icon = Icons.Rounded.Add,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun ProxyLatencyLabel(latency: Map<Int, Long?>, id: Int) {
    val palette = beyGramPalette()
    val (text, color) = when {
        !latency.containsKey(id) -> "…" to palette.textSecondary
        latency[id] == null -> stringResource(R.string.proxy_no_connection) to palette.danger
        else -> latencyStyle(latency[id]!!, palette)
    }
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 4.dp))
}

@Composable private fun subscriptionSubtitle(
    connections: List<MihomoConnection>,
    probes: Map<String, MihomoProbeResult>,
    url: String
): String = when {
    connections.isEmpty() && url.isBlank() -> stringResource(R.string.proxy_list_add_subscription_link)
    connections.isEmpty() -> stringResource(R.string.proxy_list_servers_not_loaded)
    else -> {
        val best = bestReachable(probes)
        serversCountText(connections.size) + best?.let { " · " + stringResource(R.string.proxy_list_best_server, it.first, it.second) }.orEmpty()
    }
}
