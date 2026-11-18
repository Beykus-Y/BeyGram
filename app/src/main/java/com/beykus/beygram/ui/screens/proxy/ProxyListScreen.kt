package com.beykus.beygram.ui.screens.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.accessibility.statusAnnouncement
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.components.settingsIconTint
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.groupCardShape
import com.beykus.beygram.ui.screens.settings.SettingsGroup
import com.beykus.beygram.ui.screens.settings.SettingsRowDivider
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/**
 * Список прокси: подписка Mihomo и прокси, сохранённые вручную. Выбор применяется сразу.
 * Сверху сводка маршрута: что сейчас используется для Telegram и есть ли у маршрута подключение или ошибка.
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
    var savedLoaded by remember { mutableStateOf(false) }
    var savedLoading by remember { mutableStateOf(false) }
    var savedLoadError by remember { mutableStateOf<String?>(null) }
    var reloadGeneration by remember { mutableStateOf(0) }
    var pendingDeletion by remember { mutableStateOf<SavedTelegramProxy?>(null) }
    var deletingProxyId by remember { mutableStateOf<Int?>(null) }
    var deletionError by remember { mutableStateOf<String?>(null) }
    var mihomoEnabled by remember { mutableStateOf(false) }
    // Ключ отсутствует, пока пинг не измерен; null означает «нет связи».
    var latency by remember { mutableStateOf<Map<Int, Long?>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    val reload: () -> Unit = {
        val generation = ++reloadGeneration
        savedLoading = true
        savedLoadError = null
        scope.launch {
            val result = repository.loadSavedProxies()
            if (generation != reloadGeneration) return@launch
            result.onSuccess { loaded ->
                saved = loaded
                savedLoaded = true
                latency = emptyMap()
                loaded.forEach { proxy ->
                    launch {
                        val measured = repository.measureProxyLatency(proxy.server, proxy.port).getOrNull()
                        if (generation == reloadGeneration) latency = latency + (proxy.id to measured)
                    }
                }
            }.onFailure { error ->
                // An unsuccessful refresh must never erase previously loaded proxies.
                savedLoadError = error.userMessage(context, R.string.proxy_list_load_failed)
            }
            // A failed status check does not imply that the active route was disabled.
            repository.isMihomoProxyEnabled().onSuccess {
                if (generation == reloadGeneration) mihomoEnabled = it
            }
            if (generation == reloadGeneration) savedLoading = false
        }
    }

    LaunchedEffect(Unit) {
        vpnBackend.refreshConnections()
        reload()
    }

    val selectSaved: (SavedTelegramProxy) -> Unit = { proxy ->
        scope.launch {
            vpnBackend.selectNonSubscriptionRoute { repository.enableProxy(proxy.id) }
                .onFailure { message = it.userMessage(context, R.string.proxy_list_switch_failed) }
            reload()
        }
    }
    val selectDirect: () -> Unit = {
        scope.launch {
            vpnBackend.selectNonSubscriptionRoute { repository.disableProxy() }
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
        if (deletingProxyId == null) {
            pendingDeletion = proxy
            deletionError = null
        }
    }

    // The summary uses only states this screen already reads: the saved list, the subscription switch and the proxy status.
    val routeState: ProxyRouteState = when {
        mihomoEnabled -> ProxyRouteState.Subscription(
            starting = runtime.status == ProxyRuntimeStatus.STARTING,
            failed = runtime.status == ProxyRuntimeStatus.FAILED
        )
        !savedLoaded && savedLoadError != null -> ProxyRouteState.Unknown
        !savedLoaded -> ProxyRouteState.Checking
        else -> saved.firstOrNull { it.enabled }?.let { ProxyRouteState.Manual(it) } ?: ProxyRouteState.Direct
    }
    val selectedServer = connections.firstOrNull { it.selected }?.name
    val subscriptionDetail = selectedServer?.let { stringResource(R.string.proxy_sub_selected_connection, it) }
        ?: subscriptionSubtitle(connections, probes, settings.subscriptionUrl)

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
            ProxyRouteSummary(state = routeState, subscriptionDetail = subscriptionDetail)

            SettingsGroup(stringResource(R.string.proxy_list_section_subscriptions)) {
                ProxyRadioRow(
                    selected = mihomoEnabled,
                    title = stringResource(R.string.proxy_list_subscription_main),
                    subtitle = subscriptionSubtitle(connections, probes, settings.subscriptionUrl),
                    onClick = selectSubscription,
                    actions = {
                        TextButton(onClick = onSubscriptionServers) {
                            Text(
                                stringResource(R.string.proxy_list_servers),
                                color = palette.accentInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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

            SettingsGroup(stringResource(R.string.proxy_list_section_manual)) {
                savedLoadError?.let { error ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error, color = palette.danger, fontSize = 13.sp, modifier = Modifier.weight(1f).errorAnnouncement())
                        TextButton(onClick = reload, enabled = !savedLoading) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
                if (savedLoading && !savedLoaded) {
                    Text(
                        stringResource(R.string.proxy_list_loading_saved),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).statusAnnouncement()
                    )
                }
                if (savedLoaded) {
                    ProxyRadioRow(
                        selected = !mihomoEnabled && saved.none { it.enabled },
                        title = stringResource(R.string.proxy_list_no_proxy),
                        subtitle = stringResource(R.string.proxy_list_direct_connection),
                        onClick = selectDirect
                    )
                    saved.forEach { proxy ->
                        SettingsRowDivider(startInset = 50.dp)
                        SavedProxyRow(
                            proxy = proxy,
                            selected = !mihomoEnabled && proxy.enabled,
                            latency = latency,
                            deleting = deletingProxyId != null,
                            onSelect = { selectSaved(proxy) },
                            onDelete = { removeSaved(proxy) }
                        )
                    }
                }
            }

            if (runtime.status == ProxyRuntimeStatus.FAILED) {
                runtime.message?.let {
                    Text(
                        it.resolve(),
                        color = palette.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).errorAnnouncement()
                    )
                }
            }
            message?.let {
                Text(
                    it,
                    color = palette.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).errorAnnouncement()
                )
            }
        }

        ProxyPrimaryButton(
            text = stringResource(R.string.proxy_list_add_proxy),
            onClick = onAddProxy,
            icon = Icons.Rounded.Add,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
        )
    }
    pendingDeletion?.let { proxy ->
        BeyGramDialog(
            onDismissRequest = {
                if (deletingProxyId == null) {
                    pendingDeletion = null
                    deletionError = null
                }
            },
            title = stringResource(R.string.proxy_list_delete_confirm_title),
            content = {
                Column {
                    Text(stringResource(R.string.proxy_list_delete_confirm_detail, proxy.kind.label, proxy.server, proxy.port))
                    if (proxy.enabled && !mihomoEnabled) {
                        Text(stringResource(R.string.proxy_list_delete_active_warning), color = palette.danger)
                    }
                    deletionError?.let {
                        Text(it, color = palette.danger, modifier = Modifier.padding(top = 8.dp).errorAnnouncement())
                    }
                }
            },
            confirmText = stringResource(if (deletingProxyId != null) R.string.proxy_list_deleting else R.string.common_delete),
            onConfirm = {
                if (deletingProxyId == null) {
                    deletingProxyId = proxy.id
                    scope.launch {
                        repository.removeProxy(proxy.id)
                            .onSuccess {
                                // Reconcile the last-known-good snapshot before requesting remote state.
                                // Refresh may fail even though the deletion succeeded.
                                saved = saved.filterNot { it.id == proxy.id }
                                latency = latency - proxy.id
                                savedLoaded = true
                                pendingDeletion = null
                                deletionError = null
                                reload()
                            }
                            .onFailure {
                                deletionError = it.userMessage(context, R.string.proxy_list_remove_failed)
                            }
                        deletingProxyId = null
                    }
                }
            },
            confirmEnabled = deletingProxyId == null,
            destructive = true,
            dismissText = stringResource(R.string.common_cancel),
            onDismiss = { pendingDeletion = null; deletionError = null },
            dismissEnabled = deletingProxyId == null
        )
    }
}

/** Состояние маршрута Telegram для сводки наверху списка. */
private sealed interface ProxyRouteState {
    /** Списки ещё не прочитаны. */
    data object Checking : ProxyRouteState

    /** Списки не прочитаны из-за ошибки, и подписка не включена: маршрут неизвестен. */
    data object Unknown : ProxyRouteState

    data object Direct : ProxyRouteState

    data class Manual(val proxy: SavedTelegramProxy) : ProxyRouteState

    /** Маршрут через подписку. [starting] и [failed] берутся из статуса локального прокси подписки. */
    data class Subscription(val starting: Boolean, val failed: Boolean) : ProxyRouteState
}

/**
 * Верхняя сводка: какой маршрут Telegram выбран и есть ли у него состояние подключения или ошибка. Состояние
 * читается по тексту и значку, а не только по цвету.
 */
@Composable
private fun ProxyRouteSummary(state: ProxyRouteState, subscriptionDetail: String) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val summary: Pair<String, String?> = when (state) {
        ProxyRouteState.Checking -> stringResource(R.string.multi_proxy_checking) to null
        ProxyRouteState.Unknown -> stringResource(R.string.multi_proxy_unknown) to null
        ProxyRouteState.Direct -> stringResource(R.string.proxy_list_direct_connection) to null
        is ProxyRouteState.Manual -> state.proxy.kind.label to "${state.proxy.server}:${state.proxy.port}"
        is ProxyRouteState.Subscription -> stringResource(R.string.proxy_route_subscription) to subscriptionDetail
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassCard(design, groupSurfaceColor(), palette.textSecondary, groupCardShape())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = settingsIconTint(), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.multi_proxy_route),
                color = palette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            summary.first,
            color = if (state == ProxyRouteState.Unknown) palette.danger else palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
        )
        summary.second?.let {
            Text(
                it,
                color = palette.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (state is ProxyRouteState.Subscription && (state.starting || state.failed)) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.failed) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = palette.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.proxy_connect_connect_failed),
                        color = palette.danger,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.errorAnnouncement()
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(16.dp), color = palette.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.notif_service_connecting),
                        color = palette.accentInk,
                        fontSize = 14.sp,
                        modifier = Modifier.statusAnnouncement()
                    )
                }
            }
        }
    }
}

/**
 * Строка сохранённого прокси: тип протокола в плашке, адрес и вид прокси слева, пинг справа. Удаление спрятано в меню
 * действий и по-прежнему требует подтверждения в диалоге.
 */
@Composable
private fun SavedProxyRow(
    proxy: SavedTelegramProxy,
    selected: Boolean,
    latency: Map<Int, Long?>,
    deleting: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    ProxyRadioRow(
        selected = selected,
        title = "${proxy.server}:${proxy.port}",
        subtitle = proxy.kind.label,
        onClick = onSelect,
        badges = { ProxyBadge(proxy.kind.badge, palette.surface2, palette.textSecondary) },
        trailing = { ProxyLatencyLabel(latency, proxy.id) },
        actions = {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = !deleting,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.proxy_list_row_actions),
                        tint = palette.textSecondary
                    )
                }
                BeyGramPopover(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    BeyGramPopoverItem(
                        text = stringResource(R.string.proxy_list_delete_proxy),
                        danger = true,
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    )
}

/** Пинг сохранённого прокси: ещё измеряется, нет связи или значение. Состояние показывают и значок, и текст. */
@Composable
private fun ProxyLatencyLabel(latency: Map<Int, Long?>, id: Int) {
    val palette = beyGramPalette()
    val measured = latency[id]
    when {
        !latency.containsKey(id) -> ProxyMeasuring()
        measured == null -> ProxyLatencyText(stringResource(R.string.proxy_no_connection), palette.danger, warning = true)
        else -> {
            val (text, color) = latencyStyle(measured, palette)
            ProxyLatencyText(text, color)
        }
    }
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
