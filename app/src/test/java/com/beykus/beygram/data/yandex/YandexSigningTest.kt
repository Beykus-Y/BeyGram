package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.LyricLine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexSigningTest {

    @Test
    fun lrcParsesSortsAndExpandsRepeatedTags() {
        val lrc = """
            [ar:Someone]
            [00:01.50]Hello
            [00:03]World
            [00:02.5][00:10.123]Repeat
            [00:04.00]
        """.trimIndent()
        assertEquals(
            listOf(
                LyricLine(1_500, "Hello"),
                LyricLine(2_500, "Repeat"),
                LyricLine(3_000, "World"),
                LyricLine(10_123, "Repeat"),
            ),
            YandexSigning.parseLrc(lrc),
        )
    }

    @Test
    fun plainTextHasNoSyncedLines() {
        assertTrue(YandexSigning.parseLrc("first line\nsecond line").isEmpty())
    }

    @Test
    fun directLinkUsesSaltPathAndSecond() {
        val info = YandexSigning.DownloadInfo(host = "storage.example", path = "/abc", ts = "123", s = "xyz")
        // MD5("XGRlBW9FXlekgbPrRHuSiA" + "abc" + "xyz"), computed outside the app for this fixed input.
        assertEquals(
            "https://storage.example/get-mp3/f9a64526668963e5f6bcfca5348fed38/123/abc",
            YandexSigning.directLink(info),
        )
    }

    @Test
    fun downloadXmlIsParsed() {
        val xml = "<download-info><host>h.example</host><path>/p/q</path><ts>5</ts><s>S1</s></download-info>"
        val info = YandexSigning.parseDownloadInfo(xml)
        assertEquals(YandexSigning.DownloadInfo("h.example", "/p/q", "5", "S1"), info)
    }

    @Test
    fun downloadXmlWithDoctypeOrEntityIsRejected() {
        val doctype = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><download-info><host>&e;</host></download-info>"
        assertNull(YandexSigning.parseDownloadInfo(doctype))
        val missing = "<download-info><host>h</host></download-info>"
        assertNull(YandexSigning.parseDownloadInfo(missing))
    }

    @Test
    fun trackMapsFieldsAndCoverTemplate() {
        val json = JSONObject(
            """
            {"id": 42, "title": "Song", "durationMs": 215000, "available": true,
             "artists": [{"name": "A"}, {"name": "B"}],
             "albums": [{"id": 7, "title": "Album"}],
             "coverUri": "avatars.example/cover/%%"}
            """.trimIndent()
        )
        val track = YandexApi(YandexHttp()).parseTrack(json)!!
        assertEquals("42", track.trackId)
        assertEquals("ym:42", track.key)
        assertEquals("A, B", track.performer)
        assertEquals("Album", track.albumTitle)
        assertEquals(215_000L, track.durationMs)
        assertEquals("https://avatars.example/cover/200x200", track.coverUrl)
        assertTrue(track.isAvailable)
    }

    @Test
    fun trackWithoutTitleOrIdIsSkipped() {
        val api = YandexApi(YandexHttp())
        assertNull(api.parseTrack(JSONObject("""{"id": 1}""")))
        assertNull(api.parseTrack(JSONObject("""{"title": "x"}""")))
    }
}
