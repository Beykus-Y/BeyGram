package com.beykus.beygram.domain.model

import java.net.URI

/** ARGB colours passed to TDLib so the Mini App can match the active BeyGram theme without sharing app state. */
data class BotWebAppTheme(
    val background: Int,
    val surface: Int,
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val onAccent: Int,
    val divider: Int,
    val danger: Int
)

/** Where a Web App button came from; TDLib has a separate launch call for inline-keyboard buttons. */
enum class BotWebAppSource { InlineKeyboard, KeyboardButton, InlineQueryButton }

/**
 * A short-lived TDLib Web App launch. A non-zero [launchId] must be closed with CloseWebApp when the screen leaves;
 * keyboard and inline-result buttons return a URL without a launch id.
 */
data class BotWebAppLaunch(val launchId: Long, val url: String, val requireSameOrigin: Boolean) {
    val host: String get() = normalizedWebAppUrl(url)?.let { runCatching { URI(it).host }.getOrNull() }.orEmpty()
}

/**
 * Mini Apps are only allowed over HTTPS. Credentials in the authority and non-HTTP schemes are rejected before
 * anything reaches WebView, so a malformed bot button cannot turn into a file:, intent: or javascript: navigation.
 */
fun normalizedWebAppUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.rawUserInfo != null) return null
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank()) return null
    val port = if (uri.port == -1) 443 else uri.port
    if (port !in 1..65535) return null
    return uri.toString()
}

private fun webAppOrigin(value: String): Triple<String, String, Int>? {
    val normalized = normalizedWebAppUrl(value) ?: return null
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val port = if (uri.port == -1) 443 else uri.port
    return Triple(uri.scheme.lowercase(), host, port)
}

/**
 * [requireSameOrigin] comes from TDLib's WebAppUrl. When it is set, only the launch origin may be navigated to;
 * otherwise the Web App may follow any HTTPS link, while every other scheme stays blocked.
 */
fun isAllowedWebAppNavigation(launchUrl: String, targetUrl: String, requireSameOrigin: Boolean): Boolean {
    val launch = webAppOrigin(launchUrl) ?: return false
    val target = webAppOrigin(targetUrl) ?: return false
    return !requireSameOrigin || launch == target
}
