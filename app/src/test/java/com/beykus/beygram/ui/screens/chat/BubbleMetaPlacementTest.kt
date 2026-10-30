package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleMetaPlacementTest {
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
