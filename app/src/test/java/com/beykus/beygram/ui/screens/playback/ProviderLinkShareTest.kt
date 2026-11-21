package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.data.music.provider.ProviderTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLinkShareTest {
    private fun track(id: String = "123", albumId: String? = "456", title: String = "Song") =
        ProviderTrack(
            trackId = id, title = title, artists = listOf("Artist", "Other"),
            albumTitle = "Album", durationMs = 83_000L,
            coverUrl = "https://example.com/cover.jpg", isAvailable = true, albumId = albumId
        )

    @Test fun snapshotCapturesOnlyPublicTrackMetadata() {
        val snapshot = requireNotNull(providerTrackSharePayload(track()))
        assertEquals("Song", snapshot.title)
        assertEquals("Artist, Other", snapshot.performer)
        assertEquals("https://music.yandex.ru/album/456/track/123", snapshot.publicLink)
        assertEquals("Artist, Other — Song\nhttps://music.yandex.ru/album/456/track/123", snapshot.text)
        assertEquals(83_000L, snapshot.durationMs)
        assertEquals("https://example.com/cover.jpg", snapshot.coverUrl)
    }

    @Test fun unknownAlbumStillCreatesValidTrackLink() {
        assertEquals(
            "https://music.yandex.ru/track/123",
            providerTrackSharePayload(track(albumId = null))?.publicLink
        )
    }

    @Test fun malformedProviderIdsNeverBecomeOutboundLinks() {
        assertNull(providerTrackSharePayload(track(id = "123/path")))
        assertNull(providerTrackSharePayload(track(albumId = "bad-id")))
        assertNull(providerTrackSharePayload(track(id = "https://bad.example/x")))
    }

    @Test fun commentStaysOutsideTheFrozenPayloadAndHasABoundedLimit() {
        val snapshot = requireNotNull(providerTrackSharePayload(track()))
        assertEquals(
            "Hi!\n\nArtist, Other — Song\nhttps://music.yandex.ru/album/456/track/123",
            providerLinkMessage(snapshot, "  Hi!  ")
        )
        assertEquals(snapshot.text, providerLinkMessage(snapshot, "  "))
        val limit = providerLinkCommentLimit(snapshot)
        assertTrue(limit > 0)
        val exactlyFull = providerLinkMessage(snapshot, "a".repeat(limit))
        assertEquals(4096, exactlyFull.codePointCount(0, exactlyFull.length))
    }

    @Test fun giantTrackMetadataDoesNotPermitOversizedTdlibText() {
        assertNull(providerTrackSharePayload(track(title = "x".repeat(5000))))
    }
}
