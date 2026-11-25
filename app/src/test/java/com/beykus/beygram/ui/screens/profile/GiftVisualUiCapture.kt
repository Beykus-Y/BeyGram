package com.beykus.beygram.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.GiftRarityTier
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-rRU-w411dp-h891dp-xhdpi", application = Application::class)
class GiftVisualUiCapture {
    @get:Rule
    val compose = createComposeRule()

    private val collectibleGift = ReceivedGiftItem(
        id = "g-collectible-1",
        senderName = "Анна Смирнова",
        dateSeconds = 1_732_500_000,
        emoji = "\uD83D\uDC38",
        starCount = 500L,
        name = "Plush Pepe",
        text = "С днём рождения!",
        isPrivate = false,
        isSaved = true,
        isUpgraded = true,
        wasRefunded = false,
        isPinned = true,
        collectible = GiftCollectibleDetails(
            number = 42,
            symbol = null,
            backdropCenterColor = 0x264B7A,
            backdropEdgeColor = 0x102038,
            backdropSymbolColor = 0x8BB8FF,
            backdropTextColor = 0xFFFFFF,
            modelRarityPerMille = 15,
            modelRarityTier = GiftRarityTier.Rare,
            slug = "PlushPepe-42",
            title = "Plush Pepe",
            totalUpgradedCount = 1200,
            maxUpgradedCount = 5000,
            modelName = "Emerald Frog",
            symbolName = "Crown",
            symbolRarityTier = GiftRarityTier.Epic,
            backdropName = "Midnight Blue",
            backdropRarityTier = GiftRarityTier.Uncommon
        )
    )

    private val regularGift = ReceivedGiftItem(
        id = "g-regular-2",
        senderName = "Максим Петров",
        dateSeconds = 1_732_400_000,
        emoji = "\uD83C\uDF81",
        starCount = 50L,
        name = "",
        text = "Спасибо за помощь!",
        isPrivate = false,
        isSaved = true,
        isUpgraded = false,
        wasRefunded = false
    )

    @Test
    fun captureGiftRowsAndDetailLightAndDark() {
        val destination = System.getenv("BEYGRAM_GIFT_CAPTURE_DIR")
        assumeNotNull(destination)
        val output = File(checkNotNull(destination)).apply { mkdirs() }
        val dark = mutableStateOf(false)
        val showDetail = mutableStateOf(false)

        compose.setContent {
            BeyGramTheme(darkTheme = dark.value) {
                ProvideGiftAnimationBudget {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        ReceivedGiftRow(collectibleGift, onClick = { showDetail.value = true })
                        Spacer(Modifier.height(8.dp))
                        ReceivedGiftRow(regularGift, onClick = {})
                    }
                    if (showDetail.value) {
                        GiftDetailSheet(
                            target = GiftDetailTarget.Received(collectibleGift),
                            onDismiss = { showDetail.value = false }
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Plush Pepe").assertIsDisplayed()
        capture(output, "01-rows-light")

        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("Plush Pepe").assertIsDisplayed()
        capture(output, "02-rows-dark")

        compose.runOnIdle { showDetail.value = true }
        compose.onNodeWithText("Midnight Blue").assertIsDisplayed()
        captureDialog(output, "03-detail-dark")

        compose.runOnIdle { dark.value = false }
        compose.onNodeWithText("Midnight Blue").assertIsDisplayed()
        captureDialog(output, "04-detail-light")

        compose.runOnIdle { showDetail.value = false }
    }

    @Test
    fun captureProfileShowcaseLightAndDark() {
        val destination = System.getenv("BEYGRAM_GIFT_CAPTURE_DIR")
        assumeNotNull(destination)
        val output = File(checkNotNull(destination)).apply { mkdirs() }
        val dark = mutableStateOf(false)
        compose.setContent {
            BeyGramTheme(darkTheme = dark.value) {
                ProfileGiftsShowcase(
                    gifts = listOf(collectibleGift, regularGift),
                    onOpenAllGifts = {},
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        compose.onNodeWithText("#42").assertIsDisplayed()
        capture(output, "05-showcase-light")
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("#42").assertIsDisplayed()
        capture(output, "06-showcase-dark")
    }

    private fun capture(destination: File, frame: String) {
        val image = File(destination, "gift-$frame.png")
        compose.onRoot().captureRoboImage(image.absolutePath)
        check(image.length() > 0) { "Missing screenshot $image" }
    }

    private fun captureDialog(destination: File, frame: String) {
        val image = File(destination, "gift-$frame.png")
        compose.onNode(isDialog()).captureRoboImage(image.absolutePath)
        check(image.length() > 0) { "Missing screenshot $image" }
    }
}
