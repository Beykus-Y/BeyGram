package com.beykus.beygram.data.music.bgmusic

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Reads `.bgmusic` files for the chat. A file is read once per [BgMusicCacheKey] (account, file id and size), and the result stays
 * in a small LRU cache. A cover is written into the cache folder, so it can be shown like any other artwork file.
 */
object BgMusicFileParser {
    private const val COVER_DIRECTORY = "bgmusic_covers"
    private const val CACHE_ENTRIES = 8

    private val cache = BgMusicLruCache<BgMusicCacheKey, BgMusicParsed>(CACHE_ENTRIES)

    /** The result for [key] when the file was read before. Never does I/O. */
    fun cached(key: BgMusicCacheKey): BgMusicParsed? = cache.get(key)

    /**
     * The result for the file at [file]: read on the first call, served from the cache afterwards. Blocking I/O, so call it from
     * Dispatchers.IO. A file that cannot be opened, or that failed with an I/O error, is reported as [BgMusicParsed.Invalid] but
     * is not cached, so a later visit reads it again.
     */
    fun parse(context: Context, key: BgMusicCacheKey, file: File): BgMusicParsed {
        cache.get(key)?.let { return it }
        val result = try {
            file.inputStream().use { input -> BgMusicReader.read(input, declaredSize = file.length()) }
        } catch (unreadable: IOException) {
            return BgMusicParsed.Invalid
        }
        if (result is BgMusicReadResult.Invalid && result.reason == BgMusicInvalidReason.IoError) {
            return BgMusicParsed.Invalid
        }
        val parsed = when (result) {
            is BgMusicReadResult.Success -> BgMusicParsed.Playlist(result.manifest, coverFile(context, key, result.cover))
            is BgMusicReadResult.Unsupported -> BgMusicParsed.Unsupported(result.formatVersion, result.minReaderVersion)
            is BgMusicReadResult.Invalid -> BgMusicParsed.Invalid
        }
        cache.put(key, parsed)
        return parsed
    }

    /** Writes [cover] for [key] unless the same bytes are already there, and returns its path, or null without a cover. */
    private fun coverFile(context: Context, key: BgMusicCacheKey, cover: ByteArray?): String? {
        if (cover == null) return null
        val extension = when (BgMusicFormat.coverEntryNameFor(cover)) {
            BgMusicFormat.COVER_WEBP_ENTRY -> "webp"
            BgMusicFormat.COVER_PNG_ENTRY -> "png"
            else -> "jpg"
        }
        val account = key.accountId.replace(Regex("[^A-Za-z0-9]"), "_")
        return try {
            val directory = File(context.cacheDir, COVER_DIRECTORY).apply { mkdirs() }
            val target = File(directory, "${account}_${key.fileId}_${key.sizeBytes}.$extension")
            if (!target.isFile || target.length() != cover.size.toLong()) target.writeBytes(cover)
            target.absolutePath
        } catch (failure: IOException) {
            null
        }
    }
}
