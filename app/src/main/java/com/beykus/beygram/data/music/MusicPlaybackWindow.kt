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
 * order gets the entries within [MUSIC_PLAY_WINDOW_RADIUS] of [startIndex]. The rest follows a page at a time (see
 * [musicPlayLaterPages]), so every entry ends up in the queue.
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
 * The pages of a long playlist after its first [window], in the order they join the queue: first the tail, from the end of the
 * window to the end of the playlist, then the head, everything before the window, from the start of the playlist. The window's
 * own entries are not included: they are queued with the window. Each page holds at most [MUSIC_PLAY_PAGE_SIZE]
 * positions. Empty when the window holds the whole playlist.
 */
fun musicPlayLaterPages(size: Int, window: MusicPlayRange): List<MusicPlayRange> {
    val pages = ArrayList<MusicPlayRange>()
    addPages(pages, from = window.endExclusive.coerceIn(0, size), until = size)
    addPages(pages, from = 0, until = window.start.coerceIn(0, size))
    return pages
}

/** Appends the pages that cover the positions [from, until), in order. */
private fun addPages(into: MutableList<MusicPlayRange>, from: Int, until: Int) {
    var cursor = from
    while (cursor < until) {
        val page = musicPlayPage(cursor, until)
        into += page
        cursor = page.endExclusive
    }
}

/**
 * Hands out the pages of a long playlist in order. A page counts as taken only once [advance] is called for it, so a page whose
 * lookup was cancelled is requested again. Callers serialize access.
 */
internal class MusicPlayPager(private val pages: List<MusicPlayRange>) {
    private var next = 0

    fun hasMore(): Boolean = next < pages.size

    /** The page that comes next. Calling it again returns the same page until [advance] takes it. Call it only while [hasMore]. */
    fun peek(): MusicPlayRange = pages[next]

    /** Takes [page], which must be the page [peek] returned last. */
    fun advance(page: MusicPlayRange) {
        require(hasMore() && pages[next] == page) { "the page starting at ${page.start} does not come next" }
        next++
    }
}
