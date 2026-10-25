package com.beykus.beygram.ui.screens.chat

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.BotWebAppLaunch
import com.beykus.beygram.domain.model.BotWebAppTheme
import com.beykus.beygram.domain.model.isAllowedWebAppNavigation
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.beyGramPalette
import java.net.URI

/**
 * A separate, full-screen WebView for a Telegram Mini App. Only the https URL returned by TDLib is loaded; main-frame
 * navigation is checked against the launch URL and TDLib's same-origin flag. There is no JavaScript bridge, no file or
 * content access, no mixed content, no downloads and no permission grants. The chat underneath stays composed, so
 * closing the screen restores its draft and scroll position without reloading.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BotWebAppScreen(session: BotWebAppLaunch, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val webView = remember(session.launchId) { WebView(context) }
    var currentUrl by remember(session.launchId) { mutableStateOf(session.url) }
    var progress by remember(session.launchId) { mutableIntStateOf(0) }
    var loadError by remember(session.launchId) { mutableStateOf(false) }
    val domain = remember(currentUrl) { runCatching { URI(currentUrl).host }.getOrNull().orEmpty().ifBlank { session.host } }

    fun showBlockedNavigation() {
        Toast.makeText(context, R.string.chat_webapp_navigation_blocked, Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(webView, session) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.databaseEnabled = false
        settings.setGeolocationEnabled(false)
        settings.mediaPlaybackRequiresUserGesture = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.safeBrowsingEnabled = true
        webView.setBackgroundColor(palette.screen.toArgb())
        webView.isVerticalScrollBarEnabled = true

        fun allowMainFrame(target: String): Boolean {
            if (isAllowedWebAppNavigation(session.url, target, session.requireSameOrigin)) return true
            showBlockedNavigation()
            return false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                if (!request.isForMainFrame) {
                    // Cross-origin iframes are blocked when TDLib requires the launch origin; https-only otherwise.
                    return !isAllowedWebAppNavigation(session.url, target, session.requireSameOrigin)
                }
                return !allowMainFrame(target)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !allowMainFrame(url)

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!isAllowedWebAppNavigation(session.url, url, session.requireSameOrigin)) {
                    view.stopLoading()
                    showBlockedNavigation()
                    return
                }
                currentUrl = url
                loadError = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                currentUrl = url
                progress = 100
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) loadError = true
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                loadError = true
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) loadError = true
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                loadError = true
            }

            override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
                request.cancel()
            }

            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                handler.cancel()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Toast.makeText(context, R.string.chat_webapp_crashed, Toast.LENGTH_LONG).show()
                onClose()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }

        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(context, R.string.chat_webapp_download_blocked, Toast.LENGTH_SHORT).show()
        }
        webView.loadUrl(session.url)
        onDispose {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = WebChromeClient()
            webView.destroy()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        // Back closes the Mini App and leaves the chat underneath exactly where it was.
        Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(
                Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textPrimary)
                }
                Icon(Icons.Rounded.Link, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val fallbackTitle = stringResource(R.string.chat_webapp_title)
                Column(Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { fallbackTitle },
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        domain.ifBlank { fallbackTitle },
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { loadError = false; webView.loadUrl(session.url) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, stringResource(R.string.chat_webapp_reload), tint = palette.textSecondary)
                }
            }
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accent
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                if (loadError) {
                    Column(
                        Modifier.fillMaxSize().background(palette.screen.copy(alpha = 0.94f)).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.chat_webapp_load_failed),
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextButton(onClick = { loadError = false; webView.loadUrl(session.url) }) {
                            Text(stringResource(R.string.common_retry), color = palette.accent)
                        }
                    }
                }
            }
        }
    }
}

/** The only app data passed to TDLib is the active palette; nothing about the account or message is exposed. */
internal fun BeyGramPalette.toBotWebAppTheme() = BotWebAppTheme(
    background = screen.toArgb(),
    surface = surface.toArgb(),
    text = textPrimary.toArgb(),
    textSecondary = textSecondary.toArgb(),
    accent = accent.toArgb(),
    onAccent = onAccent.toArgb(),
    divider = divider.toArgb(),
    danger = danger.toArgb()
)
