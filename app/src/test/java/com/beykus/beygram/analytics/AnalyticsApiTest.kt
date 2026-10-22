package com.beykus.beygram.analytics

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private inline fun <reified T : Any> assertIs(value: Any?): T =
    value as? T ?: throw AssertionError("Expected ${T::class.simpleName}, got $value")

/** Фейковый HTTP-сервер проверяет контракт клиента: методы, заголовки, коды ответов и отсутствие редиректов. */
class AnalyticsApiTest {
    private lateinit var server: HttpServer
    private val lastMethod = AtomicReference("")
    private val lastPath = AtomicReference("")
    private val lastAuth = AtomicReference("")
    private val lastBody = AtomicReference("")
    private var status = 200
    private var retryAfter: String? = null
    private var location: String? = null

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            lastMethod.set(exchange.requestMethod)
            lastPath.set(exchange.requestURI.path)
            lastAuth.set(exchange.requestHeaders.getFirst("Authorization").orEmpty())
            lastBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            retryAfter?.let { exchange.responseHeaders.add("Retry-After", it) }
            location?.let { exchange.responseHeaders.add("Location", it) }
            val bytes = if (status == 200) """{"registered":true,"updated":true}""".toByteArray() else ByteArray(0)
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
        status = 200
        retryAfter = null
        location = null
    }

    private fun api() = AnalyticsApi("http://127.0.0.1:${server.address.port}", insecureHttpAllowedForTests = true)

    @Test
    fun syncSendsBearerAndBodyToSyncEndpoint() {
        val credential = "c".repeat(43)
        val body = """{"installation_id":"70f6c087-fdc1-4e11-9a81-dca4b8a1bd20","revision":1}"""
        assertIs<AnalyticsResult.Ok>(api().sync(credential, body))
        assertEquals("POST", lastMethod.get())
        assertEquals("/api/v1/analytics/sync", lastPath.get())
        assertEquals("Bearer $credential", lastAuth.get())
        assertEquals(body, lastBody.get())
    }

    @Test
    fun deleteUsesInstallationPathAndSameBearer() {
        status = 200
        assertIs<AnalyticsResult.Ok>(api().delete("70f6c087-fdc1-4e11-9a81-dca4b8a1bd20", "secret"))
        assertEquals("DELETE", lastMethod.get())
        assertEquals("/api/v1/analytics/installations/70f6c087-fdc1-4e11-9a81-dca4b8a1bd20", lastPath.get())
        assertEquals("Bearer secret", lastAuth.get())
    }

    @Test
    fun unauthorizedAndInvalidAreNotRetried() {
        status = 401
        assertIs<AnalyticsResult.Unauthorized>(api().sync("c", "{}"))
        status = 422
        assertIs<AnalyticsResult.Invalid>(api().sync("c", "{}"))
    }

    @Test
    fun rateLimitKeepsRetryAfter() {
        status = 429
        retryAfter = "12"
        val result = api().sync("c", "{}")
        assertEquals(12L, assertIs<AnalyticsResult.Retry>(result).afterSeconds)
    }

    @Test
    fun serverErrorsAreTransient() {
        status = 503
        assertIs<AnalyticsResult.Retry>(api().sync("c", "{}"))
    }

    @Test
    fun missingEndpointIsUnavailable() {
        status = 404
        assertIs<AnalyticsResult.Unavailable>(api().sync("c", "{}"))
    }

    @Test
    fun redirectIsNotFollowed() {
        status = 302
        location = "http://127.0.0.1:1/elsewhere"
        assertIs<AnalyticsResult.Unavailable>(api().sync("c", "{}"))
        assertEquals("/api/v1/analytics/sync", lastPath.get())
    }

    @Test
    fun plainHttpIsRejectedOutsideTests() {
        val production = AnalyticsApi("http://127.0.0.1:${server.address.port}")
        assertIs<AnalyticsResult.Unavailable>(production.sync("c", "{}"))
        assertEquals("", lastPath.get())
    }
}
