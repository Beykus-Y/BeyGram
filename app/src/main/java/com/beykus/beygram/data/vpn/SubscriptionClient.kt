package com.beykus.beygram.data.vpn

import android.os.Build
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
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
                    "download",
                    "status=$status contentType=${safeMediaType(connection.contentType)} redirect=${redirectNumber > 0}"
                )
                when (status) {
                    in 200..299 -> {
                        val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                        if (declaredLength > MAX_BODY_BYTES) throw tooLargeError()
                        val body = connection.inputStream.use { readBounded(it, MAX_BODY_BYTES) }
                        if (body.isEmpty()) throw subscriptionUserError(SubscriptionFailure.REJECTED, UiText.Res(R.string.err_vpn_sub_empty_response), "Сервер подписки вернул пустой ответ")
                        val text = body.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                        VpnSubscriptionDiagnostics.event("response", "bodyBytes=${body.size}")
                        return FetchedVpnSubscription(
                            configuration = text,
                            contentType = connection.contentType,
                            userInfo = connection.getHeaderField("subscription-userinfo")
                        )
                    }
                    in 300..399 -> {
                        if (redirectNumber == MAX_REDIRECTS) throw subscriptionUserError(SubscriptionFailure.REJECTED, UiText.Res(R.string.err_vpn_sub_too_many_redirects), "Слишком много переадресаций при загрузке подписки")
                        val redirect = connection.getHeaderField("Location")
                            ?: throw subscriptionUserError(SubscriptionFailure.REJECTED, UiText.Res(R.string.err_vpn_sub_redirect_no_address), "Сервер подписки перенаправил без адреса")
                        val next = validateUrl(URL(currentUrl, redirect).toString())
                        // Do not forward an installation ID (or a subscription URL token) to another origin.
                        if (origin(next) != originalOrigin) throw subscriptionUserError(SubscriptionFailure.REJECTED, UiText.Res(R.string.err_vpn_sub_redirect_other_site), "Подписка перенаправляет на другой сайт. Такие переадресации запрещены")
                        currentUrl = next
                    }
                    else -> throw subscriptionUserError(SubscriptionFailure.HTTP_ERROR, UiText.Res(R.string.err_vpn_sub_http_error, listOf(status)), "Сервер подписки ответил ошибкой $status")
                }
            } catch (error: Exception) {
                VpnSubscriptionDiagnostics.failure("download", error)
                throw error
            } finally {
                connection.disconnect()
            }
        }
        throw subscriptionUserError(SubscriptionFailure.REJECTED, UiText.Res(R.string.proxy_connect_subscription_load_failed), "Не удалось загрузить подписку")
    }

    private fun validateUrl(raw: String): URL {
        val url = runCatching { URL(raw.trim()) }.getOrElse {
            throw subscriptionUserError(SubscriptionFailure.INVALID_URL, UiText.Res(R.string.err_vpn_sub_invalid_url), "Введите правильную ссылку на подписку")
        }
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null || url.port == 0) {
            throw subscriptionUserError(
                SubscriptionFailure.URL_POLICY_REJECTED,
                UiText.Res(R.string.err_vpn_sub_url_policy),
                "Ссылка на подписку должна начинаться с https:// и не содержать логин и пароль"
            )
        }
        return url
    }

    private fun origin(url: URL): String = "${url.protocol.lowercase()}://${url.host.lowercase()}:${if (url.port < 0) 443 else url.port}"

    private fun safeMediaType(value: String?): String {
        val type = value?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return type.takeIf { MEDIA_TYPE.matches(it) } ?: "unknown"
    }

    private fun tooLargeError(): UserMessageException = subscriptionUserError(
        SubscriptionFailure.TOO_LARGE,
        UiText.Res(R.string.err_vpn_sub_too_large, listOf(MAX_BODY_BYTES / (1024 * 1024))),
        "Подписка слишком большая (больше 5 МБ)"
    )

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw tooLargeError()
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

/** Why a subscription was refused. Diagnostics use it to pick the report code. */
enum class SubscriptionFailure { HTTP_ERROR, UNSUPPORTED_PROFILE, NO_SERVERS, INVALID_URL, URL_POLICY_REJECTED, TOO_LARGE, REJECTED }

/** [message] is only for the diagnostics report. The user sees the text carried by [UserMessageException]. */
class SubscriptionException(val failure: SubscriptionFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

/** A subscription refusal for the user: [text] is shown on screen; [detail] and [failure] go to the diagnostics report. */
internal fun subscriptionUserError(
    failure: SubscriptionFailure,
    text: UiText,
    detail: String,
    cause: Throwable? = null
): UserMessageException = UserMessageException(text, SubscriptionException(failure, detail, cause))
