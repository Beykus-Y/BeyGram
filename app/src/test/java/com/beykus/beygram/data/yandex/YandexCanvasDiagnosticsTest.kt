package com.beykus.beygram.data.yandex

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexCanvasDiagnosticsTest {

    @Test
    fun returnsNullForEmptyTrackArray() {
        assertNull(trackCanvasShapeSummary(JSONArray()))
    }

    @Test
    fun reportsAllowlistedTypesAndAggregatedCountsWithoutArbitraryKeysOrValues() {
        val secretUrl = "https://video-preview.s3.yandex.net/secret-path/clip.mp4?token=abc123"
        val track1 = JSONObject()
            .put("id", "99887766")
            .put("title", "Track 1")
            .put("backgroundVideoUri", secretUrl)
            .put("canvas", true)
            .put("customVideoSecretField", 42)
        val track2 = JSONObject()
            .put("id", "11223344")
            .put("title", "Track 2")
            .put("motionCover", JSONObject().put("url", "https://example.com/motion.mp4"))

        val summary = trackCanvasShapeSummary(JSONArray().put(track1).put(track2))
        assertNotNull(summary)
        assertTrue(summary!!.contains("canvas.checked=2"))
        assertTrue(summary.contains("backgroundVideoUri=1"))
        assertTrue(summary.contains("scheme.https=1"))
        assertTrue(summary.contains("ext.mp4=1"))
        assertTrue(summary.contains("canvas=boolean"))
        assertTrue(summary.contains("motionCover=object"))
        assertTrue(summary.contains("otherVideoKeys=1"))

        // Strict redaction: no arbitrary key names, scalar values, URLs, hosts, tokens, or track IDs
        assertFalse(summary.contains("customVideoSecretField"))
        assertFalse(summary.contains("true"))
        assertFalse(summary.contains("42"))
        assertFalse(summary.contains("video-preview.s3.yandex.net"))
        assertFalse(summary.contains("secret-path"))
        assertFalse(summary.contains("abc123"))
        assertFalse(summary.contains("99887766"))
        assertFalse(summary.contains("11223344"))
        assertFalse(summary.contains("example.com"))
    }

    @Test
    fun apiSearchInvokesCanvasDiagnosticsCallback() {
        val body = """
            {
              "result": {
                "tracks": {
                  "results": [
                    {
                      "id": "555",
                      "title": "Demo",
                      "available": true,
                      "artists": [{"name": "Artist"}],
                      "backgroundVideoUri": "https://cdn.example/demo.mp4"
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val http = object : YandexHttp() {
            override fun get(url: String, token: String?, paced: Boolean) = YandexResponse(200, body, null)
        }
        var captured: String? = null
        val api = YandexApi(http, onCanvasDiagnostics = { captured = it })
        api.search("token", "Demo", 5)

        assertEquals("canvas.checked=1; backgroundVideoUri=1; scheme.https=1; ext.mp4=1", captured)
    }
}
