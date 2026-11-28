package com.beykus.beygram.ui.screens.profile

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GiftCatalogItem
import com.beykus.beygram.domain.model.GiftCollectionItem
import com.beykus.beygram.domain.model.GiftSendOutcome
import com.beykus.beygram.domain.model.GiftsInfo
import com.beykus.beygram.domain.model.GiftsScreenTab
import com.beykus.beygram.domain.model.ReceivedGiftFilter
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.ReceivedGiftSort
import com.beykus.beygram.domain.model.ReceivedGiftsPage
import com.beykus.beygram.domain.model.StarBalance
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru-rRU-w360dp-h780dp-xhdpi", application = Application::class)
class GiftsCollectionsFilterTest {
    @get:Rule val compose = createComposeRule()

    private fun sampleGift(id: String, name: String, isPinned: Boolean = false) = ReceivedGiftItem(
        id = id,
        senderName = "Alice",
        dateSeconds = 1_700_000_000,
        emoji = "\uD83C\uDF81",
        starCount = 50L,
        name = name,
        text = "",
        isPrivate = false,
        isSaved = true,
        isUpgraded = false,
        wasRefunded = false,
        isPinned = isPinned
    )

    @Test
    fun otherProfileSeparatesReceivedGiftsAndShopAndLoadsCollections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val queries = mutableListOf<Triple<Int, ReceivedGiftFilter, ReceivedGiftSort>>()
        val baseInfo = GiftsInfo(
            balance = StarBalance(200L),
            catalog = listOf(GiftCatalogItem(1L, "\uD83C\uDF81", 15L, false, null, false, false)),
            received = listOf(sampleGift("g1", "Rose")),
            receivedNextOffset = "next_1",
            collections = listOf(GiftCollectionItem(id = 7, name = "Favorites", giftCount = 1, emoji = "⭐")),
            isPremium = false,
            receivedUnavailable = false,
            collectionsUnavailable = false,
            textMaxLength = 128
        )

        compose.setContent {
            BeyGramTheme(darkTheme = false) {
                GiftsScreen(
                    accountId = "acc_1",
                    title = "Gifts",
                    profileId = 99L,
                    profileTitle = "Bob",
                    isSelf = false,
                    initialTab = GiftsScreenTab.Received,
                    load = { Result.success(baseInfo) },
                    loadMoreReceived = { Result.success(ReceivedGiftsPage(emptyList(), "")) },
                    loadReceivedPage = { collectionId, filter, sort, offset ->
                        queries += Triple(collectionId, filter, sort)
                        if (offset == "next_1") {
                            Result.success(ReceivedGiftsPage(listOf(sampleGift("g2", "Gem")), ""))
                        } else {
                            Result.success(ReceivedGiftsPage(listOf(sampleGift("g3", "Star Gift")), ""))
                        }
                    },
                    send = { _, _, _ -> GiftSendOutcome.Sent },
                    onOpenStars = {},
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Rose").assertIsDisplayed()
        compose.onNodeWithText("⭐ Favorites (1)").assertIsDisplayed().performClick()
        compose.onNodeWithText("Star Gift").assertIsDisplayed()
        assertEquals(Triple(7, ReceivedGiftFilter.All, ReceivedGiftSort.Date), queries.last())

        // Switch to Shop tab without losing received gifts tab separation.
        compose.onNodeWithText(context.getString(R.string.gifts_tab_shop)).performClick()
        compose.onNodeWithText(context.getString(R.string.gifts_catalog_title)).assertIsDisplayed()
    }
}
