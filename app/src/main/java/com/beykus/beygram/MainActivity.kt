package com.beykus.beygram

import android.os.Bundle
import android.content.Intent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import com.beykus.beygram.domain.model.NotificationOpenRequest
import com.beykus.beygram.notifications.NotificationIntents
import com.beykus.beygram.notifications.NotificationRuntime
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.ui.theme.AppearanceViewModel
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.data.appearance.ThemeMode

class MainActivity : ComponentActivity() {
    private var notificationRequest by mutableStateOf<NotificationOpenRequest?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("notification_pending")) notificationRequest = NotificationOpenRequest(savedInstanceState.getLong("notification_token"),
                savedInstanceState.getLong("notification_chat").takeIf { it != 0L }, savedInstanceState.getBoolean("notification_settings"), savedInstanceState.getBoolean("notification_proxy"))
        } else acceptNotificationIntent(intent)
        enableEdgeToEdge()

        setContent {
            val appearance: AppearanceViewModel = viewModel()
            // The stored choice is read asynchronously. Nothing is drawn until it is known, so the theme never flips.
            val themeMode = appearance.themeMode.collectAsState().value ?: return@setContent
            val isDarkTheme = themeMode.isDark(isSystemInDarkTheme())

            BeyGramTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeyGramApp(
                        notificationRequest = notificationRequest,
                        onNotificationHandled = { if (notificationRequest?.token == it) notificationRequest = null },
                        isDarkTheme = isDarkTheme,
                        onThemeChange = { appearance.setThemeMode(if (it) ThemeMode.Dark else ThemeMode.Light) }
                    )
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptNotificationIntent(intent) }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_pending", notificationRequest != null)
        notificationRequest?.let { outState.putLong("notification_token", it.token); outState.putLong("notification_chat", it.chatId ?: 0L); outState.putBoolean("notification_settings", it.settings); outState.putBoolean("notification_proxy", it.proxy) }
        super.onSaveInstanceState(outState)
    }
    private fun acceptNotificationIntent(intent: Intent?) {
        if (intent == null || intent.action !in listOf(NotificationIntents.OPEN_CHAT, NotificationIntents.OPEN_SETTINGS, NotificationIntents.OPEN_PROXY) || !NotificationIntents.valid(this, intent)) return
        when (intent?.action) {
            NotificationIntents.OPEN_CHAT -> {
                val chatId = intent.getLongExtra(NotificationIntents.CHAT_ID, 0L)
                if (chatId != 0L) {
                    NotificationRuntime.get(this).dismiss(intent.getIntExtra(NotificationIntents.GROUP_ID, 0), intent.getIntExtra(NotificationIntents.MAX_ID, 0))
                    notificationRequest = NotificationOpenRequest(SystemClock.elapsedRealtimeNanos(), chatId)
                }
            }
            NotificationIntents.OPEN_SETTINGS -> notificationRequest = NotificationOpenRequest(SystemClock.elapsedRealtimeNanos(), settings = true)
            NotificationIntents.OPEN_PROXY -> notificationRequest = NotificationOpenRequest(SystemClock.elapsedRealtimeNanos(), proxy = true)
        }
    }
}
