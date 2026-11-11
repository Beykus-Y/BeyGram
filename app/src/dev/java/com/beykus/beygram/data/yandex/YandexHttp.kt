package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.vpn.MihomoProxyClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

    /** Throws [IOException] when the network is unreachable. */
    fun get(url: String, token: String? = null): YandexResponse =
        send("GET", url, token, emptyMap())

    fun post(url: String, form: Map<String, String>, token: String? = null): YandexResponse =
        send("POST", url, token, form)

    private fun send(method: String, url: String, token: String?, form: Map<String, String>): YandexResponse {
        synchronized(lock) {
            val wait = maxOf(nextAllowedAt, blockedUntil) - now()
            if (wait > 0) pause(wait)
            nextAllowedAt = now() + YandexConfig.MIN_REQUEST_GAP_MS
        }
        val connection = MihomoProxyClient.openConnection(URL(url), proxy = null)
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
            val retryAfter = if (code == HttpURLConnection.HTTP_TOO_MANY_REQUESTS) {
                val seconds = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                val ms = seconds?.times(1_000L) ?: YandexConfig.DEFAULT_RETRY_AFTER_MS
                synchronized(lock) { blockedUntil = now() + ms }
                ms
            } else {
                null
            }
            return YandexResponse(code, body, retryAfter)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
