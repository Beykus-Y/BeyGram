package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexLyricsRequestTest {

    /** Answers the queued responses in order and records every requested URL. No network. */
    private class ScriptedHttp(vararg responses: YandexResponse) : YandexHttp() {
        val urls = mutableListOf<String>()
        private val queue = ArrayDeque(responses.toList())

        override fun get(url: String, token: String?, paced: Boolean): YandexResponse {
            urls += url
            return queue.removeFirst()
        }
    }

    private fun response(code: Int, body: String) = YandexResponse(code, body, null)

    private fun linkBody(link: String) = """{"result": {"downloadUrl": "$link", "major": {"name": "Holder"}}}"""

    private fun api(http: YandexHttp, key: String = KEY) =
        YandexApi(http, lyricsSignKey = { key }, clock = { NOW_MS })

    private fun expectedSign(trackId: String) = URLEncoder.encode(
        YandexSigning.lyricsSign(trackId, TIME_STAMP, KEY),
        "UTF-8",
    )

    @Test
    fun syncedLyricsAreSignedAndNeedNoTextRequest() {
        val http = ScriptedHttp(
            response(200, linkBody("https://storage.example/a.lrc")),
            response(200, "[00:01.00]Hello\n[00:02.00]World"),
        )
        val (result, report) = api(http).lyricsWithReport("token", "12345678")

        val lyrics = (result as ProviderResult.Success).value
        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals(null, lyrics.plainText)
        assertEquals("Holder", lyrics.copyrightHolder)
        assertEquals(2, http.urls.size)
        assertEquals(
            "${YandexConfig.API_BASE}/tracks/12345678/lyrics?format=LRC&timeStamp=$TIME_STAMP&sign=${expectedSign("12345678")}",
            http.urls[0],
        )
        assertTrue(report.contains("lyrics.format=LRC"))
        assertTrue(!report.contains(KEY) && !report.contains(expectedSign("12345678")))
    }

    @Test
    fun albumPartOfTrackIdIsDroppedFromPathAndSignature() {
        val http = ScriptedHttp(
            response(200, linkBody("https://storage.example/a.lrc")),
            response(200, "[00:01.00]Hello"),
        )
        api(http).lyricsWithReport("token", "12345678:777")

        assertEquals(
            "${YandexConfig.API_BASE}/tracks/12345678/lyrics?format=LRC&timeStamp=$TIME_STAMP&sign=${expectedSign("12345678")}",
            http.urls[0],
        )
    }

    @Test
    fun notFoundOnLrcFallsBackToText() {
        val http = ScriptedHttp(
            response(404, """{"error": "not-found"}"""),
            response(200, linkBody("https://storage.example/a.txt")),
            response(200, "Line one\n\n\nLine two\n"),
        )
        val (result, report) = api(http).lyricsWithReport("token", "12345678")

        val lyrics = (result as ProviderResult.Success).value
        assertEquals("Line one\n\nLine two", lyrics.plainText)
        assertTrue(lyrics.lines.isEmpty())
        assertEquals(3, http.urls.size)
        assertTrue(http.urls[1].contains("format=TEXT"))
        assertTrue(report.contains("fallback=TEXT"))
    }

    @Test
    fun fileWithoutTimeTagsFallsBackToText() {
        val http = ScriptedHttp(
            response(200, linkBody("https://storage.example/a.lrc")),
            response(200, "plain line"),
            response(200, linkBody("https://storage.example/a.txt")),
            response(200, "plain line"),
        )
        val (result, _) = api(http).lyricsWithReport("token", "12345678")

        assertEquals("plain line", (result as ProviderResult.Success).value.plainText)
        assertEquals(4, http.urls.size)
        assertTrue(http.urls[2].contains("format=TEXT"))
    }

    @Test
    fun authFailureOnLrcDoesNotFallBack() {
        val http = ScriptedHttp(response(401, "{}"))
        val (result, _) = api(http).lyricsWithReport("token", "12345678")

        assertEquals(ProviderResult.Failure(MusicProviderFailure.SessionExpired), result)
        assertEquals(1, http.urls.size)
    }

    @Test
    fun missingKeyFailsWithoutNetwork() {
        val http = ScriptedHttp()
        val (result, report) = api(http, key = "").lyricsWithReport("token", "12345678")

        assertEquals(ProviderResult.Failure(MusicProviderFailure.Unavailable), result)
        assertEquals("lyrics.sign=missing", report)
        assertTrue(http.urls.isEmpty())
    }

    private companion object {
        const val KEY = "test-key"
        const val NOW_MS = 1_700_000_000_000L
        const val TIME_STAMP = "1700000000"
    }
}
