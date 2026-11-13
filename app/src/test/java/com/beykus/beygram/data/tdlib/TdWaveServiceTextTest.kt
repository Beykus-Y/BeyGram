package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdWaveServiceTextTest {
    private val link = "https://beygram.beykus.fun/#bgwave-v1.AAAA"

    @Test fun `label is one hidden text url entity over the whole line`() {
        val label = "Служебное сообщение BeyGram"
        val text = waveServiceFormattedText(label, link)

        assertEquals(label, text.text)
        assertEquals(1, text.entities.size)
        val entity = text.entities[0]
        assertEquals(0, entity.offset)
        assertEquals(label.length, entity.length)
        assertEquals(link, (entity.type as TdApi.TextEntityTypeTextUrl).url)
    }

    @Test fun `entity survives the domain round trip with utf16 offsets`() {
        val label = "😀 Служебное"
        val entities = waveServiceFormattedText(label, link).domainEntities()
        assertEquals(listOf(MessageTextEntity(0, label.length, TextStyleKind.Url, link)), entities)
    }

    @Test fun `links with whitespace are refused`() {
        try {
            waveServiceFormattedText("label", "https://x y")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }
}
