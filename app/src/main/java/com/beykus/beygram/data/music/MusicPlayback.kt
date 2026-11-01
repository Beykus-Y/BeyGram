package com.beykus.beygram.data.music

import android.content.Context
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.playback.resolveMusicOrigins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * @property queueSize number of playable entries in the queue handed to the player when playback started. Entries that are
 *   resolved later, page by page, are not counted.
 * @property unavailableIndices playlist positions among the entries resolved before playback started that could not be
 *   resolved. The playlist itself is unchanged. Unavailable entries of later pages are skipped without being reported.
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
 * A long playlist played in order resolves only a window around [startIndex] before playback starts (see [musicPlayWindow]).
 * The entries after the window are resolved a page at a time, as the player reaches the end of the queue. Entries before the
 * window are not queued. Shuffle, and a playlist of up to [MUSIC_PLAY_FULL_RESOLVE_LIMIT] entries, is resolved in full, as before.
 *
 * [shuffle] changes the shuffle mode only when it is not null; repeat and the other modes are left as they are.
 * Resolving can take a while, so call this from a coroutine, not from a composition.
 */
suspend fun playPlaylist(
    context: Context,
    telegramRepository: TelegramRepository,
    accountId: String,
    playlist: MusicPlaylist,
    startIndex: Int,
    shuffle: Boolean?
): PlaylistPlayResult {
    val tracks = playlist.tracks
    if (startIndex !in tracks.indices) return PlaylistPlayResult(false, 0, emptyList())
    // A shuffled queue that grows later would only shuffle its window, so shuffle keeps the whole list. Without a change of
    // mode, the mode the player already has decides.
    val shuffled = shuffle ?: PlaybackEngine.state.value.shuffleEnabled
    val window = musicPlayWindow(tracks.size, startIndex, shuffled)
    val resolved = resolvePlaylistTracks(telegramRepository, accountId, tracks.subList(window.start, window.endExclusive))
    val unavailable = resolved.indices.filter { resolved[it] == null }.map { window.start + it }
    val pages = if (window.endExclusive < tracks.size) {
        LazyPlaylistPages(telegramRepository, accountId, tracks, window.endExclusive)
    } else {
        null
    }

    // The queue is a new list. The player may reorder or trim it without touching the playlist.
    val queue = ArrayList<PlaybackItem>(resolved.filterNotNull())
    val relativeStart = startIndex - window.start
    val forward = (relativeStart until resolved.size).firstOrNull { resolved[it] != null }
    var queueIndex = -1
    if (forward != null) {
        queueIndex = resolved.subList(0, forward).count { it != null }
    } else if (pages != null) {
        // Nothing playable from the start on inside the window: the first later page with a playable entry starts playback.
        // Its entries come after the window's own entries, which all lie before the start.
        val page = pages.nextPlayablePage()
        if (page.isNotEmpty()) {
            queueIndex = queue.size
            queue += page
        }
    }
    if (queueIndex < 0) {
        val backward = (relativeStart - 1 downTo 0).firstOrNull { resolved[it] != null }
            ?: return PlaylistPlayResult(started = false, queueSize = 0, unavailableIndices = unavailable)
        queueIndex = resolved.subList(0, backward).count { it != null }
    }
    val queueCopy: List<PlaybackItem> = queue.toList()
    if (queueIndex !in queueCopy.indices) return PlaylistPlayResult(started = false, queueSize = 0, unavailableIndices = unavailable)

    val loadMore: (suspend () -> List<PlaybackItem>)? = pages?.let { later -> suspend { later.nextPlayablePage() } }
    val source = PlaybackQueueSource(
        accountId,
        telegramRepository::observeFile,
        telegramRepository::downloadFile,
        telegramRepository::autoDownloadFile,
        loadMore = loadMore
    )
    withContext(Dispatchers.Main.immediate) {
        if (shuffle != null) PlaybackEngine.setShuffle(shuffle)
        PlaybackEngine.playQueue(context, queueCopy, queueIndex, source)
    }
    return PlaylistPlayResult(started = true, queueSize = queueCopy.size, unavailableIndices = unavailable)
}

/**
 * The entries of a long playlist after its first window, resolved a page at a time. Pages are taken in playlist order. A page
 * without a playable entry is passed over, so [nextPlayablePage] is empty only once the rest of the playlist has none.
 * Each lookup runs against the snapshot of the playlist that was played, so later edits to the playlist do not reach it.
 */
internal class LazyPlaylistPages(
    private val telegramRepository: TelegramRepository,
    private val accountId: String,
    private val tracks: List<PlaylistTrack>,
    startCursor: Int
) {
    private val lock = Mutex()
    private val pager = MusicPlayPager(tracks.size, startCursor)

    /** The playable entries of the next page that has any, in playlist order. */
    suspend fun nextPlayablePage(): List<PlaybackItem> = lock.withLock {
        var playable: List<PlaybackItem> = emptyList()
        while (playable.isEmpty() && pager.hasMore()) {
            val page = pager.peek()
            val resolved = resolvePlaylistTracks(telegramRepository, accountId, tracks.subList(page.start, page.endExclusive))
            playable = resolved.filterNotNull()
            pager.advance(page)
        }
        playable
    }
}
