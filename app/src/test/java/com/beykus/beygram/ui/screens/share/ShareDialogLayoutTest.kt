package com.beykus.beygram.ui.screens.share

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.screens.chat.ForwardChatDialog
import com.beykus.beygram.ui.screens.chat.ForwardTarget
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The incoming-share recipient sheet on narrow phones: the send button and the comment field stay on screen, and the
 * composer sits below the attachment preview. The sheet is a separate window, so bounds are checked against the screen
 * size from the qualifiers instead of a root node.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 34 runs on the JDK 17 used by CI; a plain Application keeps TDLib and the native media libraries out.
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = Application::class)
class ShareDialogLayoutTest(private val widthDp: Int) {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()
    private val screenHeightDp = 640

    @Before
    fun setUp() {
        // The qualifier is per test; @Config cannot take the parameter.
        RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${screenHeightDp}dp-xhdpi")
    }

    @Test
    fun composerStaysOnScreenAndBelowThePreview() {
        val density = context.resources.displayMetrics.density
        val screenWidthPx = widthDp * density
        val screenHeightPx = screenHeightDp * density
        val longTitle = "Очень длинное название группы, которое не помещается в один ряд на узком экране"

        compose.setContent {
            BeyGramTheme {
                ForwardChatDialog(
                    chats = listOf(chat(1L, longTitle), chat(2L, "B"), chat(3L, "Работа")),
                    busy = false,
                    error = "Ошибка",
                    observeFile = { id -> MutableStateFlow(MediaFileState(id)) },
                    downloadFile = {},
                    titleOverride = context.getString(R.string.share_title),
                    previewSlot = { Text("preview") },
                    selection = listOf(ForwardTarget(1L, longTitle), ForwardTarget(2L, "B")),
                    onSelectionChange = {},
                    initialComment = "x",
                    onSend = { _, _ -> emptyList() },
                    onClose = {}
                )
            }
        }
        compose.waitForIdle()

        val send = compose.onNodeWithText(context.getString(R.string.share_send_count, 2))
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithText("preview").fetchSemanticsNode().boundsInRoot

        assertTrue("$widthDp dp: send button ends at ${send.right}, past the screen width $screenWidthPx", send.right <= screenWidthPx + 1f)
        assertTrue("$widthDp dp: send button ends at ${send.bottom}, past the screen height $screenHeightPx", send.bottom <= screenHeightPx + 1f)
        assertTrue("$widthDp dp: send button starts at ${send.top}, above the preview bottom ${preview.bottom}", send.top >= preview.bottom)

        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertTrue("$widthDp dp: no comment field found", fields.isNotEmpty())
        fields.forEach { field ->
            assertFalse(
                "$widthDp dp: a text field ends at ${field.boundsInRoot.right}, past the screen width $screenWidthPx",
                field.boundsInRoot.right > screenWidthPx + 1f
            )
        }
    }

    private fun chat(id: Long, title: String) = ChatPreview(
        id = id,
        title = title,
        initials = "C",
        lastMessage = UiText.Raw(""),
        lastMessageTime = "",
        unreadCount = 0,
        avatarColorArgb = 0xFF4477AA,
        canPost = true
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "w{0}dp")
        fun widths(): List<Array<Any>> = listOf(320, 360, 412).map { arrayOf<Any>(it) }
    }
}
