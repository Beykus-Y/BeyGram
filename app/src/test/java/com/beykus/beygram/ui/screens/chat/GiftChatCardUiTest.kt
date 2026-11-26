package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GiftChatContent
import com.beykus.beygram.domain.model.GiftChatKind
import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The gift card in the timeline (Gifts 2.0, Stage C, #1142). The card draws the same artwork as the profile gift screens;
 * these tests cover what it says, because the visible text of a drawn sticker cannot be asserted without a screenshot,
 * which belongs to the device pass (Stage E).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class GiftChatCardUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun observeFile(fileId: Int): StateFlow<MediaFileState> = MutableStateFlow(MediaFileState(fileId))

    private fun show(gift: GiftChatContent, caption: String) {
        compose.setContent {
            BeyGramTheme {
                GiftChatCard(
                    gift = gift,
                    caption = caption,
                    observeFile = ::observeFile,
                    downloadFile = {}
                )
            }
        }
    }

    @Test
    fun `a collectible gift announces its title, number and message`() {
        val gift = GiftChatContent(
            kind = GiftChatKind.Upgraded,
            title = "Plush Pepe",
            number = 42,
            emoji = "🐸",
            collectible = GiftCollectibleDetails(
                number = 42,
                symbol = null,
                backdropCenterColor = 0x112233,
                backdropEdgeColor = 0x445566,
                backdropSymbolColor = null,
                backdropTextColor = null,
                modelRarityPerMille = null,
                modelRarityTier = null
            )
        )
        val caption = "Подарок\nК подарку: с днём рождения"

        show(gift, caption)

        val expected = listOf(
            "Plush Pepe",
            context.getString(R.string.gifts_collectible_number, 42),
            caption
        ).joinToString(", ")
        compose.onNodeWithContentDescription(expected, substring = true).assertIsDisplayed()
    }

    @Test
    fun `a regular gift is described by the service text`() {
        val gift = GiftChatContent(kind = GiftChatKind.Regular, emoji = "🎁")
        val caption = "Подарок за 50 Stars"

        show(gift, caption)

        compose.onNodeWithContentDescription(caption).assertIsDisplayed()
    }

    @Test
    fun `a gift without any text falls back to the generic name`() {
        // A card always has a caption in practice; this keeps the description non-empty if a future message type does not.
        show(GiftChatContent(kind = GiftChatKind.Regular), caption = "")

        compose.onNodeWithContentDescription(context.getString(R.string.gifts_gift_generic)).assertIsDisplayed()
    }
}