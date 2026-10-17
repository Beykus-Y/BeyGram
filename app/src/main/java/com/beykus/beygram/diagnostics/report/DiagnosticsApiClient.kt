package com.beykus.beygram.diagnostics.report

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException

/**
 * Публичная конфигурация отправки отчётов. Ничего секретного здесь нет и быть
 * не должно: username и user id бота публичны, а токен бота и серверный pepper
 * живут только на сервере. Пустой или не-HTTPS baseUrl — функция отключена.
 */
data class DiagnosticsApiConfig(
    val baseUrl: String,
    val botUsername: String,
    val botUserId: Long
) {
    val isConfigured: Boolean get() = httpsOriginOrNull(baseUrl) != null && botUsername.isNotBlank()

    companion object {
        /** Строгий HTTPS-origin: только https, хост без userinfo; иначе null. */
        fun httpsOriginOrNull(raw: String): URL? = runCatching {
            val url = URL(raw.trim())
            if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null) return null
            URL("https", url.host, if (url.port < 0) 443 else url.port, "")
        }.getOrNull()
    }
}

/** Вид сбоя HTTPS-запроса; тексты для пользователя подбирает UI-слой. */
enum class ReportApiNetworkFailure {
    TIMEOUT,
    TLS,
    REDIRECT_REJECTED,
    MALFORMED_RESPONSE,
    ENDPOINT_NOT_CONFIGURED,

    /** POST мог дойти до сервера: исход неизвестен, безопасен только идемпотентный повтор. */
    UNKNOWN_AFTER_SEND,
    UNKNOWN,
}

/**
 * Результат запроса к API. Отдельно от значения несёт коды ошибок сервера —
 * по ним, а не по «голому» HTTP-статусу, выбирается поведение.
 */
sealed interface ReportApiOutcome<out T> {
    data class Ok<T>(val value: T) : ReportApiOutcome<T>

    data class HttpError(
        val httpStatus: Int,
        val serverCode: String?,
        val serverMessage: String?,
        val retryAfterSeconds: Int?
    ) : ReportApiOutcome<Nothing>

    data class Network(val failure: ReportApiNetworkFailure, val detail: String = "") : ReportApiOutcome<Nothing>

    /** Временный сбой: ту же попытку можно повторить с теми же байтами. */
    val isTransient: Boolean
        get() = when (this) {
            is Network -> failure == ReportApiNetworkFailure.TIMEOUT ||
                failure == ReportApiNetworkFailure.UNKNOWN_AFTER_SEND ||
                failure == ReportApiNetworkFailure.UNKNOWN
            is HttpError -> httpStatus in 500..599
            is Ok<*> -> false
        }
}

/** Транспорт API отчётов: реализация — HTTPS-клиент, в тестах — фейк. */
interface DiagnosticsReportTransport {
    fun postReport(config: DiagnosticsApiConfig, bodyBytes: ByteArray, nonce: String): ReportApiOutcome<AcceptedReport>
    fun getStatus(config: DiagnosticsApiConfig, reportId: String, statusToken: String): ReportApiOutcome<ReportStatusSnapshot>
}

/**
 * Клиент HTTPS API сервера отчётов (`POST /api/v1/bug-reports`, `GET .../{report_id}`).
 *
 * Политика транспорта:
 *  - только HTTPS с системной проверкой сертификата и имени хоста;
 *  - переадресации отключены: 3xx — сбой, bearer никогда не уходит на другой origin;
 *  - при активном локальном Mihomo-прокси запрос идёт через него — путь,
 *    который пользователь ожидает; провайдер прокси опрашивается на каждую попытку;
 *  - повторы решает координатор; клиент выполняет запросы с заранее
 *    зафиксированными байтами и заголовками.
 */
class DiagnosticsApiClient(
    private val proxyProvider: () -> Proxy? = { null },
    private val connectTimeoutMillis: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = READ_TIMEOUT_MS,
    /** Только для локальных unit-тестов с JDK-сервером; боевой код не передаёт true. */
    private val insecureHttpAllowedForTests: Boolean = false
) : DiagnosticsReportTransport {

    /**
     * POST отчёта. [bodyBytes] зафиксированы заранее: при повторе уходят те же
     * байты с тем же report_id и nonce (условие идемпотентности сервера).
     */
    override fun postReport(
        config: DiagnosticsApiConfig,
        bodyBytes: ByteArray,
        nonce: String
    ): ReportApiOutcome<AcceptedReport> = execute(config, "api/v1/bug-reports") { connection ->
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $nonce")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setFixedLengthStreamingMode(bodyBytes.size)
        var sent = false
        try {
            connection.outputStream.use { it.write(bodyBytes) }
            sent = true
        } catch (io: IOException) {
            return@execute ReportApiOutcome.Network(
                if (sent) ReportApiNetworkFailure.UNKNOWN_AFTER_SEND else classify(io),
                io.javaClass.simpleName
            )
        }
        readOutcome(connection) { json ->
            when (val parsed = json?.let { ReportJson.parseAccepted(it) }) {
                null -> ReportApiOutcome.Network(ReportApiNetworkFailure.MALFORMED_RESPONSE, "status body")
                else -> ReportApiOutcome.Ok(parsed)
            }
        }
    }

    /** GET состояния отчёта; status-токен идёт только в заголовке, не в query string. */
    override fun getStatus(
        config: DiagnosticsApiConfig,
        reportId: String,
        statusToken: String
    ): ReportApiOutcome<ReportStatusSnapshot> {
        if (!UUID_REGEX.matches(reportId)) {
            return ReportApiOutcome.Network(ReportApiNetworkFailure.MALFORMED_RESPONSE, "report_id")
        }
        return execute(config, "api/v1/bug-reports/$reportId") { connection ->
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $statusToken")
            connection.setRequestProperty("Accept", "application/json")
            readOutcome(connection) { json ->
                when (val parsed = json?.let { ReportJson.parseStatus(it) }) {
                    null -> ReportApiOutcome.Network(ReportApiNetworkFailure.MALFORMED_RESPONSE, "status body")
                    else -> ReportApiOutcome.Ok(parsed)
                }
            }
        }
    }

    // ---- Транспорт ------------------------------------------------------------

    private fun <T> execute(
        config: DiagnosticsApiConfig,
        path: String,
        request: (HttpURLConnection) -> ReportApiOutcome<T>
    ): ReportApiOutcome<T> {
        val origin: URL = DiagnosticsApiConfig.httpsOriginOrNull(config.baseUrl)
            ?: run {
                if (!insecureHttpAllowedForTests) return ReportApiOutcome.Network(ReportApiNetworkFailure.ENDPOINT_NOT_CONFIGURED)
                testHttpOriginOrNull(config.baseUrl)
                    ?: return ReportApiOutcome.Network(ReportApiNetworkFailure.ENDPOINT_NOT_CONFIGURED)
            }
        // toExternalForm без пути не содержит завершающего "/" — добавляем его явно.
        val url = URL(origin.toExternalForm().trimEnd('/') + "/" + path)
        val connection = (if (proxyProvider() != null) {
            url.openConnection(proxyProvider())
        } else {
            url.openConnection()
        }) as HttpURLConnection
        return try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            request(connection)
        } catch (io: IOException) {
            ReportApiOutcome.Network(classify(io), io.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> readOutcome(
        connection: HttpURLConnection,
        onSuccess: (Any?) -> ReportApiOutcome<T>
    ): ReportApiOutcome<T> {
        val status = try {
            connection.responseCode
        } catch (io: IOException) {
            return ReportApiOutcome.Network(classify(io), io.javaClass.simpleName)
        }
        if (status in 300..399) {
            return ReportApiOutcome.Network(ReportApiNetworkFailure.REDIRECT_REJECTED, "status=$status")
        }
        val body = readBounded(connection) ?: return ReportApiOutcome.Network(
            ReportApiNetworkFailure.MALFORMED_RESPONSE,
            "unreadable body status=$status"
        )
        val json = runCatching { ReportJson.parse(body) }.getOrNull()
        if (status in 200..299) return onSuccess(json)
        return ReportApiOutcome.HttpError(
            httpStatus = status,
            serverCode = json?.let { ReportJson.parseErrorCode(it) },
            serverMessage = json?.let { ReportJson.parseErrorMessage(it) },
            retryAfterSeconds = connection.getHeaderField("Retry-After")?.trim()?.toIntOrNull()
        )
    }

    private fun classify(error: IOException): ReportApiNetworkFailure = when (error) {
        is SocketTimeoutException -> ReportApiNetworkFailure.TIMEOUT
        is SSLException -> ReportApiNetworkFailure.TLS
        else -> ReportApiNetworkFailure.UNKNOWN
    }

    /** Ограниченное чтение тела: null, если тело пустое или больше [MAX_RESPONSE_BYTES]. */
    private fun readBounded(connection: HttpURLConnection): String? {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.use { input ->
            val output = ByteArrayOutputStream(1024)
            val buffer = ByteArray(4 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_RESPONSE_BYTES) return null
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** Локальный http-origin для тестового фейка; host ограничен loopback. */
        private fun testHttpOriginOrNull(raw: String): URL? = runCatching {
            val url = URL(raw.trim())
            val loopback = url.host == "127.0.0.1" || url.host == "localhost"
            if (url.protocol != "http" || !loopback || url.userInfo != null) return null
            URL("http", url.host, if (url.port < 0) 80 else url.port, "")
        }.getOrNull()
    }
}
