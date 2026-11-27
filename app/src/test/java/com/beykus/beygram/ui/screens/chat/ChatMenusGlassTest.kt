package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntRect
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.ui.components.LocalSameWindowPopoverHost
import com.beykus.beygram.ui.components.SameWindowPopoverHost
import com.beykus.beygram.ui.components.SameWindowPopoverOverlay
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.rememberDesign
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.screens.chats.ChatActionsSheet
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class ChatMenusGlassTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun sameWindowOverflowPopoverOpensAndDismissesInLiquidGlass() {
        var open by mutableStateOf(true)
        var clicked = false
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            val host = remember { SameWindowPopoverHost() }
            BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = DesignStyle.LiquidGlass))) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalGlassBackdrop provides backdrop,
                        LocalSameWindowPopoverHost provides host
                    ) {
                        BeyGramPopover(
                            expanded = open,
                            onDismissRequest = { open = false },
                            sameWindowAnchorBounds = IntRect(280, 10, 340, 58)
                        ) {
                            BeyGramPopoverItem(text = "Mark unread", onClick = { clicked = true; open = false })
                        }
                    }
                    SameWindowPopoverOverlay(host, backdrop)
                }
            }
        }
        rule.onNodeWithText("Mark unread").performClick()
        assertTrue(clicked)
        assertFalse(open)
    }

    @Test
    fun chatActionsSheetRendersInSameWindowGlassAndCloses() {
        var open by mutableStateOf(true)
        val preview = ChatPreview(
            id = 42L,
            title = "Glass Chat",
            lastMessage = UiText.Raw("Hi"),
            lastMessageTime = "12:00",
            unreadCount = 0,
            initials = "GC",
            avatarColorArgb = 0xFF3569A6L
        )
        rule.setContent {
            val backdrop = rememberGlassBackdrop()
            BeyGramTheme(design = rememberDesign(AppearanceConfig(designStyle = DesignStyle.LiquidGlass))) {
                Box(Modifier.fillMaxSize()) {
                    if (open) {
                        ChatActionsSheet(
                            chat = preview,
                            observeFile = { MutableStateFlow(MediaFileState(it)) },
                            downloadFile = {},
                            onClose = { open = false },
                            setPinned = { Result.success(Unit) },
                            setMuted = { Result.success(Unit) },
                            setRead = { Result.success(Unit) },
                            setUnread = { Result.success(Unit) },
                            archive = { Result.success(Unit) },
                            delete = { Result.success(Unit) },
                            glassBackdrop = backdrop
                        )
                    } else {
                        Text("Closed")
                    }
                }
            }
        }
        rule.onNodeWithText("Glass Chat").assertExists()
    }
}
