package com.beykus.beygram.data.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MihomoCoreLogCaptureTest {
    /** The shape libmihomo-android v0.3.11 sends: an action result whose data is the `{type, data}` event. */
    private fun wrapped(level: String, payload: String): String = JSONObject()
        .put("id", "")
        .put("method", "message")
        .put("code", 0)
        .put("data", JSONObject().put("type", "log").put("data", JSONObject().put("LogLevel", level).put("Payload", payload)))
        .toString()

    @Test
    fun errorInsideActionResultIsReported() {
        val line = MihomoCoreLogCapture.formatEvent(wrapped("error", "Start SOCKS server error: listen tcp 127.0.0.1:17891: bind: address already in use"))
        assertEquals("Proxy core error: Start SOCKS server error: listen tcp [endpoint]: bind: address already in use", line)
    }

    @Test
    fun warningIsReported() {
        assertEquals("Proxy core warning: dial failed", MihomoCoreLogCapture.formatEvent(wrapped("warning", "dial failed")))
    }

    @Test
    fun infoAndDebugAreSkipped() {
        assertNull(MihomoCoreLogCapture.formatEvent(wrapped("info", "[TCP] connected")))
        assertNull(MihomoCoreLogCapture.formatEvent(wrapped("debug", "details")))
    }

    @Test
    fun bareEventIsAccepted() {
        val raw = JSONObject().put("type", "log")
            .put("data", JSONObject().put("LogLevel", "error").put("Payload", "boom")).toString()
        assertEquals("Proxy core error: boom", MihomoCoreLogCapture.formatEvent(raw))
    }

    @Test
    fun otherEventTypesAreSkipped() {
        val raw = JSONObject().put("method", "message").put("code", 0)
            .put("data", JSONObject().put("type", "connections").put("data", JSONObject())).toString()
        assertNull(MihomoCoreLogCapture.formatEvent(raw))
    }

    @Test
    fun secretsAndAddressesAreRemoved() {
        val line = MihomoCoreLogCapture.formatEvent(
            wrapped("error", "proxy node.example.com:443 password=hunter2 url https://sub.example.com/token/abc")
        )!!
        assertFalse(line.contains("hunter2"))
        assertFalse(line.contains("example.com"))
        assertFalse(line.contains("abc"))
    }

    @Test
    fun malformedDataIsSkipped() {
        val raw = JSONObject().put("method", "message").put("data", JSONObject().put("type", "log").put("data", "text")).toString()
        assertNull(MihomoCoreLogCapture.formatEvent(raw))
    }
}
