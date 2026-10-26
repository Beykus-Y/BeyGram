package com.beykus.beygram.domain.model

/** Telegram allows legacy one-letter usernames, so the public lookup must not apply the 5-character profile rule. */
private val PUBLIC_USERNAME = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")

/**
 * Normalizes what a public-chat lookup receives: a bare username or an @username.
 * Returns null for anything that is not a single Telegram username, so a malformed value never reaches TDLib.
 */
fun normalizedPublicUsername(raw: String): String? {
    val value = raw.trim().removePrefix("@")
    return value.takeIf { PUBLIC_USERNAME.matches(it) }
}
