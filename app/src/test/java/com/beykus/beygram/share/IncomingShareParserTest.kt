package com.beykus.beygram.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingShareParserTest {
    private val send = "android.intent.action.SEND"
    private val sendMultiple = "android.intent.action.SEND_MULTIPLE"

    @Test fun plainTextIsKeptWithInnerLineBreaks() {
        assertEquals(IncomingShare(1L, "first\nsecond"), IncomingShareParser.from(send, "text/plain", "  first\nsecond  ", emptyList(), 1L))
    }

    @Test fun blankOrMissingTextIsIgnored() {
        assertNull(IncomingShareParser.from(send, "text/plain", "   ", emptyList(), 1L))
        assertNull(IncomingShareParser.from(send, "text/plain", null, emptyList(), 1L))
    }

    @Test fun otherActionsAreIgnored() {
        assertNull(IncomingShareParser.from("android.intent.action.VIEW", "text/plain", "x", emptyList(), 1L))
    }

    @Test fun nonTextWithoutFilesIsIgnored() {
        assertNull(IncomingShareParser.from(send, "image/png", "x", emptyList(), 1L))
        assertNull(IncomingShareParser.from(sendMultiple, null, null, emptyList(), 1L))
    }

    @Test fun otherTextTypesKeepTheirPlainText() {
        assertEquals(IncomingShare(1L, "a,b"), IncomingShareParser.from(send, "text/csv", "a,b", emptyList(), 1L))
    }

    @Test fun foreignContentIsAccepted() {
        assertTrue(IncomingShareParser.isForeignContent("content", "com.example.files", "com.beykus.beygram"))
        assertTrue(IncomingShareParser.isForeignContent("content", "media", "com.beykus.beygram.dev"))
    }

    @Test fun fileSchemeIsRejected() {
        assertFalse(IncomingShareParser.isForeignContent("file", null, "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("file", "", "com.beykus.beygram.dev"))
    }

    @Test fun ownFileProviderIsRejectedForBothVariants() {
        assertFalse(IncomingShareParser.isForeignContent("content", "com.beykus.beygram.files", "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("content", "com.beykus.beygram.dev.files", "com.beykus.beygram.dev"))
        assertFalse(IncomingShareParser.isForeignContent("content", "com.beykus.beygram.dev.files", "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("content", "com.beykus.beygram.files", "com.beykus.beygram.dev"))
    }

    @Test fun missingSchemeOrAuthorityIsRejected() {
        assertFalse(IncomingShareParser.isForeignContent(null, null, "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("content", null, "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("content", "", "com.beykus.beygram"))
    }

    @Test fun userInfoInAuthorityIsRejected() {
        assertFalse(IncomingShareParser.isForeignContent("content", "0@com.beykus.beygram.files", "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("content", "0@com.beykus.beygram.dev.files", "com.beykus.beygram.dev"))
        assertFalse(IncomingShareParser.isForeignContent("content", "10@com.example.files", "com.beykus.beygram"))
    }

    @Test fun portInAuthorityIsRejected() {
        assertFalse(IncomingShareParser.isForeignContent("content", "com.beykus.beygram.files:1", "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("content", "com.example:443", "com.beykus.beygram"))
    }

    @Test fun authorityComparisonIgnoresCase() {
        assertFalse(IncomingShareParser.isForeignContent("content", "COM.BEYKUS.BEYGRAM.FILES", "com.beykus.beygram"))
        assertFalse(IncomingShareParser.isForeignContent("content", "Com.Beykus.Beygram.Dev.Files", "com.beykus.beygram.dev"))
        assertFalse(IncomingShareParser.isForeignContent("CONTENT", "com.beykus.beygram.files", "com.beykus.beygram"))
    }

    @Test fun anyAuthorityOfOwnPackageIsRejected() {
        assertFalse(IncomingShareParser.isForeignContent("content", "com.beykus.beygram.dev.media", "com.beykus.beygram.dev"))
        assertFalse(IncomingShareParser.isForeignContent("content", "com.beykus.beygram.media", "com.beykus.beygram"))
    }

    @Test fun clipDataFilesAreCollectedWithoutDuplicates() {
        assertEquals(listOf("a", "b", "c"), IncomingShareParser.collectStreams(send, listOf("a", "b"), listOf("b", "c")))
        assertEquals(listOf("x"), IncomingShareParser.collectStreams(sendMultiple, emptyList(), listOf("x")))
    }

    @Test fun otherActionsCollectNoFiles() {
        assertEquals(emptyList<String>(), IncomingShareParser.collectStreams("android.intent.action.VIEW", listOf("a"), listOf("b")))
    }
}
