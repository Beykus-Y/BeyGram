package com.beykus.beygram

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.deeplink.PendingDeepLinkInbox
import com.beykus.beygram.deeplink.PendingTelegramDeepLink
import com.beykus.beygram.deeplink.SharedPreferencesPendingDeepLinkStorage
import com.beykus.beygram.domain.model.NotificationOpenRequest
import com.beykus.beygram.notifications.NotificationIntents
import com.beykus.beygram.notifications.NotificationRuntime
import android.net.Uri
import com.beykus.beygram.share.IncomingShare
import com.beykus.beygram.share.IncomingShareParser
import com.beykus.beygram.share.ShareSessions
import com.beykus.beygram.share.DirectShareShortcuts
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.ui.screens.chat.LocalBubbleMetrics
import com.beykus.beygram.ui.screens.chat.LocalChatWallpaper
import com.beykus.beygram.ui.screens.chat.bubbleMetricsFor
import com.beykus.beygram.ui.screens.chats.LocalChatListDensity
import com.beykus.beygram.ui.screens.chats.chatListDensityFor
import com.beykus.beygram.ui.theme.AppearanceViewModel
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.resolveTokens

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.beykus.beygram.data.settings.AppLocale.context(newBase))
    }
    private var notificationRequest by mutableStateOf<NotificationOpenRequest?>(null)
    private var incomingShare by mutableStateOf<IncomingShare?>(null)
    private val deepLinkInbox by lazy {
        PendingDeepLinkInbox(SharedPreferencesPendingDeepLinkStorage(this))
    }
    private var pendingDeepLink by mutableStateOf<PendingTelegramDeepLink?>(null)
    private var lastDeepLinkIntentData: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("notification_pending")) notificationRequest = NotificationOpenRequest(savedInstanceState.getLong("notification_token"),
                savedInstanceState.getLong("notification_chat").takeIf { it != 0L }, savedInstanceState.getBoolean("notification_settings"), savedInstanceState.getBoolean("notification_proxy"), savedInstanceState.getLong("notification_topic"),
                savedInstanceState.getString("notification_account") ?: "legacy")
            val restoredText = savedInstanceState.getString("share_text").orEmpty()
            val restoredFiles = restoredUris(savedInstanceState)
            // После фильтра файлов запрос может остаться пустым: тогда пустой диалог не показываем.
            if (savedInstanceState.containsKey("share_text") && (restoredText.isNotEmpty() || restoredFiles.isNotEmpty())) {
                incomingShare = IncomingShare(savedInstanceState.getLong("share_id"), restoredText, restoredFiles, savedInstanceState.getLong("share_chat_id"))
            }
        } else {
            acceptNotificationIntent(intent)
            acceptShareIntent(intent)
        }
        // A configuration change keeps the same intent; only a genuinely new VIEW data string is accepted again.
        lastDeepLinkIntentData = savedInstanceState?.getString("deeplink_intent_data") ?: lastDeepLinkIntentData
        val incomingLinkData = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
        val savedLinkData = savedInstanceState?.getString("deeplink_intent_data")
        // A task relaunched from Recents re-delivers its original VIEW intent with this flag. That link was already handled.
        val launchedFromHistory = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (!launchedFromHistory && (savedInstanceState == null || (incomingLinkData != null && incomingLinkData != savedLinkData))) {
            acceptDeepLinkIntent(intent)
        }
        // The link is persisted before login, so it is also restored after process death or Activity recreation.
        pendingDeepLink = deepLinkInbox.restore()
        enableEdgeToEdge()

        setContent {
            val appearance: AppearanceViewModel = viewModel()
            // The saved choice is read asynchronously. Nothing is drawn until it is known, so the theme never flips.
            val config = appearance.config.collectAsState().value ?: return@setContent
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = config.mode.isDark(systemDark)
            // The status and navigation bar icons follow the theme in use. The bars stay transparent, so the app's own colours show through.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                )
            }

            BeyGramTheme(
                darkTheme = isDarkTheme,
                tokens = resolveTokens(config, systemDark),
                messageTextSizeSp = config.messageTextSizeSp,
                messageLineSpacing = config.messageLineSpacing
            ) {
                // Bubble shape and list density come from the same saved choice as the colours.
                CompositionLocalProvider(
                    LocalBubbleMetrics provides bubbleMetricsFor(config.bubbleStyle, config.density, config.bubbleCornerRadiusDp),
                    LocalChatListDensity provides chatListDensityFor(config.density),
                    LocalChatWallpaper provides config.wallpaper
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        BeyGramApp(
                            notificationRequest = notificationRequest,
                            onNotificationHandled = { if (notificationRequest?.token == it) notificationRequest = null },
                            incomingShare = incomingShare,
                            onIncomingShareHandled = { if (incomingShare?.requestId == it) incomingShare = null; ShareSessions.release(this, it) },
                            pendingDeepLink = pendingDeepLink,
                            onDeepLinkBound = { token, accountId ->
                                pendingDeepLink = deepLinkInbox.bind(token, accountId) ?: pendingDeepLink
                            },
                            onDeepLinkHandled = { token ->
                                val current = pendingDeepLink?.takeIf { it.token == token }
                                if (current != null) {
                                    deepLinkInbox.take(
                                        token,
                                        current.accountId ?: TdAccountStorageLayout.LEGACY_ACCOUNT_ID
                                    )
                                    pendingDeepLink = deepLinkInbox.restore()
                                }
                            },
                            appearance = config,
                            onAppearanceChange = appearance::save
                        )
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptNotificationIntent(intent)
        acceptShareIntent(intent)
        acceptDeepLinkIntent(intent)
        pendingDeepLink = deepLinkInbox.restore()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_pending", notificationRequest != null)
        notificationRequest?.let { outState.putLong("notification_token", it.token); outState.putLong("notification_chat", it.chatId ?: 0L); outState.putBoolean("notification_settings", it.settings); outState.putBoolean("notification_proxy", it.proxy); outState.putLong("notification_topic", it.topicId); outState.putString("notification_account", it.accountId) }
        incomingShare?.let { outState.putLong("share_id", it.requestId); outState.putString("share_text", it.text); outState.putParcelableArrayList("share_uris", ArrayList(it.uris)); outState.putLong("share_chat_id", it.targetChatId) }
        outState.putString("deeplink_intent_data", lastDeepLinkIntentData)
        super.onSaveInstanceState(outState)
    }
    /**
     * External t.me/telegram.me/tg link. Only the parser decides whether it is known; nothing is opened here, because
     * the account may still be signed out. The pending link is applied after AuthState.Ready.
     */
    private fun acceptDeepLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // A Direct Share shortcut is handled as a chat target, never as an external Telegram link.
        if (intent.hasExtra(DirectShareShortcuts.LAUNCH_CHAT)) return
        val data = intent.dataString ?: return
        lastDeepLinkIntentData = data
        pendingDeepLink = deepLinkInbox.offer(data, SystemClock.elapsedRealtimeNanos()) ?: pendingDeepLink
    }

    /** Текст и файлы из другого приложения: сохраняем до выбора чата; отправка начнётся только после подтверждения на экране. */
    private fun acceptShareIntent(intent: Intent?) {
        // The same dynamic shortcut also appears in some launcher long-press menus:
        // with no shared content, open that chat, not an empty share dialog.
        if (intent?.action == Intent.ACTION_VIEW && intent.hasExtra(DirectShareShortcuts.LAUNCH_CHAT)) {
            val chatId = intent.getLongExtra(DirectShareShortcuts.LAUNCH_CHAT, 0L)
            // Any app can send this action, so a chat opens only while it is still a published shortcut.
            if (chatId != 0L && DirectShareShortcuts.isPublished(this, chatId)) notificationRequest = NotificationOpenRequest(
                SystemClock.elapsedRealtimeNanos(), chatId
            )
            return
        }
        val files = IncomingShareParser.checkUris(streamUris(intent), packageName)
        if (files.rejected > 0) Toast.makeText(this, R.string.share_file_rejected, Toast.LENGTH_LONG).show()
        val shared = IncomingShareParser.from(intent?.action, intent?.type, intent?.getCharSequenceExtra(Intent.EXTRA_TEXT), files.accepted, SystemClock.elapsedRealtimeNanos())
        // Чужой intent без содержимого не должен стирать уже принятый запрос.
        if (shared != null) {
            // A shortcut only preselects the review screen; actual eligibility is checked against
            // the current TDLib chat list and the user must still press Send.
            val shortcut = intent?.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
            incomingShare = shared.copy(targetChatId = DirectShareShortcuts.chatIdForShortcut(shortcut))
        }
    }

    private fun streamUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(singleStream(intent))
            Intent.ACTION_SEND_MULTIPLE -> multipleStreams(intent)
            else -> emptyList()
        }
        return IncomingShareParser.collectStreams(intent.action, streams + listOfNotNull(intent.data), clipUris(intent))
    }

    private fun clipUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    // minSdk 29: до API 33 нужен устаревший перегруженный вызов, без типа.
    @Suppress("DEPRECATION")
    private fun singleStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun multipleStreams(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()

    @Suppress("DEPRECATION")
    private fun restoredUris(bundle: Bundle): List<Uri> =
        IncomingShareParser.checkUris(
            (if (Build.VERSION.SDK_INT >= 33) bundle.getParcelableArrayList("share_uris", Uri::class.java) else bundle.getParcelableArrayList<Uri>("share_uris")).orEmpty(),
            packageName,
        ).accepted
    private fun acceptNotificationIntent(intent: Intent?) {
        if (intent == null || intent.action !in listOf(NotificationIntents.OPEN_CHAT, NotificationIntents.OPEN_SETTINGS, NotificationIntents.OPEN_PROXY) || !NotificationIntents.valid(this, intent)) return
        when (intent?.action) {
            NotificationIntents.OPEN_CHAT -> {
                val chatId = intent.getLongExtra(NotificationIntents.CHAT_ID, 0L)
                val source = NotificationIntents.accountForChatIntent(intent)
                if (chatId != 0L && source != null) {
                    val runtime = NotificationRuntime.get(this)
                    val group = intent.getIntExtra(NotificationIntents.GROUP_ID, 0)
                    val newest = intent.getIntExtra(NotificationIntents.MAX_ID, 0)
                    if (source == "legacy") runtime.dismiss(group, newest)
                    else runtime.dismissSecondary(source, group, newest)
                    notificationRequest = NotificationOpenRequest(
                        SystemClock.elapsedRealtimeNanos(), chatId,
                        topicId = intent.getLongExtra(NotificationIntents.TOPIC, 0L),
                        accountId = source
                    )
                }
            }
            NotificationIntents.OPEN_SETTINGS -> notificationRequest = NotificationOpenRequest(SystemClock.elapsedRealtimeNanos(), settings = true)
            NotificationIntents.OPEN_PROXY -> notificationRequest = NotificationOpenRequest(SystemClock.elapsedRealtimeNanos(), proxy = true)
        }
    }
}
