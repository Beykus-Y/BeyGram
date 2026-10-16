package com.beykus.beygram.data.tdlib

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.diagnostics.ConnectionEventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import org.drinkless.tdlib.TdApi
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.system.measureTimeMillis

/** Observes the default route for this application's UID, including external VPNs. */
internal class TelegramNetworkMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onNetworkChanged: (TdApi.NetworkType) -> Unit
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var lastRoute: String? = null
    private var probeJob: kotlinx.coroutines.Job? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        override fun onLost(network: Network) = refresh()
    }

    fun start() {
        manager.registerDefaultNetworkCallback(callback)
        refresh()
    }

    fun reconnect() = refresh(force = true)

    @Synchronized
    private fun refresh(force: Boolean = false) {
        val network = manager.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        val internet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val vpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val mobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        // Include the network ID: Wi-Fi -> VPN can retain the same underlying transport.
        val route = "$network:internet=$internet,vpn=$vpn,wifi=$wifi,mobile=$mobile"
        if (!force && lastRoute == route) return
        lastRoute = route
        val type = when {
            !internet -> TdApi.NetworkTypeNone()
            wifi -> TdApi.NetworkTypeWiFi()
            mobile -> TdApi.NetworkTypeMobile()
            else -> TdApi.NetworkTypeOther()
        }
        ConnectionEventLog.shared.record(
            "Сеть: интернет=$internet, подтверждена=$validated, Wi-Fi=$wifi, мобильная=$mobile, VPN=$vpn"
        )
        onNetworkChanged(type)
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Default route $route,validated=$validated; reopening TDLib connections")
            probeJob?.cancel()
            if (internet) probeJob = scope.launch(Dispatchers.IO) {
                // Diagnostic target is the DC1 address seen in this TDLib build's connection log.
                probe("[$network] TCP DC1 149.154.175.50:443") {
                    Socket().use { it.connect(InetSocketAddress("149.154.175.50", 443), 5_000) }
                    "socket connected"
                }
                ensureActive()
                probe("[$network] HTTPS web.telegram.org") {
                    val connection = URL("https://web.telegram.org/").openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        connection.requestMethod = "HEAD"
                        connection.instanceFollowRedirects = false
                        "HTTP ${connection.responseCode}"
                    } finally {
                        connection.disconnect()
                    }
                }
            }
        }
    }

    private fun probe(label: String, operation: () -> String) {
        var result = ""
        val elapsed = measureTimeMillis {
            result = runCatching(operation).fold(
                onSuccess = { "OK: $it" },
                onFailure = { "FAIL: ${it.javaClass.simpleName}: ${it.message}" }
            )
        }
        Log.i(TAG, "$label $result (${elapsed}ms)")
    }

    companion object {
        const val TAG = "BeyGramNet"
    }
}
