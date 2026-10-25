package com.beykus.beygram

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.security.AppLockActivity
import com.beykus.beygram.security.AppLockManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.ui.screens.chat.LocalBubbleMetrics
import com.beykus.beygram.ui.screens.chat.LocalChatWallpaper
import com.beykus.beygram.ui.screens.chat.bubbleMetricsFor
import com.beykus.beygram.ui.screens.chats.LocalChatListDensity
import com.beykus.beygram.ui.screens.chats.chatListDensityFor
import com.beykus.beygram.ui.theme.AppearanceViewModel
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.resolveTokens

class MainActivity : FragmentActivity() {
    private val appLock by lazy { AppLockManager.get(applicationContext) }
    private var windowResumed = false
    private var screenSecure = false
    private var secretPlayback = false
    /** Bumped when onStart hides the window, so Compose shows it again after drawing the gate. */
    private var windowVisibilityRevision by mutableIntStateOf(0)
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
        // Restore the window-privacy policy before the first frame. Rotation of a secret chat
        // must not produce an unprotected frame just because the new composition has not run yet.
        screenSecure = savedInstanceState?.getBoolean("secure_screen", false) ?: false
        secretPlayback = savedInstanceState?.getBoolean("secret_playback", false) ?: false
        // Protect the very first frame: a locked cold start must not flash the task snapshot
        // of a previous, already authenticated session.
        applyWindowPrivacy()
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
            val lockManager = appLock
            val locked by lockManager.locked.collectAsStateWithLifecycle()
            val lockSettings by lockManager.store.settings.collectAsStateWithLifecycle()
            // Read the manager directly as well: onStart can lock the process and invalidate
            // composition before the StateFlow collector has delivered its value. The gate
            // fails closed on the current value instead of drawing one unlocked frame.
            val gateLocked = (locked || lockManager.locked.value) && lockSettings.isActive
            // Keeps navigation and in-flight chat work alive while a secure modal gate is shown.
            // The gate hides the content visually and from accessibility; it is not disposed.
            val appStateHolder = rememberSaveableStateHolder()
            var secureScreen by remember { mutableStateOf(false) }
            val secretPlaybackActive by produceState(initialValue = false) {
                PlaybackEngine.state
                    .map { state -> state.item?.secret == true && state.started && !state.ended && !state.failed }
                    .distinctUntilChanged()
                    .collect { value = it }
            }
            LaunchedEffect(gateLocked, lockSettings.isActive, lockSettings.hideRecents, secureScreen, secretPlaybackActive) {
                screenSecure = secureScreen
                secretPlayback = secretPlaybackActive
                applyWindowPrivacy()
            }
            // The chat composition survives the lock so in-flight sends keep their scope.
            // Stop only secret audio/video; ordinary music may continue like in a media app.
            LaunchedEffect(gateLocked) { if (gateLocked) PlaybackEngine.stopSecret() }
            val appearance: AppearanceViewModel = viewModel()
            // Reading the revision makes Compose reveal the window again after onStart hid
            // it for a locked foreground transition.
            val visibilityRevision = windowVisibilityRevision
            LaunchedEffect(visibilityRevision, gateLocked) {
                if (!gateLocked) window.decorView.visibility = View.VISIBLE
            }
            // The saved choice is read asynchronously. Nothing is drawn until it is known, so the theme never flips.
            val config = appearance.config.collectAsState().value ?: return@setContent
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = config.mode.isDark(systemDark)
            // The status and navigation bar icons follow the theme in use. The bars stay transparent, so the app's own colours show through.
            SideEffect {
                // While the gate is up, the Activity window stays hidden behind the secure
                // dialog. The chat composition keeps running (in-flight sends), but nothing
                // of it can be drawn, captured, focused or read by accessibility.
                window.decorView.visibility = if (gateLocked) View.INVISIBLE else View.VISIBLE
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
                        Box(Modifier.fillMaxSize()) {
                            // The content stays composed for the lifetime of the Activity. While
                            // locked, AppLockActivity is on top; the stopped Activity cannot
                            // add new dialog windows above it. Semantics are cleared as well so
                            // TalkBack cannot reach chat content during the hand-over.
                            Box(
                                Modifier.fillMaxSize().then(
                                    if (gateLocked) Modifier.clearAndSetSemantics {} else Modifier
                                )
                            ) {
                                appStateHolder.SaveableStateProvider("beygram") {
                                    BeyGramApp(
                                        notificationRequest = if (gateLocked) null else notificationRequest,
                                        onNotificationHandled = { if (notificationRequest?.token == it) notificationRequest = null },
                                        incomingShare = if (gateLocked) null else incomingShare,
                                        onIncomingShareHandled = { if (incomingShare?.requestId == it) incomingShare = null; ShareSessions.release(this@MainActivity, it) },
                                        pendingDeepLink = if (gateLocked) null else pendingDeepLink,
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
                                        onAppearanceChange = appearance::save,
                                        onSecureScreenChange = { secureScreen = it },
                                        locked = gateLocked
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onActivityStarted(this)
        if (appLock.locked.value && appLock.store.settings.value.isActive) {
            // Hide the stale window surface until AppLockActivity is drawn. FLAG_SECURE
            // protects snapshots; this prevents a one-frame flash of the previous chat.
            window.decorView.visibility = View.INVISIBLE
            windowVisibilityRevision++
            startLockScreen()
        }
        applyWindowPrivacy()
    }

    /** The lock Activity covers every child dialog of this Activity while it is stopped. */
    @Suppress("DEPRECATION")
    private fun startLockScreen() {
        startActivity(
            Intent(this, AppLockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        overridePendingTransition(0, 0)
    }

    /**
     * Pickers, the camera, the share sheet and links that BeyGram opens take this Activity to the
     * background, but the user did not leave BeyGram. ActivityResult launchers and startActivity both
     * reach this method. The lock screen is the only in-app launch and is not such a trip.
     */
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (intent.component?.className != AppLockActivity::class.java.name) appLock.expectExternalActivity()
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onResume() {
        super.onResume()
        windowResumed = true
        applyWindowPrivacy()
    }

    override fun onPause() {
        // Set the flag before the system can capture the task snapshot. With the lock enabled
        // the Recents card stays blank even while the user is still inside the timeout window.
        windowResumed = false
        applyWindowPrivacy()
        super.onPause()
    }

    override fun onStop() {
        appLock.onActivityStopped(this, isChangingConfigurations)
        applyWindowPrivacy()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A playing secret video note can clear the flag when it ends; restore the
        // screen-level policy as soon as the window regains focus.
        if (hasFocus) applyWindowPrivacy()
    }

    /**
     * Single place that owns the window privacy flags. The lock, a secret chat and active
     * secret playback all require FLAG_SECURE; the optional Recents setting additionally
     * hides the task snapshot while the app is in the background.
     */
    private fun applyWindowPrivacy() {
        val settings = appLock.store.settings.value
        val protectRecents = settings.isActive || settings.hideRecents
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!protectRecents)
        }
        val secure = (appLock.locked.value && settings.isActive) || screenSecure || secretPlayback ||
            (!windowResumed && protectRecents && !isChangingConfigurations)
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
        outState.putBoolean("secure_screen", screenSecure)
        outState.putBoolean("secret_playback", secretPlayback)
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
