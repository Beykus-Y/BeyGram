package com.beykus.beygram.data.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.beykus.beygram.domain.model.MihomoConnection
import com.beykus.beygram.domain.model.MihomoProbeResult
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.VpnNetworkState
import com.beykus.beygram.domain.model.VpnSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The VPN/proxy controller of the process. It is created once with the application scope, so the proxy
 * and its automatic selection keep working when no screen is shown.
 */
internal class VpnBackend private constructor(
    context: Context,
    connectionState: StateFlow<TelegramConnectionState>,
    private val scope: CoroutineScope,
    private val setTdLibProxy: suspend (Boolean) -> Result<Unit>,
    private val isTdLibProxyEnabled: suspend () -> Boolean
) {
    private val appContext = context.applicationContext
    private val settingsStore = VpnSettingsStore(appContext)
    private val installationHwid = InstallationHwidProvider(appContext)
    private val subscriptionClient = SubscriptionClient(installationHwid)
    private val mihomo = MihomoCoreBridge(appContext)
    private val networkMonitor = VpnNetworkMonitor(appContext)
    private val selector = TelegramAutoSelector(
        scope = scope,
        connectionState = connectionState,
        networkState = networkMonitor.state,
        settings = settingsStore.settings,
        runtimeState = MihomoProxyService.runtimeState,
        mihomo = mihomo,
        report = { _autoSelectEvents.value = it }
    )

    val settings: StateFlow<VpnSettings> = settingsStore.settings
    val network: StateFlow<VpnNetworkState> = networkMonitor.state
    val runtimeState: StateFlow<ProxyRuntimeState> = MihomoProxyService.runtimeState
    private val _connections = MutableStateFlow<List<MihomoConnection>>(emptyList())
    val connections: StateFlow<List<MihomoConnection>> = _connections.asStateFlow()
    private val _probes = MutableStateFlow<Map<String, MihomoProbeResult>>(emptyMap())
    val probes: StateFlow<Map<String, MihomoProbeResult>> = _probes.asStateFlow()
    private val _autoSelectEvents = MutableStateFlow<AutoSelectEvent?>(null)
    val autoSelectEvents: StateFlow<AutoSelectEvent?> = _autoSelectEvents.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _probeProgress = MutableStateFlow<ProbeProgress?>(null)
    val probeProgress: StateFlow<ProbeProgress?> = _probeProgress.asStateFlow()

    init {
        networkMonitor.start()
        selector.start()
        scope.launch(Dispatchers.IO) { restoreAfterProcessStart() }
    }

    /**
     * TDLib keeps its enabled proxy across process restarts. When that proxy is BeyGram's listener, start the listener
     * again; if that fails, switch TDLib back to its previous proxy instead of leaving it on a dead local port.
     */
    private suspend fun restoreAfterProcessStart() {
        if (runtimeState.value.status == ProxyRuntimeStatus.RUNNING) return
        if (runCatching { isTdLibProxyEnabled() }.getOrDefault(false)) {
            startProxy()
                .onSuccess { refreshConnections() }
                .onFailure {
                    setTdLibProxy(false)
                    MihomoRuntimeDiagnostics.event("backend", "restore failed; TDLib proxy switched off")
                }
            return
        }
        if (mihomo.profileExists()) {
            runCatching {
                mihomo.ensureInitialized()
                mihomo.stopProxyListener()
            }.onSuccess { refreshConnections() }
        }
    }

    fun updateAutoSelect(enabled: Boolean) {
        settingsStore.update { it.copy(autoSelectEnabled = enabled) }
    }

    fun updateSubscriptionUrl(url: String) {
        settingsStore.update { it.copy(subscriptionUrl = url.trim()) }
    }

    suspend fun startProxy(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            MihomoRuntimeDiagnostics.event("backend", "proxy start requested")
            if (!mihomo.profileExists()) throw IllegalStateException("Сначала загрузите подписку Mihomo")
            ContextCompat.startForegroundService(appContext, Intent(appContext, MihomoProxyService::class.java).setAction(MihomoProxyService.ACTION_START))
            val state = withTimeout(30_000) {
                MihomoProxyService.runtimeState.first { it.status == ProxyRuntimeStatus.RUNNING || it.status == ProxyRuntimeStatus.FAILED }
            }
            if (state.status == ProxyRuntimeStatus.FAILED) throw IllegalStateException(state.message ?: "Не удалось запустить Mihomo")
            val tdlib = setTdLibProxy(true)
            if (tdlib.isFailure) {
                appContext.startService(Intent(appContext, MihomoProxyService::class.java).setAction(MihomoProxyService.ACTION_STOP))
                throw tdlib.exceptionOrNull() ?: IllegalStateException("Не удалось подключить TDLib к Mihomo")
            }
            Result.success(Unit)
        } catch (error: TimeoutCancellationException) {
            MihomoRuntimeDiagnostics.event("backend", "start timed out")
            Result.failure(IllegalStateException("Mihomo не запустился за 30 секунд"))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { MihomoRuntimeDiagnostics.failure("backend start", error); Result.failure(error) }
    }

    /** Turns the subscription proxy off: TDLib returns to the proxy it used before, then the listener stops. */
    suspend fun stopProxy(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            MihomoRuntimeDiagnostics.event("backend", "proxy stop requested")
            val tdlib = setTdLibProxy(false)
            if (tdlib.isFailure) throw tdlib.exceptionOrNull() ?: IllegalStateException("Не удалось отключить прокси TDLib")
            stopListenerNow()
            Result.success(Unit)
        } catch (error: TimeoutCancellationException) {
            MihomoRuntimeDiagnostics.event("backend", "stop timed out")
            Result.failure(IllegalStateException("Mihomo не остановился за 10 секунд"))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { MihomoRuntimeDiagnostics.failure("backend stop", error); Result.failure(error) }
    }

    /**
     * Stops only the local listener and leaves TDLib's proxy alone. Used when the user picks a saved proxy:
     * TDLib no longer points at the listener, so keeping it running would only hold a foreground notification.
     */
    suspend fun stopListener(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopListenerNow()
            Result.success(Unit)
        } catch (error: TimeoutCancellationException) {
            MihomoRuntimeDiagnostics.event("backend", "listener stop timed out")
            Result.failure(IllegalStateException("Mihomo не остановился за 10 секунд"))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { MihomoRuntimeDiagnostics.failure("backend listener stop", error); Result.failure(error) }
    }

    private suspend fun stopListenerNow() {
        if (runtimeState.value.status == ProxyRuntimeStatus.STOPPED) return
        appContext.startService(Intent(appContext, MihomoProxyService::class.java).setAction(MihomoProxyService.ACTION_STOP))
        withTimeout(10_000) { MihomoProxyService.runtimeState.first { it.status == ProxyRuntimeStatus.STOPPED } }
    }

    suspend fun fetchSubscription(url: String = settings.value.subscriptionUrl): Result<List<MihomoConnection>> =
        withContext(Dispatchers.IO) {
            if (_busy.value) return@withContext Result.failure(IllegalStateException("VPN settings are already being updated"))
            _busy.value = true
            try {
                VpnSubscriptionDiagnostics.event("fetch", "started")
                val trimmedUrl = url.trim()
                val response = subscriptionClient.fetch(trimmedUrl)
                VpnSubscriptionDiagnostics.event(
                    "fetch",
                    "downloaded bodyChars=${response.configuration.length}"
                )
                val proxyWasRunning = runtimeState.value.status == ProxyRuntimeStatus.RUNNING
                val groupName = mihomo.importSubscription(response.configuration)
                mihomo.stopProxyListener()
                if (proxyWasRunning) mihomo.startProxyListener()
                settingsStore.update { it.copy(subscriptionUrl = trimmedUrl, connectionGroup = groupName) }
                var loaded = mihomo.listConnections(groupName)
                if (loaded.isNotEmpty() && loaded.none { it.selected }) {
                    mihomo.selectConnection(groupName, loaded.first().name)
                    loaded = mihomo.listConnections(groupName)
                }
                _connections.value = loaded
                _probes.value = emptyMap()
                VpnSubscriptionDiagnostics.event("fetch", "completed connectionCount=${loaded.size}")
                Result.success(loaded)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                VpnSubscriptionDiagnostics.failure("fetch", error)
                Result.failure(error)
            } finally {
                _busy.value = false
            }
        }

    suspend fun refreshConnections(): Result<List<MihomoConnection>> = withContext(Dispatchers.IO) {
        try {
            var loaded = mihomo.listConnections(settings.value.connectionGroup)
            if (loaded.isNotEmpty() && loaded.none { it.selected }) {
                mihomo.selectConnection(settings.value.connectionGroup, loaded.first().name)
                loaded = mihomo.listConnections(settings.value.connectionGroup)
            }
            _connections.value = loaded
            MihomoRuntimeDiagnostics.event("connections", "refreshed count=${loaded.size}")
            Result.success(loaded)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            MihomoRuntimeDiagnostics.failure("connections refresh", error)
            Result.failure(error)
        }
    }

    suspend fun probeAllConnections(): Result<List<MihomoProbeResult>> = withContext(Dispatchers.IO) {
        try {
            val current = mihomo.listConnections(settings.value.connectionGroup)
            val results = mutableListOf<MihomoProbeResult>()
            _probeProgress.value = ProbeProgress(0, current.size)
            _probes.value = emptyMap()
            current.chunked(MAX_PARALLEL_PROBES).forEach { batch ->
                val batchResults = coroutineScope {
                    batch.map { connection ->
                        async { mihomo.checkTelegram(connection.name) }
                    }.awaitAll()
                }
                results += batchResults
                _probes.value = results.associateBy { it.connectionName }
                _probeProgress.value = ProbeProgress(results.size, current.size)
            }
            Result.success(results.toList())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            _probeProgress.value = null
        }
    }

    suspend fun selectConnection(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mihomo.selectConnection(settings.value.connectionGroup, name)
            _connections.value = mihomo.listConnections(settings.value.connectionGroup)
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    companion object {
        private const val MAX_PARALLEL_PROBES = 4

        @Volatile private var instance: VpnBackend? = null

        /** Returns the process-wide backend. The first caller's [scope] owns its background work. */
        fun getInstance(
            context: Context,
            connectionState: StateFlow<TelegramConnectionState>,
            scope: CoroutineScope,
            setTdLibProxy: suspend (Boolean) -> Result<Unit>,
            isTdLibProxyEnabled: suspend () -> Boolean
        ): VpnBackend = instance ?: synchronized(this) {
            instance ?: VpnBackend(context, connectionState, scope, setTdLibProxy, isTdLibProxyEnabled).also { instance = it }
        }
    }
}

internal data class ProbeProgress(val completed: Int, val total: Int)
