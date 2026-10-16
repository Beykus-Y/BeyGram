package com.beykus.beygram.data.vpn

import android.os.Build
import com.beykus.beygram.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

data class FetchedVpnSubscription(
    val configuration: String,
    val contentType: String?,
    val userInfo: String?
)

/** Fetches a provider-owned Mihomo/Clash profile without putting credentials in logs. */
internal class SubscriptionClient(
    private val installationHwidProvider: InstallationHwidProvider
) {
    fun fetch(subscriptionUrl: String): FetchedVpnSubscription {
        VpnSubscriptionDiagnostics.event("request", "started; url=redacted")
        val proxy = MihomoProxyClient.currentProxy()
        VpnSubscriptionDiagnostics.event("route", MihomoProxyClient.routeName())
        if (proxy == null) return fetchVia(subscriptionUrl, proxy = null)
        return try {
            fetchVia(subscriptionUrl, proxy)
        } catch (error: IOException) {
            // The selected node can be down while the subscription host is reachable. Only a network failure is retried,
            // once and directly, which shows the device's own address to the subscription host. An HTTP or format error
            // is not a route problem and is reported as it is.
            VpnSubscriptionDiagnostics.failure("route", error)
            VpnSubscriptionDiagnostics.event("route", "direct retry after proxy failure")
            fetchVia(subscriptionUrl, proxy = null)
        }
    }

    private fun fetchVia(subscriptionUrl: String, proxy: Proxy?): FetchedVpnSubscription {
        var currentUrl = validateUrl(subscriptionUrl)
        val originalOrigin = origin(currentUrl)
        repeat(MAX_REDIRECTS + 1) { redirectNumber ->
            val connection = MihomoProxyClient.openConnection(currentUrl, proxy).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/yaml, application/x-yaml, text/yaml, text/plain, application/octet-stream")
                setRequestProperty("User-Agent", "BeyGram/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
                // The ID is a random app-install pseudonym; physical identifiers are never read or sent.
                setRequestProperty(HWID_HEADER, installationHwidProvider.getOrCreate())
                useCaches = false
            }
            try {
                val status = connection.responseCode
                VpnSubscriptionDiagnostics.event(
                    "http",
                    "status=$status contentType=${safeMediaType(connection.contentType)} redirect=${redirectNumber > 0}"
                )
                when (status) {
                    in 200..299 -> {
                        val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                        if (declaredLength > MAX_BODY_BYTES) throw SubscriptionException("Subscription is larger than 5 MiB")
                        val body = connection.inputStream.use { readBounded(it, MAX_BODY_BYTES) }
                        if (body.isEmpty()) throw SubscriptionException("Subscription response is empty")
                        val text = body.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                        VpnSubscriptionDiagnostics.event("response", "bodyBytes=${body.size}")
                        return FetchedVpnSubscription(
                            configuration = text,
                            contentType = connection.contentType,
                            userInfo = connection.getHeaderField("subscription-userinfo")
                        )
                    }
                    in 300..399 -> {
                        if (redirectNumber == MAX_REDIRECTS) throw SubscriptionException("Too many subscription redirects")
                        val redirect = connection.getHeaderField("Location")
                            ?: throw SubscriptionException("Subscription redirect has no destination")
                        val next = validateUrl(URL(currentUrl, redirect).toString())
                        // Do not forward an installation ID (or a subscription URL token) to another origin.
                        if (origin(next) != originalOrigin) throw SubscriptionException("Cross-origin subscription redirects are blocked")
                        currentUrl = next
                    }
                    else -> throw SubscriptionException("Subscription server returned HTTP $status")
                }
            } catch (error: Exception) {
                VpnSubscriptionDiagnostics.failure("http", error)
                throw error
            } finally {
                connection.disconnect()
            }
        }
        throw SubscriptionException("Could not fetch subscription")
    }

    private fun validateUrl(raw: String): URL {
        val url = runCatching { URL(raw.trim()) }.getOrElse { throw SubscriptionException("Enter a valid HTTPS subscription URL") }
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null || url.port == 0) {
            throw SubscriptionException("Subscription URL must use HTTPS and must not contain user-info")
        }
        return url
    }

    private fun origin(url: URL): String = "${url.protocol.lowercase()}://${url.host.lowercase()}:${if (url.port < 0) 443 else url.port}"

    private fun safeMediaType(value: String?): String {
        val type = value?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return type.takeIf { MEDIA_TYPE.matches(it) } ?: "unknown"
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw SubscriptionException("Subscription is larger than 5 MiB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val HWID_HEADER = "X-BeyGram-HWID"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REDIRECTS = 3
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024
        private val MEDIA_TYPE = Regex("^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$")
    }
}

class SubscriptionException(message: String) : Exception(message)
