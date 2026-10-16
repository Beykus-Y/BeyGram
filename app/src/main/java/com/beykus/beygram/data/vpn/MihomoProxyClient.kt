package com.beykus.beygram.data.vpn

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

/** Shared loopback SOCKS entry point for BeyGram-owned Java network clients. */
internal object MihomoProxyClient {
    const val HOST = "127.0.0.1"
    const val PORT = 17891

    private val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(HOST, PORT))

    fun isRunning(): Boolean = MihomoProxyService.runtimeState.value.status == ProxyRuntimeStatus.RUNNING

    fun routeName(): String = if (isRunning()) "local_socks" else "direct"

    fun currentProxy(): Proxy? = socksProxy.takeIf { isRunning() }

    fun openConnection(url: URL): HttpURLConnection {
        val proxy = currentProxy()
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
        throw MihomoCoreException("Mihomo SOCKS listener did not bind to 127.0.0.1:$PORT${lastError?.let { ": ${it.javaClass.simpleName}" }.orEmpty()}")
    }
}
