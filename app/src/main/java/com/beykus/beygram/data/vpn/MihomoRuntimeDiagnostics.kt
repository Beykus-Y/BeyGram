package com.beykus.beygram.data.vpn

import android.util.Log
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.diagnostics.ConnectionEventLog

/** Proxy lifecycle trace for the shareable diagnostics. Never records profile contents, credentials, URLs, or proxy endpoints. */
internal object MihomoRuntimeDiagnostics {
    private const val TAG = "BeyGramMihomo"

    fun event(stage: String, detail: String) {
        val line = "Proxy $stage: $detail"
        if (BuildConfig.DEBUG) Log.i(TAG, line)
        ConnectionEventLog.shared.record(line)
    }

    fun failure(stage: String, error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) return
        val known = error.message?.takeIf {
            it.startsWith("Локальный прокси не запустился") ||
                it.startsWith("Сначала загрузите подписку") || it.startsWith("Не удалось загрузить ядро прокси")
        }
        event(stage, "failed=${error.javaClass.simpleName}${known?.let { " detail=$it" }.orEmpty()}")
    }
}
