package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.ui.text.TextRange
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SelectedMessageQuoteTest {
    @Test fun repeatedPhrasesQuoteTheSelectedOccurrence() {
        val source = "hello again hello"
        assertEquals(SelectedMessageQuote("hello", 12), selectedMessageQuote(source, TextRange(17, 12)))
    }

    @Test fun usesUtf16OffsetsWithoutSplittingAnEmojiOrCombiningMark() {
        val emoji = "👩‍💻"
        val source = "x${emoji}e\u0301"
        assertEquals(SelectedMessageQuote(emoji, 1), selectedMessageQuote(source, TextRange(1, 1 + emoji.length)))
        assertNull(selectedMessageQuote(source, TextRange(1, 2)))
        assertNull(selectedMessageQuote(source, TextRange(1, 3)))
        assertNull(selectedMessageQuote(source, TextRange(source.length - 2, source.length - 1)))
    }

    @Test fun preservesIntersectingFormattingForTheSelectedOccurrence() {
        val source = "hello again hello"
        val entities = listOf(
            MessageTextEntity(0, 5, TextStyleKind.Italic),
            MessageTextEntity(11, 6, TextStyleKind.Bold),
            MessageTextEntity(13, 3, TextStyleKind.Url, "https://example.com")
        )
        val fragment = selectedMessageQuote(source, TextRange(12, 17), entities)
        assertEquals(SelectedMessageQuote("hello", 12, listOf(
            MessageTextEntity(0, 5, TextStyleKind.Bold),
            MessageTextEntity(1, 3, TextStyleKind.Url, "https://example.com")
        )), fragment)
    }

    @Test fun rejectsSelectionsThatWouldExposeSpoilers() {
        val hidden = listOf(MessageTextEntity(0, 6, TextStyleKind.Spoiler))
        assertNull(selectedMessageQuote("secret", TextRange(1, 4), hidden))
    }

    @Test fun whitespaceAndOversizedQuotesAreUnavailable() {
        assertNull(selectedMessageQuote("   ", TextRange(0, 3)))
        assertNull(selectedMessageQuote("x".repeat(1_025), TextRange(0, 1_025)))
    }
}
