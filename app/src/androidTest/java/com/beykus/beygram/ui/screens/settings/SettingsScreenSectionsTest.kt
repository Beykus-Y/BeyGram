package com.beykus.beygram.ui.screens.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenSectionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun allFourteenRoutesAreAccessibleAfterGrouping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opened = mutableListOf<String>()
        composeRule.setContent {
            BeyGramTheme {
                SettingsScreen(
                    onNavigationSettings = { opened += "navigation" },
                    onAppearance = { opened += "appearance" },
                    onLastSeen = { opened += "lastSeen" },
                    onNotifications = { opened += "notifications" },
                    onStorage = { opened += "storage" },
                    onAutoDownload = { opened += "autoDownload" },
                    onProxy = { opened += "proxy" },
                    onVideoRecording = { opened += "video" },
                    onDiagnostics = { opened += "diagnostics" },
                    onUpdates = { opened += "updates" },
                    onLanguage = { opened += "language" },
                    onSessions = { opened += "sessions" },
                    onPrivacy = { opened += "privacy" },
                    onAccountSecurity = { opened += "accountSecurity" },
                    onSecondaryAccounts = { opened += "secondaryAccounts" },
                )
            }
        }
        listOf(
            R.string.settings_group_interface,
            R.string.settings_group_devices_privacy,
            R.string.settings_group_network,
            R.string.settings_group_data_diagnostics,
            R.string.settings_group_updates
        ).forEach {
            val heading = SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit)
            composeRule.onNode(hasText(context.getString(it)) and heading).assertExists()
        }
        val actions = listOf(
            R.string.feature_language to "language",
            R.string.nav_title to "navigation",
            R.string.theme_title to "appearance",
            R.string.settings_video_recording_title to "video",
            R.string.notif_title to "notifications",
            R.string.account_security_title to "accountSecurity",
            R.string.privacy_title to "privacy",
            R.string.secondary_accounts_title to "secondaryAccounts",
            R.string.feature_sessions to "sessions",
            R.string.settings_last_seen_title to "lastSeen",
            R.string.start_proxy_settings to "proxy",
            R.string.settings_storage_title to "storage",
            R.string.settings_auto_download_title to "autoDownload",
            R.string.diagnostics_title to "diagnostics",
            R.string.updates_title to "updates"
        )
        actions.forEach { (title, _) ->
            composeRule.onNode(hasText(context.getString(title)) and hasClickAction())
                .performScrollTo().performClick()
        }
        composeRule.runOnIdle { assertEquals(actions.map { it.second }, opened) }
    }
}
