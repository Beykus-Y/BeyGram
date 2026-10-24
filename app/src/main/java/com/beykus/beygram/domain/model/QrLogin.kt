package com.beykus.beygram.domain.model

/**
 * A validated `tg://login?token=...` link from TDLib.
 *
 * The token is deliberately masked in [toString] so a debug log or a crash report can never leak a QR login secret.
 * The original link remains available through [link] for rendering and copying.
 */
data class QrLoginToken internal constructor(val value: String) {
    val link: String get() = "${QrLoginLinks.PREFIX}$value"

    override fun toString(): String = "QrLoginToken(***)"

    companion object {
        /** Returns the token when [raw] is exactly a Telegram QR login link, otherwise null. */
        fun parse(raw: String?): QrLoginToken? = QrLoginLinks.token(raw)
    }
}

object QrLoginLinks {
    const val PREFIX = "tg://login?token="
    const val MAX_TOKEN_LENGTH = 512

    // TDLib encodes the login token with base64url. Anything else (extra query parameters, whitespace,
    // percent-encoding, a different scheme) is not a link the QR screen can safely present.
    private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{1,$MAX_TOKEN_LENGTH}")

    fun token(raw: String?): QrLoginToken? {
        if (raw == null || !raw.startsWith(PREFIX)) return null
        val token = raw.substring(PREFIX.length)
        if (token.isEmpty() || !TOKEN_PATTERN.matches(token)) return null
        return QrLoginToken(token)
    }
}

sealed interface QrLoginLinkUpdate {
    /** A new link that must replace the QR code currently on screen. */
    data class Refreshed(val token: QrLoginToken) : QrLoginLinkUpdate

    /** TDLib repeated the current link; the QR code itself doesn't change. */
    data object Unchanged : QrLoginLinkUpdate

    /** The update didn't contain a usable `tg://login?token...` link. */
    data object Invalid : QrLoginLinkUpdate
}

/**
 * Pure state helper for TDLib's repeatedly refreshed QR links.
 *
 * TDLib owns token renewal: it sends `UpdateAuthorizationState` with a new link before the old one expires.
 * The tracker keeps the newest valid token, lets the UI ignore duplicate updates and reports when too much time
 * has passed without any refresh, so a stale QR code cannot leave the login screen spinning forever.
 */
class QrLoginLinkTracker {
    private var token: QrLoginToken? = null
    private var updatedAtMillis: Long = 0L

    @Synchronized
    fun accept(rawLink: String?, nowMillis: Long = System.currentTimeMillis()): QrLoginLinkUpdate {
        val parsed = QrLoginToken.parse(rawLink) ?: return QrLoginLinkUpdate.Invalid
        updatedAtMillis = nowMillis
        val previous = token
        token = parsed
        return if (previous == parsed) QrLoginLinkUpdate.Unchanged else QrLoginLinkUpdate.Refreshed(parsed)
    }

    @Synchronized
    fun current(): QrLoginToken? = token

    @Synchronized
    fun isRefreshOverdue(
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMillis: Long = QR_LOGIN_REFRESH_TIMEOUT_MS
    ): Boolean = token != null && nowMillis - updatedAtMillis >= timeoutMillis

    @Synchronized
    fun reset() {
        token = null
        updatedAtMillis = 0L
    }
}

/** A QR token normally lives for about 30 seconds; TDLib refreshes it well before this watchdog fires. */
const val QR_LOGIN_REFRESH_TIMEOUT_MS = 75_000L
