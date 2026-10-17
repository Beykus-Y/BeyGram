package com.beykus.beygram.data.vpn

import android.util.Log
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.diagnostics.ConnectionEventLog
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Captures only warning/error messages from Mihomo, with endpoints and secrets removed. */
internal object MihomoCoreLogCapture {
    private const val TAG = "BeyGramMihomoCore"
    private val schemaReported = AtomicBoolean(false)
    private val endpoint = Regex("(?i)(?:\\b(?:\\d{1,3}\\.){3}\\d{1,3}|\\[[0-9a-f:]+]|[a-z0-9.-]+\\.[a-z]{2,})(?::\\d{1,5})?")
    private val url = Regex("(?i)https?://[^\\s\\\"'<>]+")
    private val secret = Regex("(?i)\\b(password|passwd|token|secret|uuid|authorization)\\s*[:=]\\s*[^\\s,;]+")

    fun onMessage(raw: String) {
        runCatching {
            val envelope = JSONObject(raw)
            val message = messageOf(envelope)
            if (schemaReported.compareAndSet(false, true)) {
                val method = envelope.optString("method").take(32)
                val type = message.optString("type").take(32)
                MihomoRuntimeDiagnostics.event("core log", "event schema method=$method type=$type")
            }
            val line = logLine(message) ?: return
            ConnectionEventLog.shared.record(line)
            if (BuildConfig.DEBUG) {
                if (line.startsWith("Proxy core error")) Log.e(TAG, line) else Log.w(TAG, line)
            }
        }
    }

    /**
     * Returns the safe report line for a warning or error event, or null for anything else.
     *
     * libmihomo-android v0.3.11 wraps every event in its action result: `{id, method: "message", code, data}`, where
     * `data` is the event `{type: "log", data: {LogLevel, Payload}}`. A bare event is accepted as well.
     */
    internal fun formatEvent(raw: String): String? = logLine(messageOf(JSONObject(raw)))

    private fun messageOf(envelope: JSONObject): JSONObject {
        val wrapped = envelope.optJSONObject("data") ?: envelope.optJSONObject("Data")
        return if (wrapped != null && (wrapped.has("type") || wrapped.has("Type"))) wrapped else envelope
    }

    private fun logLine(message: JSONObject): String? {
        val type = message.optString("type", message.optString("Type")).lowercase()
        if (type != "log") return null
        val data = message.optJSONObject("data") ?: message.optJSONObject("Data") ?: return null
        val levelValue = data.opt("LogLevel") ?: data.opt("logLevel") ?: data.opt("level") ?: data.opt("Level")
        val level = levelName(levelValue) ?: return null
        val payload = data.optString("Payload", data.optString("payload")).trim()
        if (payload.isBlank()) return null
        val safePayload = payload
            .replace(url, "[url]")
            .replace(secret) { "${it.groupValues[1]}=[hidden]" }
            .replace(endpoint, "[endpoint]")
            .filterNot(Char::isISOControl)
            .take(MAX_PAYLOAD_CHARS)
        return "Proxy core $level: $safePayload"
    }

    private fun levelName(value: Any?): String? {
        val text = value?.toString()?.lowercase()?.substringAfterLast('.') ?: return null
        return when {
            text.contains("error") || text == "3" -> "error"
            text.contains("warn") || text == "2" -> "warning"
            else -> null
        }
    }

    private const val MAX_PAYLOAD_CHARS = 220
}
