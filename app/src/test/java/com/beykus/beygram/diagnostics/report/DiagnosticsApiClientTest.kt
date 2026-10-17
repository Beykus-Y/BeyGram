package com.beykus.beygram.diagnostics.report

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Тесты HTTPS-клиента на локальном JDK-сервере (loopback HTTP разрешён только
 * флагом для тестов; боевой код всегда требует https).
 */
class DiagnosticsApiClientTest {

    private lateinit var server: HttpServer
    private val requests = ConcurrentLinkedQueue<CapturedRequest>()
    private val postResponse = AtomicReference<Pair<Int, String>>()
    private val statusResponse = AtomicReference<Pair<Int, String>>()
    private val statusHeaders = AtomicReference<Map<String, List<String>>>()
    private val closeWithoutResponse = AtomicReference(false)

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, List<String>>,
        val body: ByteArray
    )

    private val client = DiagnosticsApiClient(insecureHttpAllowedForTests = true)
    private val reportId = "123e4567-e89b-42d3-a456-426614174000"

    private val config get() = DiagnosticsApiConfig(
        baseUrl = "http://127.0.0.1:${server.address.port}",
        botUsername = "BeyGram_Bot",
        botUserId = 8841599020L
    )

    private val payload = BugReportPayload(reportId = reportId, description = "описание проблемы")
    private val bodyBytes = ReportJson.encode(payload).toByteArray(StandardCharsets.UTF_8)

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/bug-reports") { exchange -> handle(exchange) }
        server.executor = null
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes()
        val headers = exchange.requestHeaders.mapValues { it.value.toList() }
        requests.add(CapturedRequest(exchange.requestMethod, exchange.requestURI.path, headers, body))
        if (closeWithoutResponse.get()) {
            exchange.close()
            return
        }
        val response = when (exchange.requestMethod) {
            "GET" -> statusResponse.get()
            else -> postResponse.get()
        } ?: (500 to """{"error":{"code":"internal_error","message":"нет сценария"}}""")
        val bytes = response.second.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        if (response.first == 429) exchange.responseHeaders.add("Retry-After", "42")
        if (response.first in 300..399) {
            exchange.responseHeaders.add("Location", "/elsewhere")
        }
        exchange.sendResponseHeaders(response.first, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    private fun captured(): CapturedRequest = requests.toList().last()

    /** JDK-сервер нормализует регистр заголовков — ищем без учёта регистра. */
    private fun CapturedRequest.header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.single()

    @Test
    fun `post sends exact bytes with bearer nonce and parses 202`() {
        postResponse.set(
            202 to """{"report_id":"$reportId","status":"queued","accepted_at":1760000000.5,
                       "status_token":"tok123","idempotent_replay":false,"redactions":{},"detail":"ок"}"""
        )
        val outcome = client.postReport(config, bodyBytes, "the-nonce-value")
        val ok = outcome as ReportApiOutcome.Ok
        assertEquals(reportId, ok.value.reportId)
        assertEquals("queued", ok.value.status)
        assertEquals("tok123", ok.value.statusToken)
        val request = captured()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/bug-reports", request.path)
        assertEquals("Bearer the-nonce-value", request.header("Authorization"))
        assertTrue(
            "content-type: ${request.headers["Content-type"]}",
            request.header("Content-Type")?.startsWith("application/json") == true
        )
        assertTrue(
            "тело должно совпасть байт в байт",
            bodyBytes.contentEquals(request.body)
        )
    }

    @Test
    fun `retry sends identical bytes and the same nonce`() {
        postResponse.set(202 to """{"report_id":"$reportId","status":"queued","accepted_at":1,"status_token":"t"}""")
        val nonce = "same-nonce"
        client.postReport(config, bodyBytes, nonce)
        client.postReport(config, bodyBytes, nonce)
        val all = requests.toList()
        assertEquals(2, all.size)
        assertTrue(all[0].body.contentEquals(all[1].body))
        assertEquals(all[0].header("Authorization"), all[1].header("Authorization"))
    }

    @Test
    fun `http errors carry the server code`() {
        listOf(
            401 to "invalid_token",
            403 to "https_required",
            409 to "report_conflict",
            413 to "body_too_large",
            422 to "payload_invalid"
        ).forEach { (status, code) ->
            requests.clear()
            postResponse.set(status to """{"error":{"code":"$code","message":"детали"}}""")
            val outcome = client.postReport(config, bodyBytes, "nonce")
            val error = outcome as? ReportApiOutcome.HttpError
            assertNotNull("ожидали HttpError, было $outcome", error)
            assertEquals(status, error!!.httpStatus)
            // JDK-реализация HttpURLConnection может не отдать тело 401; код сервера проверяем, если он доехал.
            if (error.serverCode != null) assertEquals(code, error.serverCode)
            assertFalse("4xx не ретраится", error.isTransient)
        }
    }

    @Test
    fun `rate limit exposes retry-after`() {
        postResponse.set(429 to """{"error":{"code":"rate_limited","message":"много"}}""")
        val outcome = client.postReport(config, bodyBytes, "nonce") as ReportApiOutcome.HttpError
        assertEquals(429, outcome.httpStatus)
        assertEquals(42, outcome.retryAfterSeconds)
    }

    @Test
    fun `5xx is transient`() {
        postResponse.set(503 to """{"error":{"code":"intake_disabled","message":"выключено"}}""")
        val outcome = client.postReport(config, bodyBytes, "nonce") as ReportApiOutcome.HttpError
        assertTrue(outcome.isTransient)
    }

    @Test
    fun `redirects are rejected and never followed with the bearer`() {
        postResponse.set(302 to "")
        val outcome = client.postReport(config, bodyBytes, "nonce")
        assertTrue(outcome is ReportApiOutcome.Network)
        assertEquals(ReportApiNetworkFailure.REDIRECT_REJECTED, (outcome as ReportApiOutcome.Network).failure)
        // Только один запрос: переадресация не выполнялась.
        assertEquals(1, requests.size)
    }

    @Test
    fun `malformed success body is reported as malformed`() {
        postResponse.set(202 to "not json")
        val outcome = client.postReport(config, bodyBytes, "nonce")
        assertTrue(outcome is ReportApiOutcome.Network)
        assertEquals(ReportApiNetworkFailure.MALFORMED_RESPONSE, (outcome as ReportApiOutcome.Network).failure)
    }

    @Test
    fun `connection reset after the request is treated as unknown outcome and transient`() {
        postResponse.set(202 to "{}")
        closeWithoutResponse.set(true)
        val outcome = client.postReport(config, bodyBytes, "nonce")
        assertTrue(outcome is ReportApiOutcome.Network)
        assertTrue("исход после отправки должен быть временным", outcome.isTransient)
        assertEquals(1, requests.size) // тело дошло до сервера
    }

    @Test
    fun `https to a plain port fails as a transient network error`() {
        val httpsConfig = DiagnosticsApiConfig(
            baseUrl = "https://127.0.0.1:${server.address.port}",
            botUsername = "BeyGram_Bot",
            botUserId = 8841599020L
        )
        // Обычный HTTP-сервер не отвечает на TLS-handshake: клиент получает
        // TLS-отказ или таймаут — в обоих случаях исход временный.
        val outcome = client.postReport(httpsConfig, bodyBytes, "nonce")
        assertTrue("ожидали Network, было $outcome", outcome is ReportApiOutcome.Network)
        assertTrue("отказ должен быть временным", outcome.isTransient)
    }

    @Test
    fun `plain http is rejected without the test flag`() {
        val strictClient = DiagnosticsApiClient()
        val outcome = strictClient.postReport(config, bodyBytes, "nonce")
        assertTrue(outcome is ReportApiOutcome.Network)
        assertEquals(ReportApiNetworkFailure.ENDPOINT_NOT_CONFIGURED, (outcome as ReportApiOutcome.Network).failure)
        assertEquals(0, requests.size)
    }

    @Test
    fun `status get uses bearer token in the header only`() {
        statusResponse.set(
            200 to """{"report_id":"$reportId","status":"delivered","accepted_at":1,"updated_at":2,
                       "delivered_at":3,"attempts":1,"detail":"готово"}"""
        )
        val outcome = client.getStatus(config, reportId, "status-token-42")
        val ok = outcome as ReportApiOutcome.Ok
        assertEquals("delivered", ok.value.status)
        val request = captured()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/bug-reports/$reportId", request.path)
        assertEquals("Bearer status-token-42", request.header("Authorization"))
        // Токен не передаётся в query string.
        assertFalse(request.path.contains("status-token"))
    }

    @Test
    fun `non-canonical report id is refused locally`() {
        val outcome = client.getStatus(config, "../etc", "token")
        assertTrue(outcome is ReportApiOutcome.Network)
        assertEquals(ReportApiNetworkFailure.MALFORMED_RESPONSE, (outcome as ReportApiOutcome.Network).failure)
        assertEquals(0, requests.size)
    }

    @Test
    fun `unconfigured endpoint is refused locally`() {
        val outcome = client.postReport(DiagnosticsApiConfig("", "BeyGram_Bot", 1L), bodyBytes, "nonce")
        assertTrue(outcome is ReportApiOutcome.Network)
        assertEquals(ReportApiNetworkFailure.ENDPOINT_NOT_CONFIGURED, (outcome as ReportApiOutcome.Network).failure)
    }
}
