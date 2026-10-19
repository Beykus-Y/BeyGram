package com.beykus.beygram.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
