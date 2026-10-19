package com.beykus.beygram.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingShareParserTest {
    private val send = "android.intent.action.SEND"

    @Test fun plainTextIsKeptWithInnerLineBreaks() {
        assertEquals(IncomingShare(1L, "first\nsecond"), IncomingShareParser.from(send, "text/plain", "  first\nsecond  ", false, 1L))
    }

    @Test fun blankOrMissingTextIsIgnored() {
        assertNull(IncomingShareParser.from(send, "text/plain", "   ", false, 1L))
        assertNull(IncomingShareParser.from(send, "text/plain", null, false, 1L))
    }

    @Test fun streamIsMarkedUnsupportedInsteadOfDropped() {
        assertTrue(IncomingShareParser.from(send, "text/plain", null, true, 1L)!!.fileUnsupported)
    }

    @Test fun otherActionsAndTypesAreIgnored() {
        assertNull(IncomingShareParser.from("android.intent.action.VIEW", "text/plain", "x", false, 1L))
        assertNull(IncomingShareParser.from(send, "image/png", "x", false, 1L))
        assertNull(IncomingShareParser.from(send, null, "x", false, 1L))
    }
}
