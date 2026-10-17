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
            val type = envelope.optString("type", envelope.optString("Type")).lowercase()
            val data = envelope.optJSONObject("data") ?: envelope.optJSONObject("Data")
            if (schemaReported.compareAndSet(false, true)) {
                val envelopeKeys = envelope.keys().asSequence().sorted().joinToString(",")
                val dataKeys = data?.keys()?.asSequence()?.sorted()?.joinToString(",") ?: "none"
                MihomoRuntimeDiagnostics.event("core log", "event schema type=$type envelopeKeys=$envelopeKeys dataKeys=$dataKeys")
            }
            if (!type.contains("log")) return
            data ?: return
            val levelValue = data.opt("LogLevel") ?: data.opt("logLevel") ?: data.opt("level") ?: data.opt("Level")
            val level = levelName(levelValue) ?: return
            val payload = data.optString("Payload", data.optString("payload")).trim()
            if (payload.isBlank()) return
            val safePayload = payload
                .replace(url, "[url]")
                .replace(secret) { "${it.groupValues[1]}=[hidden]" }
                .replace(endpoint, "[endpoint]")
                .filterNot(Char::isISOControl)
                .take(MAX_PAYLOAD_CHARS)
            val line = "Proxy core $level: $safePayload"
            if (BuildConfig.DEBUG) {
                if (level == "error") Log.e(TAG, line) else Log.w(TAG, line)
            }
            ConnectionEventLog.shared.record(line)
        }
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
