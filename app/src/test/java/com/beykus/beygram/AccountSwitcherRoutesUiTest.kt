package com.beykus.beygram

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.presence.LastSeenSettingsStore
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.data.settings.StealthStorage
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.data.tdlib.TdLibReadiness
import com.beykus.beygram.data.tdlib.TdLibRuntimeStatus
import com.beykus.beygram.data.unavailable.UnavailableTelegramRepository
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = BeyGramApplication::class, qualifiers = "ru-w360dp-h720dp-xhdpi")
class AccountSwitcherRoutesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<BeyGramApplication>()
    private val stealthStore = StealthSettingsStore(object : StealthStorage {
        override fun entries(): Map<String, *> = emptyMap<String, Any>()
        override fun replace(values: Map<String, Any>): Boolean = true
    })
    private var locked by mutableStateOf(false)

    @Test
    fun settingsAndChatsOverflowOpenTheSameAccountsSheet() {
        // Seed slots in Robolectric's private directory. Directory fsync is unsupported on Windows.
        val primary = TdAccountStorageLayout.LEGACY_ACCOUNT_ID
        val layout = TdAccountStorageLayout(app.filesDir)
        val accountIds = listOf(primary) + List(6) { layout.newSecondaryId() }
        val registry = app.filesDir.resolve("tdlib-accounts/.registry-v1")
        registry.writeText("beygram-accounts-v1\nactive=$primary\naccounts=${accountIds.joinToString(",")}\n")

        showApp()
        val sheetTitle = app.getString(R.string.account_switcher_title)
        val stealthTitle = app.getString(R.string.stealth_title)
        compose.onNodeWithText(app.getString(R.string.settings_title)).performTouchInput { click() }
        val accounts = app.getString(R.string.secondary_accounts_title)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(accounts))
        compose.onNodeWithText(accounts).performTouchInput { click() }
        compose.onNodeWithText(sheetTitle).assertIsDisplayed()
        compose.runOnIdle { assertFalse(stealthStore.current.noReadReceipts) }
        compose.onNodeWithText(stealthTitle).performTouchInput { click() }
        val hiddenReading = app.getString(R.string.stealth_preset_hidden_reading)
        compose.onNodeWithText(hiddenReading).performTouchInput { click() }
        compose.runOnIdle { assertTrue(stealthStore.current.noReadReceipts) }

        pressBack()
        compose.onNodeWithText(sheetTitle).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.tabs_chats)).performTouchInput { click() }
        compose.onNodeWithContentDescription(app.getString(R.string.feature_more_actions)).performTouchInput { click() }
        compose.onNodeWithText(sheetTitle).performTouchInput { click() }
        compose.onNodeWithText(sheetTitle).assertIsDisplayed()
        compose.onNode(hasText(stealthTitle) and hasText(hiddenReading)).assertIsDisplayed()

        compose.runOnIdle { locked = true }
        compose.onNodeWithText(sheetTitle).assertDoesNotExist()
    }

    private fun showApp() {
        val repository = object : TelegramRepository by UnavailableTelegramRepository() {
            override val authState = MutableStateFlow<AuthState>(AuthState.Ready)
        }
        val unavailable = TdLibRuntimeStatus.Missing("Synthetic test session")
        val vpn = VpnBackend.getInstance(
            app, repository.connectionState, app.applicationScope,
            repository::setMihomoProxyEnabled, repository::isMihomoProxyEnabled
        )
        val notificationRuntime = NotificationRuntime.get(app)
        val recordingSettings = VideoMessageSettingsStore(app)
        val lastSeenSettings = LastSeenSettingsStore(app)
        compose.setContent {
            BeyGramTheme {
                BeyGramAppContent(
                    legacyRepository = repository,
                    tdLibReadiness = TdLibReadiness(false, unavailable, unavailable),
                    appearance = AppearanceConfig(),
                    onAppearanceChange = {},
                    notificationRuntime = notificationRuntime,
                    vpnBackend = vpn,
                    recordingSettings = recordingSettings,
                    lastSeenSettings = lastSeenSettings,
                    notificationRequest = null,
                    onNotificationHandled = {},
                    incomingShare = null,
                    onIncomingShareHandled = {},
                    pendingDeepLink = null,
                    onDeepLinkBound = { _, _ -> },
                    onDeepLinkHandled = {},
                    onSecureScreenChange = {},
                    locked = locked,
                    stealthStoreForRepository = { if (it === repository) stealthStore else null }
                )
            }
        }
    }
}
