package com.beykus.beygram.ui.screens.chat

/**
 * An access-ordered map that drops its least recently used entries beyond [maxSize]. Reads and writes both count
 * as use. It is not thread-safe: the chat screen uses it from the main thread only.
 */
internal class BoundedLruMap<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
}

/**
 * How many media bubble window bounds the chat keeps. A viewer always opens from a bubble that was laid out just
 * before the tap, so only the newest bubbles matter; a few hundred cover every screen with room to spare.
 */
internal const val MEDIA_ORIGIN_LIMIT = 200
