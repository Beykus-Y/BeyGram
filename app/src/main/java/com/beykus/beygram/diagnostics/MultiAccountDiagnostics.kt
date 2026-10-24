package com.beykus.beygram.diagnostics

import android.content.Context
import android.util.Log
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.data.tdlib.TdAccountLimitException
import com.beykus.beygram.data.tdlib.TdAccountSwitchFailure
import com.beykus.beygram.data.tdlib.TdSecondaryAuthException
import com.beykus.beygram.data.tdlib.TdSecondaryRouteUnavailableException
import com.beykus.beygram.data.tdlib.TdSecondarySessionLimitException
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.UserMessageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException

/**
 * Account lifecycle diagnostics on top of the existing shareable event buffers.
 *
 * Production keeps only low-volume milestones and failures. Stage-by-stage
 * traces are written for Dev builds by default and can be enabled explicitly
 * for Stable in Settings → Diagnostics. Values are restricted to opaque account
 * IDs, finite state/route names, durations and classified error categories:
 * phone numbers, codes, passwords, tokens, TDLib messages, proxy endpoints and
 * message contents must never be passed here.
 */
internal object MultiAccountDiagnostics {
    private const val TAG = "BeyGramAccounts"
    private const val PREFS = "beygram_multi_account_diagnostics"
    private const val KEY_VERBOSE = "verbose"
    private const val MAX_TOKEN = 64
    private const val MAX_VALUE = 160

    /** Keys whose values are safe to publish in the shared diagnostics report. */
    private val allowedAttrs = setOf(
        "account",
        "account_count",
        "account_index",
        "attempt",
        "busy",
        "count",
        "decision",
        "duration_ms",
        "elapsed_ms",
        "error",
        "function",
        "intent",
        "last_ok",
        "network",
        "operation",
        "outcome",
        "phase",
        "previous_phase",
        "preference",
        "ready",
        "reason",
        "repository",
        "result",
        "route",
        "route_decision",
        "route_reason",
        "sessions",
        "slots",
        "source",
        "stage",
        "tdlib",
        "trigger"
    )

    private val sequence = AtomicLong(0)
    private val accountIdPattern = Regex("a-[A-Za-z0-9-]{1,64}")
    private val numeric = Regex("\\d{1,9}")
    private val unsafeToken = Regex("[^a-z0-9_.:-]")

    @Volatile private var verbose: Boolean = BuildConfig.FLAVOR == "dev"

    fun initialize(context: Context) {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val default = BuildConfig.FLAVOR == "dev"
        if (!preferences.contains(KEY_VERBOSE)) {
            preferences.edit().putBoolean(KEY_VERBOSE, default).apply()
        }
        verbose = preferences.getBoolean(KEY_VERBOSE, default)
    }

    fun isVerbose(): Boolean = verbose

    fun setVerbose(context: Context, enabled: Boolean) {
        verbose = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VERBOSE, enabled).apply()
        milestone(
            code = "diagnostics",
            stage = "account_trace_toggled",
            accountId = "none",
            attrs = mapOf("ready" to if (enabled) "on" else "off")
        )
    }

    fun begin(
        code: String,
        accountId: String = "none",
        attrs: Map<String, String> = emptyMap()
    ): Operation {
        val operation = Operation(
            id = "op-${sequence.incrementAndGet()}",
            code = safeToken(code),
            accountId = sanitizeAccountId(accountId),
            initialAttrs = sanitizeAttributes(attrs)
        )
        if (verbose) operation.trace("begin")
        return operation
    }

    fun milestone(
        code: String,
        stage: String,
        accountId: String = "none",
        severity: Severity = Severity.INFO,
        attrs: Map<String, String> = emptyMap()
    ) {
        Operation(
            id = "op-${sequence.incrementAndGet()}",
            code = safeToken(code),
            accountId = sanitizeAccountId(accountId),
            initialAttrs = sanitizeAttributes(attrs)
        ).milestone(stage, severity = severity)
    }

    fun failure(
        code: String,
        stage: String,
        error: Throwable,
        accountId: String = "none",
        attrs: Map<String, String> = emptyMap()
    ) {
        begin(code, accountId, attrs).failure(stage, error)
    }

    /** Error classification only; the throwable message is never copied into diagnostics. */
    internal fun errorCategory(error: Throwable): String {
        val source = (error as? UserMessageException)?.cause ?: error
        return when (source) {
            is TdSecondaryRouteUnavailableException ->
                "route_unavailable:${source.reason.name}"
            is TdAccountSwitchFailure.RouteUnavailable ->
                "route_unavailable:${source.reason.name}"
            is TdAccountSwitchFailure.RoutePaused -> "route_paused"
            is TdAccountSwitchFailure.SessionLimit,
            is TdSecondarySessionLimitException -> "session_limit"
            is TdAccountSwitchFailure.AccountLimit,
            is TdAccountLimitException -> "account_limit"
            is TdAccountSwitchFailure.Timeout,
            is TimeoutCancellationException -> "timeout"
            is TdAccountSwitchFailure.NotAuthorized -> "not_authorized"
            is TdAccountSwitchFailure.Disconnected -> "disconnected"
            is TdSecondaryAuthException -> "tdlib_auth:${source.tdlibCode}"
            is CancellationException -> "cancelled"
            is SocketTimeoutException -> "network_timeout"
            is UnknownHostException -> "dns_failure"
            is ConnectException -> "connect_failure"
            is SSLException -> "tls_failure"
            else -> source.javaClass.simpleName.take(MAX_TOKEN)
        }
    }

    internal class Operation(
        val id: String,
        val code: String,
        val accountId: String,
        private val initialAttrs: Map<String, String>
    ) {
        @Volatile private var lastOk: String = "created"
        private val startedAtNanos = System.nanoTime()

        fun trace(stage: String, attrs: Map<String, String> = emptyMap()) {
            val safeStage = safeToken(stage)
            lastOk = safeStage
            if (!verbose) return
            write(safeStage, "working", attrs, Severity.DEBUG, structured = false)
        }

        fun milestone(
            stage: String,
            severity: Severity = Severity.INFO,
            attrs: Map<String, String> = emptyMap()
        ) {
            val safeStage = safeToken(stage)
            lastOk = safeStage
            write(safeStage, "ok", attrs, severity, structured = true)
        }

        fun success(stage: String = "completed", attrs: Map<String, String> = emptyMap()) {
            val safeStage = safeToken(stage)
            lastOk = safeStage
            write(safeStage, "ok", attrs, Severity.INFO, structured = true)
        }

        fun failure(
            stage: String,
            error: Throwable,
            attrs: Map<String, String> = emptyMap()
        ) {
            val safeStage = safeToken(stage)
            val category = errorCategory(error)
            val severity = if (category == "timeout" || category.startsWith("route_")) {
                Severity.ERROR
            } else {
                Severity.WARNING
            }
            write(
                stage = safeStage,
                outcome = "failed",
                attrs = attrs + mapOf(
                    "error" to category,
                    "last_ok" to lastOk,
                    "stage" to safeStage
                ),
                severity = severity,
                structured = true
            )
        }

        private fun write(
            stage: String,
            outcome: String,
            attrs: Map<String, String>,
            severity: Severity,
            structured: Boolean
        ) {
            val durationMs = ((System.nanoTime() - startedAtNanos) / 1_000_000).coerceAtLeast(0)
            val merged = initialAttrs + sanitizeAttributes(attrs)
            val line = buildString {
                append("Accounts operation=").append(code)
                append(" op=").append(id)
                append(" account=").append(accountId)
                append(" stage=").append(stage)
                append(" outcome=").append(outcome)
                append(" duration_ms=").append(durationMs)
                append(" last_ok=").append(lastOk)
                if (merged.isNotEmpty()) {
                    append(' ')
                    append(merged.entries.joinToString(" ") { (key, value) -> "$key=$value" })
                }
            }
            // Stage traces are Dev/diagnostic-only; milestones and failures stay
            // in the shareable journal so a production report still explains the result.
            if (verbose || outcome != "working") {
                ConnectionEventLog.shared.record(line)
            }
            if (verbose || severity == Severity.WARNING || severity == Severity.ERROR) {
                // android.util.Log is a stub in JVM unit tests; never let a
                // missing Android runtime break account state transitions.
                runCatching {
                    when (severity) {
                        Severity.ERROR, Severity.CRITICAL -> Log.e(TAG, line)
                        Severity.WARNING -> Log.w(TAG, line)
                        else -> Log.i(TAG, line)
                    }
                }
            }
            if (structured) {
                val structuredAttrs = (merged + mapOf(
                    "op" to id,
                    "account" to accountId,
                    "outcome" to outcome,
                    "duration_ms" to durationMs.toString(),
                    "last_ok" to lastOk
                )).entries.take(20).associate { it.key to it.value }
                StructuredEventLog.shared.record(
                    kind = EventKind.TDLIB,
                    severity = severity,
                    code = code,
                    message = stage,
                    attrs = structuredAttrs
                )
            }
        }
    }

    private fun safeToken(value: String): String =
        value.lowercase().replace(unsafeToken, "_").take(MAX_TOKEN)

    internal fun sanitizeAccountId(value: String): String = when {
        value == "legacy" || value == "none" -> value
        accountIdPattern.matches(value) -> value.take(MAX_TOKEN)
        else -> "account"
    }

    internal fun sanitizeAttributes(attrs: Map<String, String>): Map<String, String> =
        attrs.entries
            .filter { it.key in allowedAttrs }
            .associate { (key, value) -> key to safeValue(value) }

    private fun safeValue(value: String): String {
        val singleLine = value.replace(Regex("[\\r\\n\\t]"), " ").trim()
        if (numeric.matches(singleLine)) return singleLine.take(MAX_VALUE)
        return DiagnosticsRedactor.redact(singleLine).take(MAX_VALUE)
    }
}
