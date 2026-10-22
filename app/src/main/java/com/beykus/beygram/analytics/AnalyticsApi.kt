package com.beykus.beygram.analytics

import com.beykus.beygram.diagnostics.report.DiagnosticsApiConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Исход запроса к аналитике; тексты для пользователя не нужны, решает координатор. */
internal sealed interface AnalyticsResult {
    /** 200. Для sync `updated=false` — валидный идемпотентный успех. */
    data object Ok : AnalyticsResult

    /** 401: credential или ID не совпадают с серверной записью. */
    data object Unauthorized : AnalyticsResult

    /** 422 или другая ошибка контракта: тот же payload повторять бессмысленно. */
    data object Invalid : AnalyticsResult

    /** Временный сбой: 429, 5xx, сеть или таймаут. [afterSeconds] из Retry-After, если сервер его прислал. */
    data class Retry(val afterSeconds: Long? = null) : AnalyticsResult

    /** 404 до деплоя, 3xx или невалидный origin: повторы с большим интервалом. */
    data object Unavailable : AnalyticsResult
}

/**
 * HTTPS-клиент аналитики. Не использует TDLib, прокси мессенджера и клиент отчётов, а запросы
 * не следуют за редиректами, чтобы bearer-credential не ушёл на другой origin.
 */
internal class AnalyticsApi(
    private val baseUrl: String,
    /** Только для локальных тестов с HTTP-сервером; боевой код не передаёт true. */
    private val insecureHttpAllowedForTests: Boolean = false
) {
    fun sync(credential: String, body: String): AnalyticsResult =
        send("POST", "api/v1/analytics/sync", credential, body)

    fun delete(installationId: String, credential: String): AnalyticsResult =
        send("DELETE", "api/v1/analytics/installations/$installationId", credential, null)

    private fun send(method: String, path: String, credential: String, body: String?): AnalyticsResult {
        val origin = originOrNull() ?: return AnalyticsResult.Unavailable
        val url = URL(origin.toExternalForm().trimEnd('/') + "/" + path)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $credential")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            classify(connection)
        } catch (io: IOException) {
            AnalyticsResult.Retry()
        } finally {
            connection.disconnect()
        }
    }

    private fun classify(connection: HttpURLConnection): AnalyticsResult {
        val status = connection.responseCode
        val retryAfter = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
        return when {
            status == 200 -> AnalyticsResult.Ok
            status == 401 -> AnalyticsResult.Unauthorized
            status == 422 -> AnalyticsResult.Invalid
            status == 404 || status in 300..399 -> AnalyticsResult.Unavailable
            status == 429 || status >= 500 -> AnalyticsResult.Retry(retryAfter)
            else -> AnalyticsResult.Invalid
        }
    }

    private fun originOrNull(): URL? {
        DiagnosticsApiConfig.httpsOriginOrNull(baseUrl)?.let { return it }
        if (!insecureHttpAllowedForTests) return null
        val url = runCatching { URL(baseUrl.trim()) }.getOrNull() ?: return null
        return if (url.protocol == "http" && url.host.isNotBlank() && url.userInfo == null) url else null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
