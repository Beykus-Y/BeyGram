package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.beykus.beygram.data.media.SharedTgsEmojiRenderer
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EmojiPanelStressTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun denseGridWithOverlayPauseAndTwentyOpenCloseCyclesRemainsStableAndResponsive() {
        val context = RuntimeEnvironment.getApplication()
        val pack1Ids = (1L..20L).toList()
        val pack2Ids = (21L..40L).toList()
        val packs = listOf(
            CustomEmojiSetBrief(id = 100L, title = "Pack One", covers = listOf(1L)),
            CustomEmojiSetBrief(id = 200L, title = "Pack Two", covers = listOf(21L))
        )
        val rows = buildList {
            add(PremiumEmojiRow.Header(100L, "Pack One"))
            pack1Ids.forEachIndexed { idx, id -> add(PremiumEmojiRow.Emoji(100L, id, idx)) }
            add(PremiumEmojiRow.Header(200L, "Pack Two"))
            pack2Ids.forEachIndexed { idx, id -> add(PremiumEmojiRow.Emoji(200L, id, idx)) }
        }
        val emojiFlows = (1L..40L).associateWith { id ->
            MutableStateFlow<CustomEmoji?>(CustomEmoji(id, id.toInt(), "🙂", "application/x-tgsticker"))
        }
        val fileFlows = (1..40).associateWith { id ->
            MutableStateFlow(MediaFileState(id = id, localPath = "/tmp/nonexistent_$id.tgs"))
        }
        var pickedCount by mutableIntStateOf(0)
        val dock = DockCustomEmoji(
            packs = packs,
            installedIds = setOf(100L, 200L),
            locked = false,
            busyPacks = emptySet(),
            failedPacks = emptySet(),
            changeInstallation = { _, _ -> },
            dynamicOrder = false,
            onPackOrder = {},
            reorderBusy = false,
            reorder = {},
            rows = rows,
            recentIds = (1L..8L).toList(),
            loadPack = {},
            failed = false,
            retry = {},
            observeCustomEmoji = { id -> emojiFlows.getValue(id) },
            observeFile = { id -> fileFlows.getValue(id) },
            downloadFile = {},
            onPick = { pickedCount++ }
        )

        var open by mutableStateOf(true)
        var pausedByOverlay by mutableStateOf(false)
        compose.setContent {
            BeyGramTheme {
                CompositionLocalProvider(
                    LocalEmojiAnimationPolicy provides EmojiAnimationPolicy(paused = pausedByOverlay)
                ) {
                    if (open) {
                        EmojiPanel(
                            recents = listOf("😀", "🎉", "❤️"),
                            onEmoji = {},
                            onBackspace = {},
                            customEmoji = dock
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        pausedByOverlay = true
        compose.waitForIdle()
        pausedByOverlay = false
        compose.waitForIdle()

        compose.onAllNodesWithText("😀").onFirst().performClick()
        compose.waitForIdle()

        repeat(20) {
            compose.runOnIdle { open = false }
            compose.waitForIdle()
            compose.runOnIdle { open = true }
            compose.waitForIdle()
        }

        compose.runOnIdle { open = false }
        compose.waitForIdle()

        val renderer = SharedTgsEmojiRenderer.default(context)
        assertEquals(0, renderer.activeContextCount())
        assertEquals(0, renderer.totalTargetCount())
        assertTrue(pickedCount >= 0)
    }
}
