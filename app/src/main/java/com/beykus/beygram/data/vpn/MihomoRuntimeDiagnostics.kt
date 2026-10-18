package com.beykus.beygram.data.vpn

import android.util.Log
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.diagnostics.ConnectionEventLog
import com.beykus.beygram.diagnostics.StructuredEventLog
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.UserMessageException

/** Proxy lifecycle trace for the shareable diagnostics. Never records profile contents, credentials, URLs, or proxy endpoints. */
internal object MihomoRuntimeDiagnostics {
    private const val TAG = "BeyGramMihomo"

    fun event(stage: String, detail: String) {
        val line = "Proxy $stage: $detail"
        if (BuildConfig.DEBUG) Log.i(TAG, line)
        ConnectionEventLog.shared.record(line)
    }

    /** Structured outcome for the problem report; the text trace above keeps its own lines, so the journal does not repeat them. */
    fun outcome(code: String, text: String, severity: Severity = Severity.INFO, attrs: Map<String, String> = emptyMap()) {
        StructuredEventLog.shared.record(kind = EventKind.VPN, severity = severity, code = code, message = text, attrs = attrs)
    }

    fun failure(stage: String, error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) return
        val source = (error as? UserMessageException)?.cause ?: error
        // Only the core's own failures carry a developer detail; other failures are reported by their class.
        val known = (source as? MihomoCoreException)?.message
        event(stage, "failed=${source.javaClass.simpleName}${known?.let { " detail=$it" }.orEmpty()}")
    }
}
