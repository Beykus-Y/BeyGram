package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.share.ShareOutcomeUncertainException
import com.beykus.beygram.ui.screens.chat.ForwardTarget
import kotlinx.coroutines.runBlocking
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

    @Test fun partialBatchFailureLeavesOnlyFailedRecipientsSelected() = runBlocking {
        val recipients = listOf(
            ForwardTarget(1L, "First"),
            ForwardTarget(2L, "Second", topicId = 42L),
            ForwardTarget(3L, "Removed")
        )
        val attempts = mutableListOf<Pair<Long, Long>>()
        val payload = requireNotNull(providerTrackSharePayload(track()))
        val message = providerLinkMessage(payload, "Hello")
        val outcome = sendPublicProviderLinkToRecipients(recipients, setOf(1L, 2L), message) { target, text ->
            assertEquals(message, text)
            attempts += target.chatId to target.topicId
            if (target.chatId == 2L) Result.failure(IllegalStateException("not writable"))
            else Result.success(Unit)
        }
        assertEquals(listOf(1L to 0L, 2L to 42L), attempts)
        assertEquals(listOf(recipients[1], recipients[2]), outcome.failed)
        assertEquals(false, outcome.uncertain)
    }

    @Test fun uncertainDeliveryDisablesBlindRetryEvenIfOtherChatsSucceeded() = runBlocking {
        val recipients = listOf(ForwardTarget(10L, "Delivered"), ForwardTarget(20L, "Unconfirmed"))
        val outcome = sendPublicProviderLinkToRecipients(recipients, setOf(10L, 20L), "public link") { target, _ ->
            if (target.chatId == 20L) Result.failure(ShareOutcomeUncertainException())
            else Result.success(Unit)
        }
        assertEquals(listOf(recipients[1]), outcome.failed)
        assertTrue(outcome.uncertain)
    }

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

    @Test fun providerKeysNeverFallBackToSendingAudioFiles() {
        val knownProviderKey = ProviderTracks.playbackItem(track()).key
        val malformedProviderKey = ProviderTracks.playbackItem(track(id = "bad/id")).key
        assertTrue(ProviderTracks.isProviderKey(knownProviderKey))
        assertTrue(ProviderTracks.isProviderKey(malformedProviderKey))
        assertNull(providerTrackSharePayload(track(id = "bad/id")))
        assertEquals(false, canOfferLocalAudioShare(knownProviderKey, allowedByMediaPolicy = true))
        assertEquals(false, canOfferLocalAudioShare(malformedProviderKey, allowedByMediaPolicy = true))
        assertTrue(canOfferLocalAudioShare("telegram-audio", allowedByMediaPolicy = true))
        assertEquals(false, canOfferLocalAudioShare("telegram-audio", allowedByMediaPolicy = false))
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

    @Test fun albumPayloadContainsOnlyAValidatedPublicLink() {
        val payload = requireNotNull(providerAlbumSharePayload("  Album Name  ", "812", null))
        assertEquals("Album Name", payload.title)
        assertEquals("https://music.yandex.ru/album/812", payload.publicLink)
        assertEquals("Album Name\nhttps://music.yandex.ru/album/812", payload.text)
        assertEquals(0L, payload.durationMs)
        assertNull(providerAlbumSharePayload("Album", "812/track/999", null))
    }

    @Test fun playlistPayloadRejectsNonUuidAndKeepsCanonicalLink() {
        val uuid = "abcdef12-3456-7890-abcd-1234567890ab"
        val payload = requireNotNull(providerPlaylistSharePayload("  My List  ", uuid, "https://covers.example/img"))
        assertEquals("My List", payload.title)
        assertEquals("https://music.yandex.ru/playlists/$uuid", payload.publicLink)
        assertEquals("My List\nhttps://music.yandex.ru/playlists/$uuid", payload.text)
        assertNull(providerPlaylistSharePayload("Private List", "private_owner/token", null))
    }

    @Test fun giantTrackMetadataDoesNotPermitOversizedTdlibText() {
        assertNull(providerTrackSharePayload(track(title = "x".repeat(5000))))
    }
}
