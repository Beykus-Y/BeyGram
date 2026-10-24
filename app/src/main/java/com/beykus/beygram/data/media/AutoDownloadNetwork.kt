package com.beykus.beygram.data.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Pure transport classification; Android capabilities are read only by [currentAutoDownloadNetwork]. */
internal fun classifyAutoDownloadNetwork(
    hasInternet: Boolean,
    wifi: Boolean,
    mobile: Boolean,
    vpn: Boolean,
    roaming: Boolean
): AutoDownloadNetwork = when {
    !hasInternet -> AutoDownloadNetwork.None
    wifi -> AutoDownloadNetwork.Wifi
    mobile && roaming -> AutoDownloadNetwork.Roaming
    mobile -> AutoDownloadNetwork.Mobile
    vpn -> AutoDownloadNetwork.Other
    else -> AutoDownloadNetwork.Other
}

/**
 * Current route for local download decisions. An external VPN hides its transport on many devices, so
 * the code also inspects the other known networks; if no Wi-Fi/cellular transport can be proved, the
 * result is [AutoDownloadNetwork.Other], which follows the conservative mobile policy. The local
 * Mihomo proxy is a SOCKS listener inside the app and does not change this transport.
 */
internal fun currentAutoDownloadNetwork(context: Context): AutoDownloadNetwork {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return AutoDownloadNetwork.None
    val active = manager.activeNetwork ?: return AutoDownloadNetwork.None
    val activeCaps = manager.getNetworkCapabilities(active) ?: return AutoDownloadNetwork.None
    val candidates = buildList {
        add(activeCaps)
        manager.allNetworks.forEach { network ->
            manager.getNetworkCapabilities(network)?.let(::add)
        }
    }
    val activeTransport = activeCaps.takeIf {
        !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }
    val transport = activeTransport ?: candidates.firstOrNull {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    } ?: candidates.firstOrNull {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    val wifi = transport?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val mobile = transport?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    @Suppress("DEPRECATION")
    val roaming = mobile && transport?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) == false
    return classifyAutoDownloadNetwork(
        hasInternet = activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        wifi = wifi,
        mobile = mobile,
        vpn = activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        roaming = roaming
    )
}

/** Process-lifetime route observer used to stop automatic downloads when the connection changes. */
internal class AutoDownloadNetworkMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onChanged: (AutoDownloadNetwork) -> Unit
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(ConnectivityManager::class.java)
    private var last: AutoDownloadNetwork? = null
    @Volatile private var route = AutoDownloadNetwork.None
    @Volatile private var started = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        override fun onLost(network: Network) = refresh()
    }

    fun current(): AutoDownloadNetwork = route

    @Synchronized
    fun start() {
        val service = manager ?: return
        if (started) return
        started = true
        runCatching { service.registerDefaultNetworkCallback(callback) }
            .onFailure { started = false }
        if (started) refresh()
    }

    @Synchronized
    fun stop() {
        val service = manager ?: return
        if (!started) return
        started = false
        runCatching { service.unregisterNetworkCallback(callback) }
    }

    fun refresh() {
        if (!started) return
        val next = currentAutoDownloadNetwork(appContext)
        if (last == next) return
        last = next
        route = next
        scope.launch { onChanged(next) }
    }
}

/** Applies the same local policy used by the settings screen before an automatic download is requested. */
object AutoDownloadRuntime {
    fun decision(context: Context, request: AutoDownloadRequest, accountId: String = "legacy"): AutoDownloadDecision = decideAutoDownload(
        settings = AutoDownloadSettingsStore.getInstance(context.applicationContext, accountId).settings.value,
        network = currentAutoDownloadNetwork(context.applicationContext),
        request = request
    )

    /** Returns true when [download] was invoked. A denied request leaves the existing manual button untouched. */
    fun request(context: Context, request: AutoDownloadRequest, download: (Int) -> Unit, accountId: String = "legacy"): Boolean {
        if (decision(context, request, accountId) != AutoDownloadDecision.Allowed) return false
        download(request.fileId)
        return true
    }
}
