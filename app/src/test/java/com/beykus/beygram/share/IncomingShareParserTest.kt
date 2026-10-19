package com.beykus.beygram.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingShareParserTest {
    private val send = "android.intent.action.SEND"

    @Test fun plainTextIsKeptWithInnerLineBreaks() {
        assertEquals("first\nsecond", IncomingShareParser.textOf(send, "text/plain", "  first\nsecond  "))
    }

    @Test fun blankOrMissingTextIsIgnored() {
        assertNull(IncomingShareParser.textOf(send, "text/plain", "   "))
        assertNull(IncomingShareParser.textOf(send, "text/plain", null))
    }

    @Test fun otherActionsAndTypesAreIgnored() {
        assertNull(IncomingShareParser.textOf("android.intent.action.VIEW", "text/plain", "x"))
        assertNull(IncomingShareParser.textOf(send, "image/png", "x"))
        assertNull(IncomingShareParser.textOf(send, null, "x"))
    }
}
