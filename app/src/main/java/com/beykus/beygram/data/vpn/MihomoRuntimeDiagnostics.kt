package com.beykus.beygram.data.vpn

import android.util.Log
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.diagnostics.ConnectionEventLog

/** Dev-only lifecycle trace. Never records profile contents, credentials, URLs, or proxy endpoints. */
internal object MihomoRuntimeDiagnostics {
    private const val TAG = "BeyGramMihomo"
    private val enabled get() = BuildConfig.DEBUG && BuildConfig.FLAVOR == "dev"

    fun event(stage: String, detail: String) {
        if (!enabled) return
        val line = "Mihomo $stage: $detail"
        Log.i(TAG, line)
        ConnectionEventLog.shared.record(line)
    }

    fun failure(stage: String, error: Throwable) {
        if (!enabled || error is kotlinx.coroutines.CancellationException) return
        val known = error.message?.takeIf {
            it.startsWith("Mihomo SOCKS listener") || it.startsWith("Mihomo did not start") ||
                it.startsWith("Сначала загрузите подписку")
        }
        event(stage, "failed=${error.javaClass.simpleName}${known?.let { " detail=$it" }.orEmpty()}")
    }
}
