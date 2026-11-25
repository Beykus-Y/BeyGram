package com.beykus.beygram.domain.music

import com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem

/**
 * Origin category of an audio track, kept separate from any lyrics or canvas source.
 */
enum class TrackAudioSource {
    Telegram,
    LocalFile,
    YandexMusic,
    Unknown,
}

/**
 * Privacy and catalog restrictions that govern whether on-device tags or external metadata
 * lookups are permitted for a track.
 */
data class TrackPrivacyFlags(
    val systemPrivate: Boolean,
    val protectedChat: Boolean,
    val isRestrictedCatalog: Boolean,
) {
    /** External network lookups by track metadata are strictly forbidden for private or protected media. */
    val allowsExternalMetadataLookup: Boolean
        get() = !systemPrivate && !protectedChat

    /** On-device tag reads are permitted only for non-private media. */
    val allowsOnDeviceTagRead: Boolean
        get() = !systemPrivate
}

/**
 * Shared, provider-agnostic track identity used by BG Lyrics 2.0 and BG Canvas.
 * Never carries message text, chat ids, message ids, TDLib file ids, or provider tokens.
 */
data class TrackIdentity(
    val playbackKey: String,
    val accountId: String,
    val audioSource: TrackAudioSource,
    val providerTrackId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val localFilePath: String?,
    val isrc: String? = null,
    val privacy: TrackPrivacyFlags,
) {
    companion object {
        fun fromPlaybackItem(
            item: PlaybackItem,
            protectedChat: Boolean = false,
            durationOverrideMs: Long = 0L,
            isrc: String? = null,
        ): TrackIdentity {
            val audioSource = when {
                item.key.startsWith(YANDEX_TRACK_KEY_PREFIX) -> TrackAudioSource.YandexMusic
                item.key.startsWith("tg|") -> TrackAudioSource.Telegram
                item.key.startsWith("local|") -> TrackAudioSource.LocalFile
                else -> TrackAudioSource.Unknown
            }
            val providerTrackId = if (audioSource == TrackAudioSource.YandexMusic) {
                item.key.removePrefix(YANDEX_TRACK_KEY_PREFIX).takeIf { it.isNotBlank() }
            } else {
                null
            }
            val resolvedDurationMs = durationOverrideMs.takeIf { it > 0L }
                ?: (item.durationSeconds.coerceAtLeast(0) * 1_000L)
            val completedLocalPath = item.path.takeIf {
                it.isNotBlank() && item.growing == null && !it.endsWith(".temp")
            }
            return TrackIdentity(
                playbackKey = item.key,
                accountId = item.accountId,
                audioSource = audioSource,
                providerTrackId = providerTrackId,
                title = item.trackTitle.ifBlank { item.title }.trim(),
                artist = item.performer.trim(),
                album = item.album.trim(),
                durationMs = resolvedDurationMs,
                localFilePath = completedLocalPath,
                isrc = isrc?.trim()?.takeIf { it.isNotEmpty() },
                privacy = TrackPrivacyFlags(
                    systemPrivate = item.systemPrivate,
                    protectedChat = protectedChat,
                    isRestrictedCatalog = item.isRestrictedCatalog,
                ),
            )
        }
    }
}

/**
 * Minimal public metadata allowed to leave the device when external lookup is explicitly enabled.
 */
data class ExternalQueryMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
)

enum class MetadataLookupDecision {
    Allowed,
    DeniedNotMusic,
    DeniedPrivateMedia,
    DeniedProtectedChat,
    DeniedOptInRequired,
    DeniedMissingMetadata,
}

/**
 * Enforces privacy boundaries for on-device audio tag inspection and opt-in external metadata lookups.
 */
object ExternalMetadataPolicy {

    fun checkExternalLookup(
        identity: TrackIdentity,
        isMusic: Boolean = true,
        externalLookupOptIn: Boolean,
    ): MetadataLookupDecision = when {
        !isMusic -> MetadataLookupDecision.DeniedNotMusic
        identity.privacy.systemPrivate -> MetadataLookupDecision.DeniedPrivateMedia
        identity.privacy.protectedChat -> MetadataLookupDecision.DeniedProtectedChat
        !externalLookupOptIn -> MetadataLookupDecision.DeniedOptInRequired
        identity.title.isBlank() -> MetadataLookupDecision.DeniedMissingMetadata
        else -> MetadataLookupDecision.Allowed
    }

    fun checkOnDeviceTagRead(
        identity: TrackIdentity,
        isMusic: Boolean = true,
    ): MetadataLookupDecision = when {
        !isMusic -> MetadataLookupDecision.DeniedNotMusic
        !identity.privacy.allowsOnDeviceTagRead -> MetadataLookupDecision.DeniedPrivateMedia
        identity.localFilePath.isNullOrBlank() -> MetadataLookupDecision.DeniedMissingMetadata
        else -> MetadataLookupDecision.Allowed
    }

    /**
     * Convenience check directly from a [PlaybackItem] before constructing a full query.
     */
    fun allowsExternalLookup(
        item: PlaybackItem,
        protectedChat: Boolean,
        externalLookupOptIn: Boolean,
    ): Boolean {
        if (item.kind != MediaKind.Audio) return false
        val identity = TrackIdentity.fromPlaybackItem(item, protectedChat = protectedChat)
        return checkExternalLookup(
            identity = identity,
            isMusic = item.isMusic,
            externalLookupOptIn = externalLookupOptIn,
        ) == MetadataLookupDecision.Allowed
    }

    /**
     * Returns only sanitized public track metadata, or null if external lookup is disallowed or
     * the title is missing.
     */
    fun sanitizedExternalQuery(identity: TrackIdentity): ExternalQueryMetadata? {
        if (!identity.privacy.allowsExternalMetadataLookup) return null
        val cleanTitle = identity.title.trim()
        if (cleanTitle.isEmpty()) return null
        val cleanAlbum = identity.album.trim().takeIf { it.isNotEmpty() }
        val durationSec = (identity.durationMs / 1_000L).toInt().takeIf { it > 0 }
        return ExternalQueryMetadata(
            title = cleanTitle,
            artist = identity.artist.trim(),
            album = cleanAlbum,
            durationSeconds = durationSec,
        )
    }
}
