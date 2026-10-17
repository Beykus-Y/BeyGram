package com.beykus.beygram.data.vpn

import android.content.Context
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.Socket
import java.net.URL

/** Shared loopback SOCKS entry point for BeyGram-owned Java network clients. */
internal object MihomoProxyClient {
    const val HOST = "127.0.0.1"
    const val PORT = 17891

    private val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(HOST, PORT))

    @Volatile private var credentials: LoopbackProxyCredentials? = null

    /** Gives Java clients the loopback SOCKS credentials. The authenticator answers only for this listener. */
    fun install(context: Context) {
        val loaded = LoopbackProxyCredentials.get(context)
        credentials = loaded
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val isLocalListener = requestingPort == PORT && (requestingHost == HOST || requestingHost == "localhost")
                return if (isLocalListener) PasswordAuthentication(loaded.username, loaded.password.toCharArray()) else null
            }
        })
    }

    fun isRunning(): Boolean = MihomoProxyService.runtimeState.value.status == ProxyRuntimeStatus.RUNNING

    fun routeName(): String = if (isRunning()) "local_proxy" else "direct"

    fun currentProxy(): Proxy? = socksProxy.takeIf { isRunning() }

    /** Goes through the listener while it runs. Pass null to connect directly. */
    fun openConnection(url: URL, proxy: Proxy? = currentProxy()): HttpURLConnection {
        return (if (proxy == null) url.openConnection() else url.openConnection(proxy)) as HttpURLConnection
    }

    fun newSocket(): Socket = Socket(socksProxy)

    fun awaitReady(timeoutMillis: Long = 5_000) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var lastError: Exception? = null
        while (System.nanoTime() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress(HOST, PORT), 300) }
                return
            } catch (error: Exception) {
                lastError = error
                Thread.sleep(100)
            }
        }
        MihomoRuntimeDiagnostics.event("listener", "port not ready error=${lastError?.javaClass?.simpleName ?: "none"}")
        throw MihomoCoreException("Локальный прокси не запустился")
    }
}
