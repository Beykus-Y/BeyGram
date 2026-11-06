package com.beykus.beygram.playback

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Silence measurements of Telegram music files, kept across launches. A record is keyed by account and TDLib file id,
 * and it is trusted only while the file still has the length it was measured with, so a replaced file is measured
 * again. The newest records are kept and the oldest are dropped.
 */
internal object SilenceEdgeStore {
    private const val PREFS_NAME = "silence_edges"
    private const val FORMAT = 1
    private const val FIELD_COUNT = 6
    private const val MAX_RECORDS = 4_000
    private const val PRUNE_TO = 3_000

    private val records = ConcurrentHashMap<String, Record>()

    @Volatile
    private var loaded = false

    private data class Record(val edges: SilenceEdges, val fileLength: Long, val analyzedAtMs: Long)

    /** Reads the saved records once. [lookup] answers null until this has run. Call it from a background thread. */
    fun warm(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            prefs(context)?.all?.forEach { (key, value) ->
                (value as? String)?.let { decode(it) }?.let { records[key] = it }
            }
            loaded = true
        }
    }

    /** Known edges for [key] while the file still has [fileLength] bytes, otherwise null. */
    fun lookup(key: String, fileLength: Long): SilenceEdges? {
        val record = records[key] ?: return null
        return record.edges.takeIf { record.fileLength == fileLength }
    }

    fun put(context: Context, key: String, edges: SilenceEdges, fileLength: Long, nowMs: Long) {
        val record = Record(edges, fileLength, nowMs)
        records[key] = record
        prefs(context)?.edit()?.putString(key, encode(record))?.apply()
        if (records.size > MAX_RECORDS) prune(context)
    }

    private fun prune(context: Context) {
        val dropped = records.entries
            .sortedBy { it.value.analyzedAtMs }
            .take(records.size - PRUNE_TO)
            .map { it.key }
        dropped.forEach { records.remove(it) }
        val editor = prefs(context)?.edit() ?: return
        dropped.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun prefs(context: Context): SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: RuntimeException) {
        null
    }

    private fun encode(record: Record): String = listOf(
        FORMAT.toLong(),
        record.edges.leadingMs,
        record.edges.trailingMs,
        record.edges.durationMs,
        record.fileLength,
        record.analyzedAtMs
    ).joinToString(",")

    private fun decode(value: String): Record? {
        val parts = value.split(',')
        if (parts.size != FIELD_COUNT || parts[0] != FORMAT.toString()) return null
        val numbers = parts.drop(1).map { it.toLongOrNull() ?: return null }
        return Record(
            edges = SilenceEdges(leadingMs = numbers[0], trailingMs = numbers[1], durationMs = numbers[2]),
            fileLength = numbers[3],
            analyzedAtMs = numbers[4]
        )
    }
}
