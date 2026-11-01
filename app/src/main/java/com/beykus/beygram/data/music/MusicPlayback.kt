package com.beykus.beygram.data.music

import android.content.Context
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.playback.resolveMusicOrigins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves each playlist entry against the account's current TDLib state. The result has one slot per entry, in order:
 * a null slot is an entry that could not be resolved now. Nothing is dropped from the playlist because of it.
 */
suspend fun resolvePlaylistTracks(
    repository: TelegramRepository,
    accountId: String,
    tracks: List<PlaylistTrack>
): List<PlaybackItem?> = resolveMusicOrigins(repository, accountId, tracks.map { it.origin })

/**
 * Outcome of [playPlaylist].
 *
 * @property started whether the player received a queue and started it.
 * @property queueSize number of playable entries in the queue copy.
 * @property unavailableIndices playlist positions that could not be resolved. The playlist itself is unchanged.
 */
data class PlaylistPlayResult(
    val started: Boolean,
    val queueSize: Int,
    val unavailableIndices: List<Int>
)

/**
 * Plays [playlist] from [startIndex]. The queue is a copy of the playable entries in playlist order, so later queue edits
 * never write back into the playlist. An unresolvable start moves to the next playable entry, then to the previous one.
 *
 * [shuffle] changes the shuffle mode only when it is not null; repeat and the other modes are left as they are.
 * Resolving every entry can take a while for long playlists, so call this from a coroutine, not from a composition.
 */
suspend fun playPlaylist(
    context: Context,
    telegramRepository: TelegramRepository,
    accountId: String,
    playlist: MusicPlaylist,
    startIndex: Int,
    shuffle: Boolean?
): PlaylistPlayResult {
    val resolved = resolvePlaylistTracks(telegramRepository, accountId, playlist.tracks)
    val unavailable = resolved.indices.filter { resolved[it] == null }
    if (startIndex !in resolved.indices) return PlaylistPlayResult(false, 0, unavailable)
    val start = (startIndex until resolved.size).firstOrNull { resolved[it] != null }
        ?: (startIndex - 1 downTo 0).firstOrNull { resolved[it] != null }
        ?: return PlaylistPlayResult(false, 0, unavailable)
    // The queue is a new list. The player may reorder or trim it without touching the playlist.
    val queue: List<PlaybackItem> = resolved.filterNotNull()
    val queueIndex = resolved.subList(0, start).count { it != null }
    val source = PlaybackQueueSource(
        accountId,
        telegramRepository::observeFile,
        telegramRepository::downloadFile,
        telegramRepository::autoDownloadFile
    )
    withContext(Dispatchers.Main.immediate) {
        if (shuffle != null) PlaybackEngine.setShuffle(shuffle)
        PlaybackEngine.playQueue(context, queue, queueIndex, source)
    }
    return PlaylistPlayResult(started = true, queueSize = queue.size, unavailableIndices = unavailable)
}
