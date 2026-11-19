package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumTopicCreationColorTest {
    @Test fun everyColourTelegramOffersIsKeptAsIs() {
        assertEquals(6, ForumTopicCreationColors.size)
        ForumTopicCreationColors.forEach { rgb -> assertEquals(rgb, forumTopicCreationColor(rgb)) }
    }

    @Test fun anyOtherColourFallsBackToTheDefaultBlue() {
        assertEquals(ForumTopicDefaultColor, forumTopicCreationColor(0))
        assertEquals(ForumTopicDefaultColor, forumTopicCreationColor(0x123456))
        // An alpha byte makes it an ARGB value, which is not TDLib's 0xRRGGBB form, so it is not offered either.
        assertEquals(ForumTopicDefaultColor, forumTopicCreationColor(0xFF6FB9F0.toInt()))
    }

    @Test fun theDefaultColourIsOneOfTheOfferedColours() {
        assertTrue(ForumTopicDefaultColor in ForumTopicCreationColors)
    }
}
