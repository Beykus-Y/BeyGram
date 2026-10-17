package com.beykus.beygram.data.vpn

import android.util.Log
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.diagnostics.ConnectionEventLog
import com.beykus.beygram.domain.UserMessageException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Subscription trace for the shareable diagnostics. Never pass a URL, header, profile value, or server body here. */
internal object VpnSubscriptionDiagnostics {
    private const val TAG = "BeyGramVpnSub"

    fun event(stage: String, details: String) {
        val safeLine = "VPN subscription $stage: $details"
        if (BuildConfig.DEBUG) Log.i(TAG, safeLine)
        ConnectionEventLog.shared.record(safeLine)
    }

    fun failure(stage: String, error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) return
        // A user-facing failure is classified by the developer exception it wraps.
        val source = (error as? UserMessageException)?.cause ?: error
        event(stage, "failed=${failureKind(source)} cause=${causeSummary(source)}")
    }

    private fun causeSummary(error: Throwable): String = generateSequence(error) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .joinToString(" <- ") { cause ->
            // The message of a UserMessageException is its resource text, which is not a detail for the report.
            val message = (if (cause is UserMessageException) "" else cause.message.orEmpty())
                .replace(URL, "[url]")
                .replace(SECRET) { "${it.groupValues[1]}=[hidden]" }
                .replace(ENDPOINT, "[endpoint]")
                .filterNot(Char::isISOControl)
                .take(MAX_CAUSE_MESSAGE_CHARS)
            cause.javaClass.simpleName + message.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
        }

    private fun failureKind(error: Throwable): String = when (error) {
        is SocketTimeoutException -> "network_timeout"
        is UnknownHostException -> "name_resolution_failure"
        is SSLException -> "secure_connection_failure"
        is SubscriptionException -> when (error.failure) {
            SubscriptionFailure.HTTP_ERROR -> "http_error"
            SubscriptionFailure.UNSUPPORTED_PROFILE -> "unsupported_profile"
            SubscriptionFailure.NO_SERVERS -> "decoded_content_no_servers"
            SubscriptionFailure.INVALID_URL -> "invalid_url"
            SubscriptionFailure.URL_POLICY_REJECTED -> "url_policy_rejected"
            SubscriptionFailure.TOO_LARGE -> "response_too_large"
            SubscriptionFailure.REJECTED -> "subscription_rejected"
        }
        is MihomoCoreException -> "core_rejected_profile"
        else -> error.javaClass.simpleName.take(48).ifBlank { "unknown_error" }
    }

    private const val MAX_CAUSE_DEPTH = 3
    private const val MAX_CAUSE_MESSAGE_CHARS = 140
    private val URL = Regex("(?i)https?://[^\\s\\\"'<>]+")
    private val ENDPOINT = Regex("(?i)(?:\\b(?:\\d{1,3}\\.){3}\\d{1,3}|\\[[0-9a-f:]+]|[a-z0-9.-]+\\.[a-z]{2,})(?::\\d{1,5})?")
    private val SECRET = Regex("(?i)\\b(password|passwd|token|secret|uuid|authorization)\\s*[:=]\\s*[^\\s,;]+")
}
