package com.beykus.beygram.data.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** The only network path of the updater. Every hop is HTTPS on a GitHub host; redirects are followed by hand. */
internal object UpdateHttp {
    private val allowedHosts = setOf(
        "github.com",
        "api.github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com",
    )

    /**
     * Opens a GET. [headers] are sent on every hop. A 416 comes back to the caller only for a ranged request,
     * so the caller can decide what to do with it; every other non-2xx status is a typed failure.
     */
    fun connect(address: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            if (url.protocol != "https" || url.host.lowercase(Locale.ROOT) !in allowedHosts) {
                throw StableUpdateException(StableUpdateReason.UntrustedRedirect, "Сервер перенаправил загрузку на недоверенный адрес.")
            }
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("User-Agent", "BeyGram-Android-Updater")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            // Byte offsets of Range replies refer to the identity encoding only.
            connection.setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                if (location.isNullOrEmpty()) {
                    connection.disconnect()
                    throw StableUpdateException(StableUpdateReason.ServerError, "Сервер вернул пустое перенаправление.")
                }
                connection.disconnect()
                url = URL(url, location)
            } else {
                val rangeRefused = status == 416 && headers.containsKey("Range")
                if (status !in 200..299 && !rangeRefused) {
                    connection.disconnect()
                    throw statusFailure(status)
                }
                return connection
            }
        }
        throw StableUpdateException(StableUpdateReason.ServerError, "Слишком много перенаправлений.")
    }

    suspend fun readText(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > limit) {
                        throw StableUpdateException(StableUpdateReason.InvalidMetadata, "Ответ GitHub слишком большой.")
                    }
                    output.write(buffer, 0, count)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun statusFailure(status: Int): StableUpdateException = when (status) {
        403, 429 -> StableUpdateException(StableUpdateReason.RateLimited, "GitHub HTTP $status")
        404 -> StableUpdateException(StableUpdateReason.MissingRelease, "GitHub HTTP 404")
        else -> StableUpdateException(StableUpdateReason.ServerError, "GitHub HTTP $status")
    }
}
