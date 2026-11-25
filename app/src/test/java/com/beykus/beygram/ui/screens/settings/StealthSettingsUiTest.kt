package com.beykus.beygram.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.data.settings.ChatStealthMode
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.data.settings.StealthStorage
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class StealthSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickPresetPreservesExceptionsAndDelay() {
        val store = StealthSettingsStore(Storage())
        store.update { it.copy(chats = mapOf(42L to ChatStealthMode.Block), scheduledSendDelaySeconds = 60) }
        render(store)

        compose.onNodeWithText(label(R.string.stealth_preset_hidden_reading)).performClick()

        assertEquals(StealthPresetKind.HiddenReading, matchingPreset(store.current))
        assertEquals(mapOf(42L to ChatStealthMode.Block), store.current.chats)
        assertEquals(60, store.current.scheduledSendDelaySeconds)
    }

    @Test fun failedWriteCanBeRetriedFromTheSelectedPreset() {
        val storage = Storage()
        val store = StealthSettingsStore(storage)
        storage.writable = false
        render(store)
        val hidden = label(R.string.stealth_preset_hidden_reading)

        compose.onNodeWithText(hidden).performClick()
        compose.onNodeWithText(label(R.string.stealth_save_failed)).assertExists()

        storage.writable = true
        // The failed write applies the stricter preset in memory, so the title and row both show it.
        val selected = hasText(hidden) and isSelected()
        compose.onNode(selected).assertIsSelected().performScrollTo().performClick()

        compose.onNodeWithText(label(R.string.stealth_save_failed)).assertDoesNotExist()
        assertTrue(StealthSettingsStore(storage).current.noReadReceipts)
    }

    @Test fun exceptionSearchAndRemovalUseTheCurrentAccount() {
        val first = StealthSettingsStore(Storage())
        val second = StealthSettingsStore(Storage())
        first.update { it.copy(chats = mapOf(42L to ChatStealthMode.Block, 115L to ChatStealthMode.Allow)) }
        second.update { it.copy(chats = mapOf(42L to ChatStealthMode.Allow)) }
        val selectedStore = mutableStateOf(first)
        val testChat = ChatPreview(
            id = 42L, title = "Alpha chat", initials = "A", lastMessage = UiText.Raw(""),
            lastMessageTime = "", unreadCount = 0, avatarColorArgb = 0xFF336699
        )
        compose.setContent {
            BeyGramTheme {
                StealthSettingsScreen(
                    selectedStore.value,
                    onServerPrivacy = {},
                    onBack = {},
                    exceptionChats = listOf(testChat)
                )
            }
        }

        openExceptions(count = 2)
        compose.onNode(hasSetTextAction()).performTextInput("Alpha")
        compose.onNodeWithText(label(R.string.stealth_exceptions_results, 1, 2)).assertExists()
        compose.onNodeWithText("Alpha chat").performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.stealth_chat_inherit)).performClick()
        assertEquals(mapOf(115L to ChatStealthMode.Allow), first.current.chats)

        compose.runOnIdle { selectedStore.value = second }
        openExceptions(count = 1)
        compose.onNodeWithText("Alpha chat").assertExists()
        assertEquals(mapOf(42L to ChatStealthMode.Allow), second.current.chats)
    }

    @Test fun statusAndPresetsComposeInEveryDesignStyle() {
        val store = StealthSettingsStore(Storage())
        val styleState = mutableStateOf(DesignStyle.Default to false)
        compose.setContent {
            val (style, glassV2) = styleState.value
            BeyGramTheme(design = designFor(style, liquidGlassV2 = glassV2)) {
                StealthSettingsScreen(store, onServerPrivacy = {}, onBack = {})
            }
        }

        val styles = DesignStyle.entries.map { it to false } + (DesignStyle.LiquidGlass to true)
        styles.forEach { style ->
            compose.runOnIdle { styleState.value = style }
            compose.onNodeWithText(label(R.string.stealth_client_state)).assertExists()
            compose.onNodeWithText(label(R.string.stealth_preset_hidden_reading)).assertExists()
        }
    }

    private fun openExceptions(count: Int) {
        compose.onNode(hasText(label(R.string.stealth_exceptions_count, count)) and hasClickAction())
            .performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.stealth_exceptions_title)).assertExists()
    }

    private fun render(store: StealthSettingsStore) {
        compose.setContent {
            BeyGramTheme { StealthSettingsScreen(store, onServerPrivacy = {}, onBack = {}) }
        }
    }

    private fun label(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private class Storage : StealthStorage {
        private var values = emptyMap<String, Any>()
        var writable = true

        override fun entries(): Map<String, *> = values

        override fun replace(values: Map<String, Any>): Boolean {
            if (!writable) return false
            this.values = values.toMap()
            return true
        }
    }
}
