package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.PeerAccentColor
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.ProfileMemberRole
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.screens.profile.MemberRow
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.LocalBeyGramDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
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
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xhdpi")
class PeerColorsSurfacesTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun authorColorMaintainsWcagContrastInLightAndDark() {
        val accent = PeerAccentColor(builtInId = 2, lightRgb = listOf(0xDDDDDD), darkRgb = listOf(0x222222))
        val lightText = accent.authorColor(dark = false, background = Color.White, foreground = Color.Black)
        val darkText = accent.authorColor(dark = true, background = Color(0xFF121212), foreground = Color.White)
        assertTrue(contrastRatio(lightText, Color.White) >= 4.5f)
        assertTrue(contrastRatio(darkText, Color(0xFF121212)) >= 4.5f)
    }

    @Test
    fun chatAvatarMemberRowReplyAndForwardRenderWithAndWithoutPeerAccent() {
        val accent = PeerAccentColor(builtInId = 4, lightRgb = listOf(0x267181), darkRgb = listOf(0x83C8D5))
        val chat = ChatPreview(
            id = 10L, title = "Peer Chat", initials = "PC",
            lastMessage = UiText.Raw("Hi"), lastMessageTime = "12:00",
            unreadCount = 0, avatarColorArgb = 0xFF3569A6L, accentColor = accent
        )
        val member = ProfileMember(
            userId = 20L, name = "Peer Member", role = ProfileMemberRole.Admin,
            online = true, isBot = false, tag = "Mod", photo = null,
            avatarColorArgb = 0xFF3569A6L, accentColor = accent
        )
        val reply = MessageReply(10L, 1L, UiText.Raw("Quoted"), senderName = "Peer Reply", senderAccentColor = accent)
        val forwarded = ChatMessage(1L, 10L, "Body", "12:01", false, isForwarded = true, forwardedFromName = "Public Channel", forwardedAccentColor = accent)
        val anonymousForward = ChatMessage(2L, 10L, "Body 2", "12:02", false, isForwarded = true, forwardedFromName = "Hidden Sender", forwardedAccentColor = null)

        rule.setContent {
            BeyGramTheme {
                Column {
                    ChatAvatar(chat, 40.dp, { MutableStateFlow(com.beykus.beygram.domain.model.MediaFileState(it)) }, {})
                    MemberRow(member, { MutableStateFlow(com.beykus.beygram.domain.model.MediaFileState(it)) }, {}, {})
                    ReplyQuote(reply, onClick = {})
                    ForwardedMessageHeader(forwarded)
                    ForwardedMessageHeader(anonymousForward)
                }
            }
        }
        rule.onNodeWithText("Peer Member").assertExists()
        rule.onNodeWithText("Peer Reply").assertExists()
        rule.onNodeWithText("Public Channel", substring = true).assertExists()
        rule.onNodeWithText("Hidden Sender", substring = true).assertExists()
    }

    @Test
    fun peerColorsFollowAppThemeRegardlessOfSystemTheme() {
        val accent = PeerAccentColor(builtInId = 4, lightRgb = listOf(0x267181), darkRgb = listOf(0x83C8D5))
        val reply = MessageReply(10L, 1L, UiText.Raw("Quoted"), senderName = "Peer Reply", senderAccentColor = accent)
        var observedDark: Boolean? = null
        val useDark = androidx.compose.runtime.mutableStateOf(true)

        rule.setContent {
            BeyGramTheme(darkTheme = useDark.value) {
                observedDark = LocalBeyGramDarkTheme.current
                ReplyQuote(reply, onClick = {})
            }
        }
        rule.runOnIdle {
            assertEquals(true, observedDark)
            useDark.value = false
        }
        rule.runOnIdle {
            assertEquals(false, observedDark)
        }
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
}
