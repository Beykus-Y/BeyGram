package com.beykus.beygram.ui.screens.music.yandex

import com.beykus.beygram.data.music.provider.ProviderPlaylist

/**
 * A Yandex collection screen as a plain string, so the shared navigation can keep it without knowing Yandex:
 * `liked`, `album:<id>` or `playlist:<ownerUid>:<kind>` (owner empty for the account's own playlist).
 */
internal sealed interface YandexCollectionRef {
    data object Liked : YandexCollectionRef
    data class Album(val id: String) : YandexCollectionRef
    data class Playlist(val ownerUid: String?, val kind: String) : YandexCollectionRef

    companion object {
        const val LIKED = "liked"
        private const val ALBUM = "album:"
        private const val PLAYLIST = "playlist:"

        fun album(id: String): String = ALBUM + id

        fun playlist(playlist: ProviderPlaylist): String =
            PLAYLIST + (if (playlist.isOwn) "" else playlist.ownerUid.orEmpty()) + ":" + playlist.kind

        fun parse(ref: String): YandexCollectionRef? = when {
            ref == LIKED -> Liked
            ref.startsWith(ALBUM) -> ref.removePrefix(ALBUM).takeIf { it.isNotBlank() }?.let(::Album)
            ref.startsWith(PLAYLIST) -> {
                val rest = ref.removePrefix(PLAYLIST)
                val split = rest.lastIndexOf(':')
                if (split < 0) null else {
                    val kind = rest.substring(split + 1)
                    if (kind.isBlank()) null else Playlist(rest.substring(0, split).ifBlank { null }, kind)
                }
            }
            else -> null
        }
    }
}
