package com.beykus.beygram.data.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.beykus.beygram.domain.model.VpnNetworkState
import com.beykus.beygram.domain.model.VpnTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks the default route and whether validated Wi-Fi/cellular routes are present. */
internal class VpnNetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(VpnNetworkState())
    val state: StateFlow<VpnNetworkState> = _state.asStateFlow()
    private var started = false

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) = refresh()
    }
    private val allNetworksCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) = refresh()
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        manager.registerDefaultNetworkCallback(defaultCallback)
        manager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            allNetworksCallback
        )
        refresh()
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        runCatching { manager.unregisterNetworkCallback(defaultCallback) }
        runCatching { manager.unregisterNetworkCallback(allNetworksCallback) }
    }

    @Synchronized
    @Suppress("DEPRECATION")
    private fun refresh() {
        val networks = runCatching { manager.allNetworks.toList() }.getOrDefault(emptyList())
        val capabilities = networks.mapNotNull { network -> manager.getNetworkCapabilities(network) }
        val active = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        val activeHasInternet = active?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val activeValidated = active?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val transport = when {
            active == null -> VpnTransport.NONE
            active.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> VpnTransport.VPN
            active.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> VpnTransport.WIFI
            active.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> VpnTransport.CELLULAR
            active.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> VpnTransport.ETHERNET
            else -> VpnTransport.OTHER
        }
        val wifiAvailable = capabilities.any {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val cellularAvailable = capabilities.any {
            it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val next = VpnNetworkState(
            internetAvailable = (activeHasInternet && activeValidated) || (transport == VpnTransport.VPN && (wifiAvailable || cellularAvailable)),
            wifiAvailable = wifiAvailable,
            cellularAvailable = cellularAvailable,
            activeTransport = transport,
            changedAtMillis = System.currentTimeMillis()
        )
        if (next.copy(changedAtMillis = 0L) != _state.value.copy(changedAtMillis = 0L)) _state.value = next
    }
}
