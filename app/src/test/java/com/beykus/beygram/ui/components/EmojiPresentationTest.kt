package com.beykus.beygram.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiPresentationTest {
    // Stand-ins for the text-default emoji used below; the device checks the real Unicode property.
    private val textDefault = setOf(0x00A9, 0x2640, 0x2642, 0x2764)

    private fun qualify(emoji: String) = qualifyEmoji(emoji) { it in textDefault }

    @Test fun `a text-default emoji gets the colour selector`() {
        assertEquals("❤\uFE0F", qualify("❤"))
        assertEquals("©\uFE0F", qualify("©"))
    }

    @Test fun `a ZWJ sequence that starts or ends with a text-default emoji is qualified as a whole`() {
        assertEquals("❤\uFE0F\u200D🔥", qualify("❤\u200D🔥"))
        assertEquals("🤷\u200D♂\uFE0F", qualify("🤷\u200D♂"))
    }

    @Test fun `emoji that already have a selector stay as they are`() {
        assertEquals("❤\uFE0F\u200D🔥", qualify("❤\uFE0F\u200D🔥"))
        assertEquals("❤\uFE0E", qualify("❤\uFE0E"))
    }

    @Test fun `colour emoji and plain text are unchanged`() {
        assertEquals("🔥", qualify("🔥"))
        assertEquals("👨\u200D💻", qualify("👨\u200D💻"))
        assertEquals("abc", qualify("abc"))
    }
}
