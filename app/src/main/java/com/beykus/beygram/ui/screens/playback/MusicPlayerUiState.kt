package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.playback.PlaybackState
import kotlin.math.roundToInt

/**
 * The stable part of the shared player state used by the mini-player. Position and buffered progress are
 * deliberately absent: they change four times a second and are read by a tiny progress composable instead of
 * recomposing the whole bar.
 */
internal data class MiniPlayerUi(
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkPath: String?,
    val audioPath: String?,
    val isMusic: Boolean,
    val playing: Boolean,
    val intentPlaying: Boolean,
    val buffering: Boolean,
    val failed: Boolean,
    val ended: Boolean,
    val canNext: Boolean,
    val durationSeconds: Int,
    val origin: PlaybackOrigin?
)

/** The displayed music title without the legacy "Artist — Title" join. */
internal fun PlaybackItem.musicTitle(): String = trackTitle.ifBlank { title }

/** A separate artist line; empty when the file has no artist tag. */
internal fun PlaybackItem.musicArtist(): String = performer.trim()

/** The mini-player exists exactly while the shared player has an item, including voice and video notes. */
internal fun PlaybackState.miniPlayerVisible(): Boolean = item != null

internal fun PlaybackState.miniPlayerUi(): MiniPlayerUi? {
    val current = item ?: return null
    return MiniPlayerUi(
        key = current.key,
        title = if (current.isMusic) current.musicTitle() else current.title,
        artist = if (current.isMusic) current.musicArtist() else "",
        album = if (current.isMusic) current.album else "",
        artworkPath = current.artworkPath.takeIf { current.isMusic },
        audioPath = current.path.takeIf { current.isMusic && it.isNotBlank() },
        isMusic = current.isMusic,
        playing = playing,
        intentPlaying = intentPlaying,
        buffering = isBuffering,
        failed = failed,
        ended = ended,
        canNext = canNext,
        durationSeconds = current.durationSeconds,
        origin = current.origin
    )
}

/** Stable full-player state: artwork, metadata, transport availability and modes, never the playhead. */
internal data class MusicPlayerUi(
    val item: PlaybackItem,
    val playing: Boolean,
    val intentPlaying: Boolean,
    val buffering: Boolean,
    val failed: Boolean,
    val ended: Boolean,
    val canNext: Boolean,
    val canPrevious: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: PlaybackRepeatMode,
    val speed: Float,
    val queueSize: Int,
    val queueIndex: Int
) {
    val hasQueue: Boolean get() = queueSize > 1
}

internal fun PlaybackState.musicPlayerUi(): MusicPlayerUi? {
    val current = item?.takeIf { it.isMusic } ?: return null
    return MusicPlayerUi(
        item = current,
        playing = playing,
        intentPlaying = intentPlaying,
        buffering = isBuffering,
        failed = failed,
        ended = ended,
        canNext = canNext,
        canPrevious = canPrevious,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        speed = musicSpeed,
        queueSize = queue.size,
        queueIndex = queueIndex
    )
}

internal enum class MusicPlaybackStatus {
    Ready,
    NotDownloaded,
    Downloading,
    Buffering,
    Playing,
    Paused,
    Ended,
    Failed,
    Unavailable
}

/** Stable per-card state; the card keeps its own file/download state and only this changes on transport events. */
internal data class MusicCardPlayback(
    val isCurrent: Boolean = false,
    val playing: Boolean = false,
    val intentPlaying: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val ended: Boolean = false
)

internal fun PlaybackState.musicCardPlayback(key: String): MusicCardPlayback {
    val current = item?.key == key
    return if (!current) {
        MusicCardPlayback()
    } else {
        MusicCardPlayback(
            isCurrent = true,
            playing = playing,
            intentPlaying = intentPlaying,
            buffering = isBuffering,
            failed = failed,
            ended = ended
        )
    }
}

/**
 * One mapping for the music bubble. The transport state wins over download flags for the current track:
 * a track that is already buffering must say "buffering", not "downloading", even if TDLib is still writing it.
 */
internal fun musicCardStatus(
    playback: MusicCardPlayback,
    isDownloading: Boolean,
    waitingForDownload: Boolean,
    hasPlayableSource: Boolean,
    downloadFailed: Boolean
): MusicPlaybackStatus {
    if (playback.isCurrent) {
        return when {
            playback.failed -> MusicPlaybackStatus.Failed
            playback.ended -> MusicPlaybackStatus.Ended
            playback.playing -> MusicPlaybackStatus.Playing
            playback.intentPlaying -> MusicPlaybackStatus.Buffering
            else -> MusicPlaybackStatus.Paused
        }
    }
    return when {
        downloadFailed -> MusicPlaybackStatus.Unavailable
        isDownloading -> MusicPlaybackStatus.Downloading
        waitingForDownload -> MusicPlaybackStatus.Buffering
        !hasPlayableSource -> MusicPlaybackStatus.NotDownloaded
        else -> MusicPlaybackStatus.Ready
    }
}

internal enum class QueueItemStatus {
    CurrentPlaying,
    CurrentPaused,
    CurrentBuffering,
    CurrentEnded,
    CurrentFailed,
    Loading,
    Ready,
    Unavailable
}

internal data class QueueItemUi(
    /** Index in PlaybackState.queue; -1 for a single track that has no queue. */
    val index: Int,
    val item: PlaybackItem,
    val isCurrent: Boolean,
    val status: QueueItemStatus
)

internal data class MusicQueueUi(
    val rows: List<QueueItemUi>,
    val currentIndex: Int,
    val current: QueueItemUi?,
    /**
     * Tracks after the current one in the actual playback order. With shuffle on this is derived from the Media3
     * timeline, not from the source tail; the source index of each row stays available for the "No. N" marker.
     */
    val upcoming: List<QueueItemUi>,
    val shuffleEnabled: Boolean,
    val origin: PlaybackOrigin?,
    val repeatAll: Boolean = false,
    /** True only when [upcoming] came from a valid Media3 shuffle order. */
    val playbackOrderApplied: Boolean = false
) {
    val size: Int get() = rows.size
    val loadingCount: Int get() = rows.count { it.status == QueueItemStatus.Loading }
}

/**
 * Builds the queue panel from the original source order. [playbackOrder] is a list of original queue indices in the
 * exact order Media3 will play them; when supplied, "Next" follows that order instead of the source tail.
 */
internal fun PlaybackState.musicQueueUi(playbackOrder: List<Int>? = null): MusicQueueUi? {
    val current = item?.takeIf { it.isMusic } ?: return null
    if (queue.isEmpty()) {
        val single = QueueItemUi(
            index = -1,
            item = current,
            isCurrent = true,
            status = currentQueueStatus()
        )
        return MusicQueueUi(
            rows = listOf(single),
            currentIndex = 0,
            current = single,
            upcoming = emptyList(),
            shuffleEnabled = shuffleEnabled,
            origin = current.origin
        )
    }
    val index = queueIndex.takeIf { it in queue.indices }
        ?: queue.indexOfFirst { it.key == current.key }
    if (index !in queue.indices) {
        val single = QueueItemUi(-1, current, true, currentQueueStatus())
        return MusicQueueUi(listOf(single), 0, single, emptyList(), shuffleEnabled, current.origin)
    }
    val rows = queue.mapIndexed { queueIndex, entry ->
        val isCurrent = queueIndex == index
        QueueItemUi(
            index = queueIndex,
            item = entry,
            isCurrent = isCurrent,
            status = if (isCurrent) currentQueueStatus() else upcomingQueueStatus(entry)
        )
    }
    val currentRow = rows[index]
    val base = MusicQueueUi(
        rows = rows,
        currentIndex = index,
        current = currentRow,
        upcoming = rows.drop(index + 1),
        shuffleEnabled = shuffleEnabled,
        origin = current.origin,
        repeatAll = repeatMode == PlaybackRepeatMode.All
    )
    return base.withPlaybackOrder(playbackOrder)
}

/**
 * Replaces the source-tail "Next" list with the actual playback sequence. Invalid/partial orders fall back to the
 * source order so a broken timeline dump can never hide the queue. The current row and the original rows stay intact.
 */
internal fun MusicQueueUi.withPlaybackOrder(playbackOrder: List<Int>?): MusicQueueUi {
    if (playbackOrder == null || !shuffleEnabled) return this
    if (playbackOrder.size != rows.size || playbackOrder.distinct().size != rows.size) return this
    if (playbackOrder.any { it !in rows.indices }) return this
    val following = followingPlaybackIndices(playbackOrder, currentIndex, repeatAll)
    val byIndex = rows.associateBy { it.index }
    return copy(
        upcoming = following.mapNotNull(byIndex::get),
        playbackOrderApplied = true
    )
}

/**
 * Original queue indices that Media3 plays after [currentIndex]. With repeat-all the sequence wraps, matching the
 * player's behaviour; repeat-one still lists the manual-Next sequence.
 */
internal fun followingPlaybackIndices(
    playbackOrder: List<Int>,
    currentIndex: Int,
    repeatAll: Boolean
): List<Int> {
    if (playbackOrder.isEmpty() || currentIndex !in playbackOrder) return emptyList()
    val position = playbackOrder.indexOf(currentIndex)
    val after = playbackOrder.drop(position + 1)
    if (!repeatAll || playbackOrder.size <= 1) return after
    return after + playbackOrder.take(position)
}

private fun PlaybackState.currentQueueStatus(): QueueItemStatus = when {
    failed -> QueueItemStatus.CurrentFailed
    ended -> QueueItemStatus.CurrentEnded
    playing -> QueueItemStatus.CurrentPlaying
    intentPlaying || isBuffering -> QueueItemStatus.CurrentBuffering
    else -> QueueItemStatus.CurrentPaused
}

private fun upcomingQueueStatus(item: PlaybackItem): QueueItemStatus = when {
    item.systemPrivate -> QueueItemStatus.Unavailable
    item.path.isNotBlank() || item.growing != null -> QueueItemStatus.Ready
    item.fileId != null -> QueueItemStatus.Loading
    else -> QueueItemStatus.Unavailable
}

/**
 * Clamp a reordered row to the "Next" section. The playing row and everything above it keep their places; a queue
 * without an upcoming item has no valid drop target. Returns -1 when the move is not allowed.
 */
internal fun queueDropTarget(from: Int, to: Int, currentIndex: Int, size: Int): Int {
    if (size <= 0 || from !in 0 until size) return -1
    val firstMovable = (currentIndex + 1).coerceAtLeast(0)
    if (firstMovable > size - 1 || from < firstMovable) return -1
    return to.coerceIn(firstMovable, size - 1)
}

/** Translate a vertical drag on a fixed-height queue row into a destination queue index. */
internal fun queueDragTarget(
    from: Int,
    dragPx: Float,
    rowHeightPx: Float,
    currentIndex: Int,
    size: Int
): Int {
    if (rowHeightPx <= 0f) return from
    val steps = (dragPx / rowHeightPx).roundToInt()
    val target = queueDropTarget(from, from + steps, currentIndex, size)
    return if (target >= 0) target else from
}

internal enum class PlaybackSourceKind {
    Local,
    SavedMessages,
    Channel,
    Group,
    Chat
}

/** What the "Playing from" header should call the current source. */
internal fun playbackSourceKind(
    hasOrigin: Boolean,
    isSavedMessages: Boolean,
    isChannel: Boolean,
    isGroup: Boolean
): PlaybackSourceKind = when {
    !hasOrigin -> PlaybackSourceKind.Local
    isSavedMessages -> PlaybackSourceKind.SavedMessages
    isChannel -> PlaybackSourceKind.Channel
    isGroup -> PlaybackSourceKind.Group
    else -> PlaybackSourceKind.Chat
}

/** Elapsed time of the player and of the queue rows, consistent across both screens. */
internal fun playbackClock(positionMs: Long): String {
    val seconds = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun playbackRemaining(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return playbackClock(0L)
    return "-" + playbackClock(durationMs - positionMs.coerceAtLeast(0L))
}

/** Music card cover size: compact enough for a 360 dp bubble, large enough for artwork to read. */
internal const val MUSIC_CARD_ARTWORK_DP = 48
