package com.beykus.beygram.domain.model

import org.json.JSONTokener
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
 *
 * [botUserId] and [buttonText] identify the button that opened the Web App, which TDLib needs for SendWebAppData.
 */
data class BotWebAppLaunch(
    val launchId: Long,
    val url: String,
    val requireSameOrigin: Boolean,
    val botUserId: Long = 0L,
    val buttonText: String = ""
) {
    val host: String get() = normalizedWebAppUrl(url)?.let { runCatching { URI(it).host }.getOrNull() }.orEmpty()
}

/** Maximum data size accepted by Telegram's web_app_data_send / SendWebAppData flow. */
const val BOT_WEB_APP_DATA_MAX_BYTES = 4096

/** Why data from a Mini App cannot be forwarded to its bot; null means the payload is valid. */
enum class BotWebAppDataError { Empty, TooLarge, InvalidJson }

/**
 * Validates Mini App data before it reaches TDLib. The size limit is in UTF-8 bytes, as in Telegram clients. Telegram
 * allows arbitrary strings, so plain text is forwarded unchanged, while structured payloads (JSON objects, arrays and
 * quoted strings) must be complete JSON and are rejected when malformed.
 */
fun validateBotWebAppData(data: String): BotWebAppDataError? {
    if (data.isEmpty()) return BotWebAppDataError.Empty
    // UTF-8 never uses fewer bytes than UTF-16 code units, so this avoids copying an obviously oversized payload.
    if (data.length > BOT_WEB_APP_DATA_MAX_BYTES) return BotWebAppDataError.TooLarge
    if (data.toByteArray(Charsets.UTF_8).size > BOT_WEB_APP_DATA_MAX_BYTES) return BotWebAppDataError.TooLarge
    val text = data.trim()
    val structured = text.startsWith('{') || text.startsWith('[') || text.startsWith('"')
    return if (structured && !isCompleteJsonValue(text)) BotWebAppDataError.InvalidJson else null
}

private fun isCompleteJsonValue(value: String): Boolean {
    val text = value.trim()
    if (text.isEmpty()) return false
    when (text.first()) {
        't' -> return text == "true"
        'f' -> return text == "false"
        'n' -> return text == "null"
        '{', '[', '"', '-' -> Unit
        in '0'..'9' -> Unit
        else -> return false
    }
    return try {
        val tokener = JSONTokener(text)
        tokener.nextValue()
        // JSONTokener is lenient, so explicitly reject anything left after the first value.
        tokener.nextClean() == '\u0000'
    } catch (ignored: Exception) {
        false
    }
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

/**
 * Exact WebView origin rule (`https://host[:port]`) for the launch URL. The Mini App bridge is installed only for
 * this origin, so a redirect cannot inherit the bridge even when unrestricted navigation is allowed.
 */
fun webAppOriginRule(raw: String): String? {
    val (scheme, host, port) = webAppOrigin(raw) ?: return null
    return buildString {
        append(scheme)
        append("://")
        append(host)
        if (port != 443) {
            append(':')
            append(port)
        }
    }
}
