package com.beykus.beygram.data.music.bgmusic

/**
 * Identity of a parsed file in the chat cache. The account comes first because TDLib file ids are only unique inside one
 * account. The size is part of the key, so a file that TDLib replaces under the same id is parsed again.
 */
data class BgMusicCacheKey(val accountId: String, val fileId: Int, val sizeBytes: Long)

/** What the chat needs to know about a `.bgmusic` file after reading it once. */
sealed interface BgMusicParsed {
    /** A readable playlist. [coverPath] is a cache file holding the cover image, or null when the file has none. */
    data class Playlist(val manifest: BgMusicManifest, val coverPath: String?) : BgMusicParsed

    /** Needs a newer BeyGram. The chat shows a note and the ordinary document row. */
    data class Unsupported(val formatVersion: Int, val minReaderVersion: Int) : BgMusicParsed

    /** Not a `.bgmusic` file, or damaged. The chat shows the ordinary document row. */
    data object Invalid : BgMusicParsed
}

/**
 * A small least-recently-used map. Reading an entry counts as using it. The map holds at most [maxEntries] values and drops
 * the least recently used one first. All access is synchronized, so the parser threads and the UI can share it.
 */
class BgMusicLruCache<K : Any, V : Any>(private val maxEntries: Int) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val lock = Any()
    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
    }

    fun get(key: K): V? = synchronized(lock) { entries[key] }

    fun put(key: K, value: V) {
        synchronized(lock) { entries[key] = value }
    }

    fun size(): Int = synchronized(lock) { entries.size }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}
