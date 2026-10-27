package com.beykus.beygram.ui.screens.chat

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.beykus.beygram.domain.model.BotWebAppTheme
import com.beykus.beygram.domain.model.isAllowedWebAppNavigation
import com.beykus.beygram.domain.model.webAppOriginRule
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/** The object name expected by telegram-web-app.js. It is provided by a fixed shim, not by addJavascriptInterface. */
internal const val TELEGRAM_WEB_APP_BRIDGE_NAME = "TelegramWebviewProxy"
private const val TELEGRAM_WEB_APP_MESSAGE_OBJECT = "TelegramWebviewProxyMessage"

private const val MAX_EVENT_TYPE_CHARS = 128
private const val MAX_EVENT_DATA_CHARS = 64 * 1024
private const val MAX_ENVELOPE_CHARS = 96 * 1024
private const val MAX_MAIN_BUTTON_TEXT_CHARS = 64

/** A deliberately small subset of the Telegram WebApp protocol; anything else is ignored. */
internal sealed interface TelegramWebAppEvent {
    data object Ready : TelegramWebAppEvent
    data object Close : TelegramWebAppEvent
    data object Expand : TelegramWebAppEvent
    data object RequestTheme : TelegramWebAppEvent
    data class SetupMainButton(val button: TelegramWebAppMainButton) : TelegramWebAppEvent
    data class SetupBackButton(val visible: Boolean) : TelegramWebAppEvent
    data class SendData(val data: String) : TelegramWebAppEvent
    data class OpenInvoice(val slug: String) : TelegramWebAppEvent
}

internal data class TelegramWebAppMainButton(
    val visible: Boolean = false,
    val active: Boolean = false,
    val text: String = "",
    val color: Int? = null,
    val textColor: Int? = null,
    val progressVisible: Boolean = false
)

/**
 * A strict parser for TelegramWebviewProxy.postEvent payloads. telegram-web-app.js JSON-encodes the second argument,
 * so object events arrive as JSON objects and sendData arrives as a JSON string (or as {data: "..."} in newer SDKs).
 * Malformed, oversized and unknown events return null instead of reaching the UI or TDLib.
 */
internal fun parseTelegramWebAppEvent(eventType: String, eventData: String?): TelegramWebAppEvent? {
    if (eventType.isEmpty() || eventType.length > MAX_EVENT_TYPE_CHARS) return null
    val raw = eventData.orEmpty()
    if (raw.length > MAX_EVENT_DATA_CHARS) return null
    return when (eventType) {
        "web_app_ready" -> TelegramWebAppEvent.Ready
        "web_app_close" -> TelegramWebAppEvent.Close
        "web_app_expand" -> TelegramWebAppEvent.Expand
        "web_app_request_theme" -> TelegramWebAppEvent.RequestTheme
        "web_app_setup_main_button" -> parseMainButton(raw)
        "web_app_setup_back_button" -> parseBackButton(raw)
        "web_app_send_data", "web_app_data_send" -> parseSendData(raw)
        // Both the Telegram event name and the JS method spelling are accepted; no invoice is ever opened here.
        "web_app_open_invoice", "openInvoice" -> parseInvoice(raw)
        else -> null
    }
}

internal fun parseWebAppColor(raw: String?): Int? {
    val value = raw?.trim().orEmpty()
    val hex = when {
        value.length == 7 && value.startsWith('#') -> value.substring(1)
        value.length == 4 && value.startsWith('#') -> value.substring(1).map { "$it$it" }.joinToString("")
        else -> return null
    }
    if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    return 0xFF000000.toInt() or hex.toInt(16)
}

/** JSON accepted by telegram-web-app.js receiveEvent('theme_changed', ...). Only local palette ARGB values are used. */
internal fun BotWebAppTheme.toWebAppThemeChangedJson(): String = JSONObject().apply {
    put("theme_params", JSONObject().apply {
        put("bg_color", background.webAppHex())
        put("secondary_bg_color", surface.webAppHex())
        put("text_color", text.webAppHex())
        put("hint_color", textSecondary.webAppHex())
        put("link_color", accent.webAppHex())
        put("button_color", accent.webAppHex())
        put("button_text_color", onAccent.webAppHex())
        put("header_bg_color", surface.webAppHex())
        put("bottom_bar_bg_color", surface.webAppHex())
        put("accent_text_color", accent.webAppHex())
        put("section_bg_color", surface.webAppHex())
        put("section_header_text_color", accent.webAppHex())
        put("section_separator_color", divider.webAppHex())
        put("subtitle_text_color", textSecondary.webAppHex())
        put("destructive_text_color", danger.webAppHex())
    })
}.toString()

private fun Int.webAppHex(): String = String.format(Locale.US, "#%06x", this and 0xFFFFFF)

private fun parseMainButton(raw: String): TelegramWebAppEvent.SetupMainButton? {
    val info = decodeJsonObject(raw) ?: return null
    val text = (info.opt("text") as? String).orEmpty().trim().take(MAX_MAIN_BUTTON_TEXT_CHARS)
    val visible = info.optBoolean("is_visible", false) && text.isNotEmpty()
    return TelegramWebAppEvent.SetupMainButton(
        TelegramWebAppMainButton(
            visible = visible,
            active = info.optBoolean("is_active", false),
            text = text,
            color = parseWebAppColor(info.opt("color") as? String),
            textColor = parseWebAppColor(info.opt("text_color") as? String),
            progressVisible = visible && info.optBoolean("is_progress_visible", false)
        )
    )
}

private fun parseBackButton(raw: String): TelegramWebAppEvent.SetupBackButton? {
    val info = decodeJsonObject(raw) ?: return null
    return TelegramWebAppEvent.SetupBackButton(info.optBoolean("is_visible", false))
}

private fun parseSendData(raw: String): TelegramWebAppEvent.SendData? {
    val value = decodeJsonValue(raw) ?: return null
    val data = when (value) {
        is String -> value
        is JSONObject -> if (value.has("data")) {
            value.opt("data") as? String ?: return null
        } else {
            // Older clients may pass the payload object itself, without the {data: "..."} wrapper.
            value.toString()
        }
        is JSONArray -> value.toString()
        JSONObject.NULL -> "null"
        else -> value.toString()
    }
    return TelegramWebAppEvent.SendData(data)
}

private fun parseInvoice(raw: String): TelegramWebAppEvent.OpenInvoice? {
    val value = decodeJsonValue(raw) ?: return null
    val slug = when (value) {
        is JSONObject -> value.opt("slug") as? String
        is String -> value
        else -> null
    } ?: return null
    if (!INVOICE_SLUG.matches(slug)) return null
    return TelegramWebAppEvent.OpenInvoice(slug)
}

private fun decodeJsonObject(raw: String): JSONObject? {
    val value = decodeJsonValue(raw) ?: return null
    return when (value) {
        is JSONObject -> value
        // Tolerate one extra string encoding, which some wrappers produce for object events.
        is String -> decodeJsonValue(value) as? JSONObject
        else -> null
    }
}

private fun decodeJsonValue(raw: String): Any? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    when (text.first()) {
        't' -> if (text != "true") return null
        'f' -> if (text != "false") return null
        'n' -> if (text != "null") return null
        '{', '[', '"', '-' -> Unit
        in '0'..'9' -> Unit
        else -> return null
    }
    return try {
        val tokener = JSONTokener(text)
        val value = tokener.nextValue()
        if (tokener.nextClean() == '\u0000') value else null
    } catch (ignored: Exception) {
        null
    }
}

private val INVOICE_SLUG = Regex("[A-Za-z0-9_=\\-]{1,256}")

/**
 * Origin-bound WebApp bridge. A fixed document-start shim exposes TelegramWebviewProxy.postEvent and forwards the
 * payload through WebMessageCompat. Only the launch origin may talk to it, and only the main frame is accepted, so a
 * cross-origin iframe cannot use the bridge. The listener is intentionally fail-closed when WebView lacks the APIs.
 */
internal class TelegramWebAppBridge(
    private val webView: WebView,
    private val launchUrl: String,
    private val themeJson: () -> String,
    private val onEvent: (TelegramWebAppEvent) -> Unit
) {
    private var scriptHandler: ScriptHandler? = null
    private var available = false
    private var listenerInstalled = false

    @Volatile
    private var documentUrl: String? = null

    @Volatile
    private var documentGeneration = 0L

    fun install() {
        if (available) return
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            ) {
                return
            }
            val originRule = webAppOriginRule(launchUrl) ?: return
            val allowedOrigins = setOf(originRule)
            if (!listenerInstalled) {
                WebViewCompat.addWebMessageListener(webView, TELEGRAM_WEB_APP_MESSAGE_OBJECT, allowedOrigins) { view, message, sourceOrigin, isMainFrame, _ ->
                    if (view === webView && isMainFrame) handleMessage(message, sourceOrigin)
                }
                listenerInstalled = true
            }
            if (scriptHandler == null) {
                scriptHandler = WebViewCompat.addDocumentStartJavaScript(webView, BRIDGE_SHIM, allowedOrigins)
            }
            documentUrl = launchUrl
            available = true
        } catch (ignored: Exception) {
            // The bridge is optional; an unsupported or broken WebView must leave the Mini App usable without it.
            available = false
            documentUrl = null
            runCatching { scriptHandler?.remove() }
            scriptHandler = null
            if (listenerInstalled) {
                runCatching { WebViewCompat.removeWebMessageListener(webView, TELEGRAM_WEB_APP_MESSAGE_OBJECT) }
                listenerInstalled = false
            }
        }
    }

    /** Every main-frame load invalidates callbacks queued by the previous document. */
    fun onPageStarted(url: String?) {
        documentUrl = url
        documentGeneration++
    }

    /** Keeps the trusted document in sync if a WebView version skips onPageStarted for a restored page. */
    fun onPageFinished(url: String?) {
        if (url != null) documentUrl = url
    }

    fun dispose() {
        documentGeneration++
        available = false
        documentUrl = null
        runCatching { scriptHandler?.remove() }
        scriptHandler = null
        if (listenerInstalled) {
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.removeWebMessageListener(webView, TELEGRAM_WEB_APP_MESSAGE_OBJECT)
                }
            }
            listenerInstalled = false
        }
    }

    fun sendMainButtonPressed() {
        sendEvent("main_button_pressed")
    }

    fun sendBackButtonPressed() {
        sendEvent("back_button_pressed")
    }

    private fun handleMessage(message: WebMessageCompat, sourceOrigin: Uri) {
        val generation = documentGeneration
        if (message.type != WebMessageCompat.TYPE_STRING) return
        if (!isAllowedWebAppNavigation(launchUrl, sourceOrigin.toString(), requireSameOrigin = true)) return
        val envelopeText = message.data ?: return
        if (envelopeText.length > MAX_ENVELOPE_CHARS) return
        val envelope = try {
            JSONObject(envelopeText)
        } catch (ignored: Exception) {
            return
        }
        val eventType = envelope.opt("eventType") as? String ?: return
        val eventData = envelope.opt("eventData") as? String
        val event = parseTelegramWebAppEvent(eventType, eventData) ?: return
        webView.post {
            if (generation != documentGeneration || !isTrustedDocument()) return@post
            when (event) {
                TelegramWebAppEvent.RequestTheme -> sendTheme()
                else -> onEvent(event)
            }
        }
    }

    private fun sendTheme() {
        val payload = try {
            themeJson()
        } catch (ignored: Exception) {
            return
        }
        if (payload.isNotEmpty()) sendEvent("theme_changed", payload)
    }

    private fun sendEvent(event: String, payload: String = "null") {
        if (!available || event !in NATIVE_EVENTS) return
        if (!isTrustedDocument()) return
        val script = "(function(){try{var t=window.Telegram&&window.Telegram.WebView;" +
            "if(t&&typeof t.receiveEvent==='function'){t.receiveEvent('" + event + "'," + payload + ");}}catch(e){}})();"
        runCatching { webView.evaluateJavascript(script, null) }
    }

    private fun isTrustedDocument(): Boolean =
        documentUrl?.let { isAllowedWebAppNavigation(launchUrl, it, requireSameOrigin = true) } == true

    private companion object {
        val NATIVE_EVENTS = setOf("theme_changed", "main_button_pressed", "back_button_pressed")

        /**
         * The only script this app injects. It contains no data from the bot and recreates the standard JS interface
         * on top of the origin-restricted WebMessageListener.
         */
        val BRIDGE_SHIM = """
            (function() {
              window.$TELEGRAM_WEB_APP_BRIDGE_NAME = {
                postEvent: function(eventType, eventData) {
                  try {
                    window.$TELEGRAM_WEB_APP_MESSAGE_OBJECT.postMessage(JSON.stringify({
                      eventType: eventType,
                      eventData: eventData
                    }));
                  } catch (e) {}
                }
              };
            })();
        """.trimIndent()
    }
}
