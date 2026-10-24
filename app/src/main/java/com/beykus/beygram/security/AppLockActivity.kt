package com.beykus.beygram.security

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.ui.theme.AppearanceViewModel
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.resolveTokens

/**
 * Full-screen, secure lock window. MainActivity stays stopped below it: Compose keeps the
 * chat composition (and its coroutine scopes) alive, but the stopped Activity cannot add
 * dialog windows above the lock. This is the security boundary for in-flight sends.
 */
class AppLockActivity : FragmentActivity() {
    private val manager by lazy { AppLockManager.get(applicationContext) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.context(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Secret audio/video must not keep playing behind the gate.
        PlaybackEngine.stopSecret()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        enableEdgeToEdge()
        // Back must not reveal MainActivity or finish the lock screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent {
            val appearance: AppearanceViewModel = viewModel()
            val config = appearance.config.collectAsState().value ?: return@setContent
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = config.mode.isDark(systemDark)
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            BeyGramTheme(
                darkTheme = isDarkTheme,
                tokens = resolveTokens(config, systemDark),
                messageTextSizeSp = config.messageTextSizeSp,
                messageLineSpacing = config.messageLineSpacing,
            ) {
                val locked by manager.locked.collectAsStateWithLifecycle()
                LaunchedEffect(locked) {
                    if (!locked) {
                        manager.unlock()
                        finish()
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppLockScreen(manager)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        manager.onActivityStarted(this)
        if (!manager.locked.value) {
            // Already unlocked (for example the lock was disabled elsewhere): finish through
            // the same path as a successful unlock so the hand-over is not treated as background.
            manager.unlock()
            finish()
        }
    }

    override fun onStop() {
        manager.onActivityStopped(this, isChangingConfigurations)
        super.onStop()
    }
}
