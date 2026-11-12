package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.vpn.MihomoProxyClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder

private const val HTTP_TOO_MANY_REQUESTS = 429

/** Raw result of one request. The body is never logged. */
internal class YandexResponse(val code: Int, val body: String, val retryAfterMs: Long?)

/**
 * The only path to Yandex. Requests go directly, not through the local proxy (the service answers "unavailable"
 * outside its regions), in one queue with a fixed gap between requests. A 429 pauses the whole queue.
 */
internal class YandexHttp(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val pause: (Long) -> Unit = { ms -> Thread.sleep(ms) },
) {
    private val lock = Any()
    private var nextAllowedAt = 0L
    private var blockedUntil = 0L

    /**
     * Throws [IOException] when the network is unreachable. [paced] false skips the gap between requests: it is for
     * helper files on the storage hosts (the download XML of a track), which are not API calls. A 429 pause still applies.
     */
    fun get(url: String, token: String? = null, paced: Boolean = true): YandexResponse =
        send("GET", url, token, emptyMap(), paced)

    fun post(url: String, form: Map<String, String>, token: String? = null): YandexResponse =
        send("POST", url, token, form, paced = true)

    private fun send(method: String, url: String, token: String?, form: Map<String, String>, paced: Boolean): YandexResponse {
        synchronized(lock) {
            val wait = (if (paced) maxOf(nextAllowedAt, blockedUntil) else blockedUntil) - now()
            if (wait > 0) pause(wait)
            if (paced) nextAllowedAt = now() + YandexConfig.MIN_REQUEST_GAP_MS
        }
        val connection = MihomoProxyClient.openConnection(URL(url), proxy = null)
        // A response read to the end and closed leaves the connection in the pool, so the next request to the same host
        // skips the TCP and TLS handshake (a few hundred milliseconds on a phone). Only a request that failed half-way
        // is disconnected.
        var reusable = false
        try {
            connection.requestMethod = method
            connection.connectTimeout = YandexConfig.CONNECT_TIMEOUT_MS
            connection.readTimeout = YandexConfig.READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", YandexConfig.USER_AGENT)
            connection.setRequestProperty("X-Yandex-Music-Client", YandexConfig.CLIENT_HEADER)
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "OAuth $token")
            if (method == "POST") {
                val payload = form.entries.joinToString("&") {
                    "${encode(it.key)}=${encode(it.value)}"
                }.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
            reusable = true
            val retryAfter = if (code == HTTP_TOO_MANY_REQUESTS) {
                val seconds = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                val ms = seconds?.times(1_000L) ?: YandexConfig.DEFAULT_RETRY_AFTER_MS
                synchronized(lock) { blockedUntil = now() + ms }
                ms
            } else {
                null
            }
            return YandexResponse(code, body, retryAfter)
        } finally {
            if (!reusable) connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
