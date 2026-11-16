package com.beykus.beygram.deeplink

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YandexMusicLinkTest {
    @Test fun `album links open the album with or without tracking tags`() {
        assertEquals(
            YandexMusicTarget.Album("41286299"),
            yandexMusicTarget("https://music.yandex.ru/album/41286299?utm_source=desktop&utm_medium=copy_link")
        )
        assertEquals(YandexMusicTarget.Album("41286299"), yandexMusicTarget("https://music.yandex.ru/album/41286299"))
        assertEquals(YandexMusicTarget.Album("41286299"), yandexMusicTarget("music.yandex.ru/album/41286299/"))
    }

    @Test fun `track links keep the album when the address has one`() {
        assertEquals(
            YandexMusicTarget.Track("140731042", "37293134"),
            yandexMusicTarget("https://music.yandex.ru/album/37293134/track/140731042")
        )
        assertEquals(
            YandexMusicTarget.Track("140731042", "37293134"),
            yandexMusicTarget("https://music.yandex.ru/album/37293134/track/140731042?utm_source=desktop&utm_medium=copy_link")
        )
        assertEquals(YandexMusicTarget.Track("140731042", null), yandexMusicTarget("https://music.yandex.ru/track/140731042"))
    }

    @Test fun `every Yandex Music domain is recognised, with or without www`() {
        listOf("music.yandex.ru", "music.yandex.com", "music.yandex.by", "music.yandex.kz", "music.yandex.uz", "www.music.yandex.ru")
            .forEach { host -> assertEquals(YandexMusicTarget.Album("1"), yandexMusicTarget("https://$host/album/1")) }
    }

    @Test fun `other hosts, schemes and credentials are never treated as Yandex Music`() {
        assertNull(yandexMusicTarget("https://music.yandex.ru.example.com/album/1"))
        assertNull(yandexMusicTarget("https://example.com/music.yandex.ru/album/1"))
        assertNull(yandexMusicTarget("javascript://music.yandex.ru/album/1"))
        assertNull(yandexMusicTarget("https://user@music.yandex.ru/album/1"))
    }

    @Test fun `playlists open by their uuid, with or without the lk prefix and in any letter case`() {
        assertEquals(
            YandexMusicTarget.Playlist("39a8f319-4740-d4ed-9629-b6a38b01a1dc"),
            yandexMusicTarget("https://music.yandex.ru/playlists/39a8f319-4740-d4ed-9629-b6a38b01a1dc?utm_source=desktop&utm_medium=copy_link")
        )
        assertEquals(
            YandexMusicTarget.Playlist("lk.fd280462-dabc-4ecd-a4d2-94110e7552dc"),
            yandexMusicTarget("https://music.yandex.ru/playlists/lk.fd280462-dabc-4ecd-a4d2-94110e7552dc")
        )
        assertEquals(
            YandexMusicTarget.Playlist("39a8f319-4740-d4ed-9629-b6a38b01a1dc"),
            yandexMusicTarget("music.yandex.ru/playlists/39A8F319-4740-D4ED-9629-B6A38B01A1DC")
        )
    }

    @Test fun `forms the native open does not handle stay null, so they open in the browser`() {
        assertNull(yandexMusicTarget("https://music.yandex.ru/playlists/not-a-uuid"))
        assertNull(yandexMusicTarget("https://music.yandex.ru/users/music-blog/playlists/1250"))
        assertNull(yandexMusicTarget("https://music.yandex.ru/artist/123"))
        assertNull(yandexMusicTarget("https://music.yandex.ru/"))
    }

    @Test fun `ids must be numbers and the path must have exactly the known shape`() {
        assertNull(yandexMusicTarget("https://music.yandex.ru/album/abc"))
        assertNull(yandexMusicTarget("https://music.yandex.ru/album/1/track/x"))
        assertNull(yandexMusicTarget("https://music.yandex.ru/album/1/track/2/extra"))
    }

    @Test fun `a Yandex Music link in text becomes a native link entity`() {
        val prefix = "Слушай "
        val url = "https://music.yandex.ru/album/37293134/track/140731042?utm_source=desktop&utm_medium=copy_link"
        val text = prefix + url
        val link = messageEntityLink(MessageTextEntity(prefix.length, url.length, TextStyleKind.Url, ""), text)
        assertEquals(MessageEntityLink.YandexMusic(url, YandexMusicTarget.Track("140731042", "37293134")), link)
    }

    @Test fun `a playlist link becomes a native link entity, a login-form playlist stays an ordinary link`() {
        val url = "https://music.yandex.ru/playlists/39a8f319-4740-d4ed-9629-b6a38b01a1dc?utm_source=desktop&utm_medium=copy_link"
        assertEquals(
            MessageEntityLink.YandexMusic(url, YandexMusicTarget.Playlist("39a8f319-4740-d4ed-9629-b6a38b01a1dc")),
            messageEntityLink(MessageTextEntity(0, url.length, TextStyleKind.Url, ""), url)
        )
        val login = "https://music.yandex.ru/users/music-blog/playlists/1250"
        assertEquals(
            MessageEntityLink.ExternalUrl(login),
            messageEntityLink(MessageTextEntity(0, login.length, TextStyleKind.Url, ""), login)
        )
    }
}
