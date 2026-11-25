package com.beykus.beygram.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
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

    private fun render(store: StealthSettingsStore) {
        compose.setContent {
            BeyGramTheme { StealthSettingsScreen(store, onServerPrivacy = {}, onBack = {}) }
        }
    }

    private fun label(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

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
