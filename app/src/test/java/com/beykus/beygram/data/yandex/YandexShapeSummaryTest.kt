package com.beykus.beygram.data.yandex

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexShapeSummaryTest {

    @Test
    fun `short error codes are shown, other strings only by type`() {
        val root = JSONObject("""{"error":"not-allowed","result":{"downloadUrl":"https://example.test/x?sign=abc"}}""")
        val summary = shapeSummary(root)
        assertTrue(summary.contains("error=\"not-allowed\""))
        assertTrue(summary.contains("result.downloadUrl=string"))
        assertFalse(summary.contains("example.test"))
        assertFalse(summary.contains("abc"))
    }

    @Test
    fun `strings that could carry a token are never shown`() {
        val root = JSONObject("""{"message":"OAuth abc123token","code":"Bearer xyz"}""")
        val summary = shapeSummary(root)
        assertFalse(summary.contains("abc123token"))
        assertFalse(summary.contains("xyz"))
        assertTrue(summary.contains("message=string"))
    }

    @Test
    fun `numbers and booleans are shown as values`() {
        val summary = shapeSummary(JSONObject("""{"result":{"timeStamp":1700000000,"ok":true}}"""))
        assertTrue(summary.contains("result.timeStamp=1700000000"))
        assertTrue(summary.contains("result.ok=true"))
    }
}
