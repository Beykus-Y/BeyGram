package com.beykus.beygram.media.bgwave

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgWaveLinkBoundaryTest {
    @Test fun limitIsAppliedToTheTransmittedHttpsWrapper() {
        val payload = "a".repeat(BgWaveLink.MAX_LINK_CHARS - BgWaveLink.HTTPS_PREFIX.length)
        val uri = BgWaveLink.URI_PREFIX + payload
        assertEquals(BgWaveLink.MAX_LINK_CHARS, BgWaveLink.toHttps(uri)?.length)
        assertTrue(BgWaveLink.isWaveLink(uri))
        assertNull(BgWaveLink.toHttps(uri + "a"))
        assertFalse(BgWaveLink.isWaveLink(BgWaveLink.HTTPS_PREFIX + payload + "a"))
    }

    @Test fun emptyPayloadIsNotAWaveLink() {
        assertNull(BgWaveLink.toHttps(BgWaveLink.URI_PREFIX))
        assertFalse(BgWaveLink.isWaveLink(BgWaveLink.HTTPS_PREFIX))
    }

    @Test fun ordinaryTextWithInlineWaveLinkIsNotAServiceMessage() {
        val text = "Please read this link"
        val link = BgWaveLink.HTTPS_PREFIX + "abc"
        val inline = MessageTextEntity(17, 4, TextStyleKind.Url, link)
        assertNull(BgWaveLink.find(text, listOf(inline)))
        val whole = MessageTextEntity(0, text.length, TextStyleKind.Url, link)
        assertEquals(link, BgWaveLink.find(text, listOf(whole)))
    }
}
