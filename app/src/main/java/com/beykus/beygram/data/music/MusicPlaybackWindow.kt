package com.beykus.beygram.data.music

/** Playlists of at most this many entries are resolved in full before playback starts, as they always were. */
const val MUSIC_PLAY_FULL_RESOLVE_LIMIT = 60

/** Entries kept on each side of the start index in the first window of a long playlist. */
const val MUSIC_PLAY_WINDOW_RADIUS = 20

/** Entries resolved per page once playback reaches the end of the first window of a long playlist. */
const val MUSIC_PLAY_PAGE_SIZE = 30

/** Playlist positions [start, endExclusive). */
data class MusicPlayRange(val start: Int, val endExclusive: Int) {
    val size: Int get() = endExclusive - start
}

/**
 * The playlist positions resolved before playback starts. A playlist of at most [MUSIC_PLAY_FULL_RESOLVE_LIMIT] entries, and
 * any playlist played shuffled, is resolved in full, because shuffle has to see the whole list. A longer playlist played in
 * order gets the entries within [MUSIC_PLAY_WINDOW_RADIUS] of [startIndex]. Entries before that window are not queued: a
 * queue only grows at its end, so they would have to be played after the last entry.
 */
fun musicPlayWindow(size: Int, startIndex: Int, shuffled: Boolean): MusicPlayRange {
    if (size <= 0) return MusicPlayRange(0, 0)
    if (shuffled || size <= MUSIC_PLAY_FULL_RESOLVE_LIMIT) return MusicPlayRange(0, size)
    val start = startIndex.coerceIn(0, size - 1)
    return MusicPlayRange(
        start = (start - MUSIC_PLAY_WINDOW_RADIUS).coerceAtLeast(0),
        endExclusive = (start + MUSIC_PLAY_WINDOW_RADIUS + 1).coerceAtMost(size)
    )
}

/**
 * The page that starts at [cursor]: up to [MUSIC_PLAY_PAGE_SIZE] positions, never past [size]. Empty once [cursor] has
 * reached [size].
 */
fun musicPlayPage(cursor: Int, size: Int): MusicPlayRange {
    val start = cursor.coerceAtLeast(0)
    if (start >= size) return MusicPlayRange(size, size)
    return MusicPlayRange(start, minOf(size, start + MUSIC_PLAY_PAGE_SIZE))
}

/**
 * Walks the playlist positions after the first window in playlist order, one page at a time. A page counts as taken only
 * once [advance] is called for it, so a page whose lookup was cancelled is simply requested again. Callers serialize access.
 */
internal class MusicPlayPager(private val size: Int, startCursor: Int) {
    private var cursor = startCursor.coerceAtLeast(0)

    fun hasMore(): Boolean = cursor < size

    /** The page that comes next. Calling it again returns the same page until [advance] takes it. */
    fun peek(): MusicPlayRange = musicPlayPage(cursor, size)

    /** Takes [page], which must be the page [peek] returned last. */
    fun advance(page: MusicPlayRange) {
        require(page.start == cursor) { "page ${page.start} does not follow cursor $cursor" }
        cursor = page.endExclusive
    }
}
