package com.beykus.beygram.ui.screens.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactAlphabetGroupTest {
    @Test fun latinNamesAreGroupedCaseInsensitively() {
        assertEquals("A", contactAlphabetGroup("alice"))
        assertEquals("B", contactAlphabetGroup("Bob"))
    }

    @Test fun cyrillicNamesStayInTheirOwnAlphabet() {
        assertEquals("Ю", contactAlphabetGroup("юлия"))
        assertEquals("А", contactAlphabetGroup("  Анна"))
    }

    @Test fun missingAndSymbolOnlyNamesGoToFallback() {
        assertEquals("#", contactAlphabetGroup(""))
        assertEquals("#", contactAlphabetGroup("  "))
        assertEquals("#", contactAlphabetGroup("🌟 Nickname"))
        assertEquals("#", contactAlphabetGroup("123"))
    }
}
