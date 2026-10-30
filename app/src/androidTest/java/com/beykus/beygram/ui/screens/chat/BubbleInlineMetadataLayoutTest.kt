package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Layout-level protection against text and timestamp overlap on different Android fonts. */
@RunWith(AndroidJUnit4::class)
class BubbleInlineMetadataLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(maxWidthDp: Int, bodyText: String) {
        composeRule.setContent {
            BeyGramTheme {
                Box(Modifier.width(maxWidthDp.dp)) {
                    BubbleLayout(
                        modifier = Modifier,
                        overlayMeta = false,
                        inlineMeta = true,
                        body = { Text(bodyText, fontSize = 18.sp, modifier = Modifier.testTag("body")) },
                        meta = { Text("23:02", fontSize = 12.sp, modifier = Modifier.testTag("time")) }
                    )
                }
            }
        }
    }

    @Test
    fun shortTextAndTimeAreSideBySideWithoutOverlapping() {
        render(maxWidthDp = 300, bodyText = "да")
        val text = composeRule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
        val time = composeRule.onNodeWithTag("time").fetchSemanticsNode().boundsInRoot
        assertTrue("Timestamp must start to the right of text", time.left >= text.right - 1f)
        assertTrue("Timestamp must remain near the message baseline", time.top >= text.top - 1f)
    }

    @Test
    fun insufficientWidthMovesMetadataBelowText() {
        render(maxWidthDp = 72, bodyText = "Очень длинное сообщение")
        val text = composeRule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
        val time = composeRule.onNodeWithTag("time").fetchSemanticsNode().boundsInRoot
        assertTrue("Narrow messages must not overlap with timestamps", time.top >= text.bottom - 1f)
    }
}
