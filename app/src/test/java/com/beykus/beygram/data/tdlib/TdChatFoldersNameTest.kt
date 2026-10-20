package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Test

class TdChatFoldersNameTest {
    class Formatted(@JvmField val text: String)
    class ModernName(@JvmField val text: Formatted)

    @Test fun legacyStringNamesRemainReadable() {
        assertEquals("Family", tdFolderDisplayName("  Family  "))
    }

    @Test fun modernFormattedFolderNamesAreUnwrapped() {
        assertEquals("💼 Work", tdFolderDisplayName(ModernName(Formatted("💼 Work"))))
    }

    @Test fun missingFolderNameIsEmptyAndCallerUsesFallback() {
        assertEquals("", tdFolderDisplayName(null))
        assertEquals("", tdFolderDisplayName(object {}))
    }
}
