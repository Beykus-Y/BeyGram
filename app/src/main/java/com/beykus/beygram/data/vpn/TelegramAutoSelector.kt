package com.beykus.beygram.data.vpn

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.VpnNetworkState
import com.beykus.beygram.domain.model.VpnSettings
import com.beykus.beygram.domain.model.VpnTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Waits for a sustained TDLib Connecting state before probing. Network changes cancel
 * in-flight checks, and a small concurrency limit prevents a large subscription from
 * creating a burst of sockets.
 */
internal class TelegramAutoSelector(
    private val scope: CoroutineScope,
    private val connectionState: Flow<TelegramConnectionState>,
    private val networkState: Flow<VpnNetworkState>,
    private val settings: Flow<VpnSettings>,
    private val runtimeState: Flow<ProxyRuntimeState>,
    private val mihomo: MihomoCoreBridge,
    private val report: (AutoSelectEvent) -> Unit = {}
) {
    private var job: Job? = null
    private val checking = AtomicBoolean(false)
    /** Tracks the node the last sweep relied on and nodes that TDLib did not confirm. Survives collectLatest restarts. */
    private val routes = AutoSelectRouteTracker(
        confirmMillis = RETRY_INTERVAL_MS,
        cooldownMillis = COOLDOWN_MILLIS
    )
    @Volatile private var lastGroup: String? = null
    @Volatile private var lastNetworkKey: VpnTransport? = null
    @Volatile private var networkKeyInitialized = false

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            // Метрики прокси (upstreamReachable, telegramConnected) меняются во время перебора; перезапускать должен только статус.
            combine(connectionState, networkState, settings, runtimeState.distinctUntilChangedBy { it.status }) { connection, network, currentSettings, runtime ->
                AutoSelectInputs(connection, network, currentSettings, runtime)
            }.collectLatest { inputs ->
                val settings = inputs.settings
                if (inputs.connection == TelegramConnectionState.Connected) routes.confirm()
                val networkKey = autoSelectNetworkKey(inputs.network)
                val reachable = inputs.network.canReachInternetForTelegram()
                if (shouldResetAutoSelectCooldowns(lastGroup, lastNetworkKey, settings.connectionGroup, networkKey, networkKeyInitialized)) {
                    val wasInitialized = networkKeyInitialized
                    lastGroup = settings.connectionGroup
                    lastNetworkKey = networkKey
                    networkKeyInitialized = true
                    routes.clearCooldowns()
                    if (wasInitialized) {
                        MihomoRuntimeDiagnostics.event("auto", "network changed; cooldowns cleared")
                    }
                }
                if (!shouldProbeAutomaticRoute(settings.autoSelectEnabled,
                        reachable, inputs.connection, inputs.runtime.status)
                ) return@collectLatest

                delay(settings.handoverDelayMillis)
                while (shouldProbeAutomaticRoute(inputs.settings.autoSelectEnabled,
                        inputs.network.canReachInternetForTelegram(), inputs.connection, inputs.runtime.status)
                ) {
                    // The node selected by the previous sweep had a full retry interval to bring TDLib to Connected.
                    if (routes.expireIfUnconfirmed()) {
                        MihomoRuntimeDiagnostics.event("auto", "route not confirmed by Telegram; candidate skipped for a while")
                    }
                    if (checking.compareAndSet(false, true)) {
                        try {
                            MihomoProbeCoordinator.sweep { evaluateAndSelect(settings.connectionGroup) }
                        } finally {
                            checking.set(false)
                        }
                    }
                    delay(RETRY_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun evaluateAndSelect(groupName: String) {
        val connections = try {
            mihomo.listConnections(groupName)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            report(AutoSelectEvent.Failed((error as? UserMessageException)?.text ?: UiText.Res(R.string.err_vpn_servers_load_failed)))
            return
        }
        if (connections.isEmpty()) {
            report(AutoSelectEvent.NoConnections)
            return
        }

        val candidates = connections.filterNot { routes.isCoolingDown(it.name) }
        if (candidates.isEmpty()) {
            report(AutoSelectEvent.NoReachableConnection)
            return
        }

        val results = mutableListOf<com.beykus.beygram.domain.model.MihomoProbeResult>()
        candidates.chunked(MAX_PARALLEL_PROBES).forEach { batch ->
            val batchResults = coroutineScope {
                batch.map { connection ->
                    async(Dispatchers.IO) { mihomo.checkTelegram(connection.name) }
                }.awaitAll()
            }
            results += batchResults
            batchResults.forEach { report(AutoSelectEvent.Probed(it)) }
        }

        val fastest = results.asSequence()
            .filter { it.telegramReachable && it.delayMillis != null }
            .minByOrNull { it.delayMillis!! }
        if (fastest == null) {
            report(AutoSelectEvent.NoReachableConnection)
            return
        }
        val selected = connections.firstOrNull { it.name == fastest.connectionName }
        if (selected?.selected == true) {
            routes.markPending(fastest.connectionName)
            report(AutoSelectEvent.Kept(fastest.connectionName, fastest.delayMillis!!))
            return
        }
        try {
            mihomo.selectConnection(groupName, fastest.connectionName)
            routes.markPending(fastest.connectionName)
            report(AutoSelectEvent.Switched(fastest.connectionName, fastest.delayMillis!!))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            report(AutoSelectEvent.Failed((error as? UserMessageException)?.text ?: UiText.Res(R.string.err_vpn_server_select_failed)))
        }
    }

    private data class AutoSelectInputs(
        val connection: TelegramConnectionState,
        val network: VpnNetworkState,
        val settings: VpnSettings,
        val runtime: ProxyRuntimeState
    )

    private fun VpnNetworkState.canReachInternetForTelegram(): Boolean =
        internetAvailable && (wifiAvailable || cellularAvailable)

    companion object {
        private const val MAX_PARALLEL_PROBES = 4
        private const val RETRY_INTERVAL_MS = 20_000L
        /** A node that TDLib has not confirmed within one retry interval is skipped for this long. */
        private const val COOLDOWN_MILLIS = 10 * 60_000L
    }
}

/**
 * TDLib reports WaitingForNetwork even when Android has a validated route and a proxy
 * node is unreachable. Auto recovery must probe in this state as well as Connecting.
 */
internal fun shouldProbeAutomaticRoute(
    enabled: Boolean,
    networkReady: Boolean,
    telegramState: TelegramConnectionState,
    proxyStatus: ProxyRuntimeStatus
): Boolean = enabled && networkReady && proxyStatus == ProxyRuntimeStatus.RUNNING &&
    (telegramState == TelegramConnectionState.Connecting ||
        telegramState == TelegramConnectionState.WaitingForNetwork)

/** Identifies the network that cooldowns were earned on: only the active transport of a usable network, never availability flags or timestamps. */
internal fun autoSelectNetworkKey(network: VpnNetworkState): VpnTransport? =
    if (network.internetAvailable && (network.wifiAvailable || network.cellularAvailable)) network.activeTransport else null

/** Cooldowns are per network: a new connection group or a different usable network (Wi-Fi <-> cellular, reachable <-> unreachable) gives every node a fresh chance. */
internal fun shouldResetAutoSelectCooldowns(
    previousGroup: String?,
    previousNetwork: VpnTransport?,
    group: String,
    network: VpnTransport?,
    initialized: Boolean
): Boolean = !initialized || previousGroup != group || previousNetwork != network

sealed interface AutoSelectEvent {
    data class Probed(val result: com.beykus.beygram.domain.model.MihomoProbeResult) : AutoSelectEvent
    data class Switched(val connectionName: String, val delayMillis: Long) : AutoSelectEvent
    data class Kept(val connectionName: String, val delayMillis: Long) : AutoSelectEvent
    data class Failed(val message: UiText) : AutoSelectEvent
    data object NoConnections : AutoSelectEvent
    data object NoReachableConnection : AutoSelectEvent
}
