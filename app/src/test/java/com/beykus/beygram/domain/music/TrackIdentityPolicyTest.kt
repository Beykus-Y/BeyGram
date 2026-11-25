package com.beykus.beygram.domain.music

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackIdentityPolicyTest {

    private fun telegramAudio(
        secret: Boolean = false,
        isSecretChat: Boolean = false,
        viewOnce: Boolean = false,
        path: String = "/data/user/0/app/cache/track.mp3",
    ) = PlaybackItem(
        key = "tg|legacy|chat:42:message:99|Audio|777",
        kind = MediaKind.Audio,
        path = path,
        title = "Artist — Track Title",
        trackTitle = "Track Title",
        performer = "Artist",
        album = "Album",
        durationSeconds = 215,
        origin = PlaybackOrigin(chatId = 42L, messageId = 99L, accountId = "acc1"),
        secret = secret,
        isSecretChat = isSecretChat,
        viewOnce = viewOnce,
        accountId = "acc1",
        fileId = 777,
    )

    @Test
    fun fromPlaybackItemExtractsYandexTelegramAndLocalSources() {
        val ym = PlaybackItem(
            key = "ym:123456",
            kind = MediaKind.Audio,
            path = "",
            title = "Song",
            trackTitle = "Song",
            performer = "Band",
            durationSeconds = 180,
        )
        val ymId = TrackIdentity.fromPlaybackItem(ym)
        assertEquals(TrackAudioSource.YandexMusic, ymId.audioSource)
        assertEquals("123456", ymId.providerTrackId)
        assertNull(ymId.localFilePath)

        val tgId = TrackIdentity.fromPlaybackItem(telegramAudio())
        assertEquals(TrackAudioSource.Telegram, tgId.audioSource)
        assertNull(tgId.providerTrackId)
        assertEquals("Track Title", tgId.title)
        assertEquals("Artist", tgId.artist)
        assertEquals(215_000L, tgId.durationMs)

        val local = PlaybackItem(
            key = "local|legacy|/sdcard/Music/a.flac",
            kind = MediaKind.Audio,
            path = "/sdcard/Music/a.flac",
            title = "Local Song",
            durationSeconds = 90,
        )
        assertEquals(TrackAudioSource.LocalFile, TrackIdentity.fromPlaybackItem(local).audioSource)
    }

    @Test
    fun externalLookupDeniedForSecretProtectedAndOptOut() {
        val publicId = TrackIdentity.fromPlaybackItem(telegramAudio(), protectedChat = false)
        assertEquals(
            MetadataLookupDecision.DeniedOptInRequired,
            ExternalMetadataPolicy.checkExternalLookup(publicId, isMusic = true, externalLookupOptIn = false),
        )
        assertEquals(
            MetadataLookupDecision.Allowed,
            ExternalMetadataPolicy.checkExternalLookup(publicId, isMusic = true, externalLookupOptIn = true),
        )

        val protectedId = TrackIdentity.fromPlaybackItem(telegramAudio(), protectedChat = true)
        assertEquals(
            MetadataLookupDecision.DeniedProtectedChat,
            ExternalMetadataPolicy.checkExternalLookup(protectedId, isMusic = true, externalLookupOptIn = true),
        )
        assertNull(ExternalMetadataPolicy.sanitizedExternalQuery(protectedId))

        val unknownPrivacyId = TrackIdentity.fromPlaybackItem(telegramAudio(), protectedChat = null)
        assertEquals(
            MetadataLookupDecision.DeniedUnknownPrivacy,
            ExternalMetadataPolicy.checkExternalLookup(unknownPrivacyId, isMusic = true, externalLookupOptIn = true),
        )
        assertNull(ExternalMetadataPolicy.sanitizedExternalQuery(unknownPrivacyId))
        assertFalse(ExternalMetadataPolicy.allowsExternalLookup(telegramAudio(), protectedChat = null, externalLookupOptIn = true))

        val secretId = TrackIdentity.fromPlaybackItem(telegramAudio(secret = true))
        assertEquals(
            MetadataLookupDecision.DeniedPrivateMedia,
            ExternalMetadataPolicy.checkExternalLookup(secretId, isMusic = true, externalLookupOptIn = true),
        )
        assertEquals(
            MetadataLookupDecision.DeniedPrivateMedia,
            ExternalMetadataPolicy.checkOnDeviceTagRead(secretId, isMusic = true),
        )

        val viewOnceItem = telegramAudio(viewOnce = true)
        assertFalse(ExternalMetadataPolicy.allowsExternalLookup(viewOnceItem, protectedChat = false, externalLookupOptIn = true))
    }

    @Test
    fun sanitizedExternalQueryNeverContainsTelegramOrAccountIds() {
        val id = TrackIdentity.fromPlaybackItem(telegramAudio(), protectedChat = false)
        val query = ExternalMetadataPolicy.sanitizedExternalQuery(id)!!
        assertEquals("Track Title", query.title)
        assertEquals("Artist", query.artist)
        assertEquals("Album", query.album)
        assertEquals(215, query.durationSeconds)
        assertTrue(ExternalMetadataPolicy.allowsExternalLookup(telegramAudio(), protectedChat = false, externalLookupOptIn = true))
    }
}
