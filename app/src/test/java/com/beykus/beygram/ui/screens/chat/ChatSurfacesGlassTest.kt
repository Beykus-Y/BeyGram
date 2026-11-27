package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class ChatSurfacesGlassTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun reactionPickerConnectionBannerAndEditBannerRenderAcrossAllFiveStyles() {
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                Column {
                    DesignStyle.entries.forEach { style ->
                        BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = style))) {
                            CompositionLocalProvider(LocalChatChromeOnGlass provides (style == DesignStyle.LiquidGlass)) {
                                ChatConnectionBanner(accountId = "acc", connection = TelegramConnectionState.WaitingForNetwork)
                                MessageReactionPicker(
                                    options = listOf(ReactionOption("👍")),
                                    chosen = emptySet(),
                                    observeCustomEmoji = { MutableStateFlow(null) },
                                    observeFile = { MutableStateFlow(com.beykus.beygram.domain.model.MediaFileState(it)) },
                                    downloadFile = {},
                                    onPick = {}
                                )
                            }
                            EditBanner(
                                original = "orig-${style.name}",
                                isCaption = false,
                                count = 4,
                                limit = 100,
                                error = null,
                                onClose = {}
                            )
                        }
                    }
                }
            }
        }
        DesignStyle.entries.forEach { style ->
            rule.onNodeWithText("orig-${style.name}").assertExists()
        }
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("👍", substring = true))
            .fetchSemanticsNodes()
            .let { org.junit.Assert.assertEquals(DesignStyle.entries.size, it.size) }
    }
}
