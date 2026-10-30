package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleMetaPlacementTest {
    private fun message(text: String): ChatMessage = ChatMessage(
        id = 1L, chatId = 1L, text = text, time = "18:35", isOutgoing = true
    )

    @Test
    fun shortPlainTextIsEligibleForInlineTime() {
        assertTrue(useInlineTextMetadata(message("Привет"), false, false))
        assertTrue(useInlineTextMetadata(message("ok"), false, false))
    }

    @Test
    fun multilineAndLongTextKeepSeparateMetadataRow() {
        assertFalse(useInlineTextMetadata(message("Привет\nКак дела?"), false, false))
        assertFalse(useInlineTextMetadata(message("This is a longer sentence with many words"), false, false))
    }

    @Test
    fun senderHeaderOrTranslationPreventsInlineMetadata() {
        assertFalse(useInlineTextMetadata(message("ok"), true, false))
        assertFalse(useInlineTextMetadata(message("ok"), false, true))
    }

    @Test
    fun shortTextAndTimeCanShareOneRow() {
        assertTrue(bubbleMetaFitsBesideBody(bodyWidth = 45, metaWidth = 70, maxWidth = 292))
    }

    @Test
    fun textUsesSeparateTimeRowIfBothDoNotFit() {
        assertFalse(bubbleMetaFitsBesideBody(bodyWidth = 250, metaWidth = 70, maxWidth = 292))
    }

    @Test
    fun exactFitIsAllowed() {
        assertTrue(bubbleMetaFitsBesideBody(bodyWidth = 212, metaWidth = 80, maxWidth = 292))
    }

    @Test
    fun emptyBodyOrEmptyMetadataIsNotInline() {
        assertFalse(bubbleMetaFitsBesideBody(bodyWidth = 0, metaWidth = 55, maxWidth = 292))
        assertFalse(bubbleMetaFitsBesideBody(bodyWidth = 55, metaWidth = 0, maxWidth = 292))
    }

    @Test
    fun overflowSafeEvenWithVeryLargeMeasuredChild() {
        assertFalse(bubbleMetaFitsBesideBody(bodyWidth = Int.MAX_VALUE, metaWidth = 80, maxWidth = 292))
    }
}
