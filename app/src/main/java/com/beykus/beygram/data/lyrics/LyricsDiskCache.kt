package com.beykus.beygram.data.lyrics

import com.beykus.beygram.domain.lyrics.LyricWord
import com.beykus.beygram.domain.lyrics.LyricsAttribution
import com.beykus.beygram.domain.lyrics.LyricsDocument
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.lyrics.RichLyricLine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class DiskCacheEntry(
    val document: LyricsDocument,
    val storedAtMs: Long,
    val ttlMs: Long,
)

/**
 * Bounded, versioned JSON disk cache for [LyricsDocument] entries.
 * Isolated by account-scoped cache keys and guarded by a generation counter on [clear].
 */
class LyricsDiskCache(
    private val rootDir: File,
    private val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    private val maxEntryBytes: Int = MAX_ENTRY_BYTES,
) {
    private val lock = Any()
    @Volatile
    private var generation = 0

    fun currentGeneration(): Int = generation

    fun get(cacheKey: String, nowMs: Long): DiskCacheEntry? {
        val file = entryFile(cacheKey)
        synchronized(lock) {
            if (!file.isFile) return null
            val raw = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return null
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: run {
                file.delete()
                return null
            }
            val version = obj.optInt("formatVersion", -1)
            val storedAt = obj.optLong("storedAtMs", 0L)
            val ttl = obj.optLong("ttlMs", 0L)
            if (version != LyricsDocument.CURRENT_FORMAT_VERSION || storedAt <= 0L || nowMs - storedAt >= ttl) {
                file.delete()
                return null
            }
            val docObj = obj.optJSONObject("document") ?: run {
                file.delete()
                return null
            }
            val doc = deserializeDocument(docObj) ?: run {
                file.delete()
                return null
            }
            file.setLastModified(nowMs)
            return DiskCacheEntry(document = doc, storedAtMs = storedAt, ttlMs = ttl)
        }
    }

    fun put(
        cacheKey: String,
        document: LyricsDocument,
        ttlMs: Long,
        nowMs: Long,
        expectedGeneration: Int = generation,
    ) {
        if (ttlMs <= 0L) return
        val payload = JSONObject()
            .put("formatVersion", LyricsDocument.CURRENT_FORMAT_VERSION)
            .put("storedAtMs", nowMs)
            .put("ttlMs", ttlMs)
            .put("document", serializeDocument(document))
            .toString()
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > maxEntryBytes) return

        synchronized(lock) {
            if (generation != expectedGeneration) return
            rootDir.mkdirs()
            val target = entryFile(cacheKey)
            val temp = File(rootDir, target.name + ".tmp")
            val ok = runCatching {
                temp.writeBytes(bytes)
                if (target.exists()) target.delete()
                temp.renameTo(target)
            }.getOrDefault(false)
            if (ok) {
                target.setLastModified(nowMs)
                trimLocked()
            } else {
                temp.delete()
            }
        }
    }

    fun remove(cacheKey: String) {
        synchronized(lock) {
            entryFile(cacheKey).delete()
        }
    }

    fun clear() {
        synchronized(lock) {
            generation += 1
            rootDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun trimLocked() {
        val files = rootDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxTotalBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun entryFile(cacheKey: String): File {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(cacheKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(rootDir, "$hash.json")
    }

    private fun serializeDocument(doc: LyricsDocument): JSONObject {
        val linesArray = JSONArray()
        for (line in doc.lines) {
            val wordsArray = JSONArray()
            for (word in line.words) {
                wordsArray.put(
                    JSONObject()
                        .put("s", word.startMs)
                        .put("e", word.endMs)
                        .put("sc", word.startCharIndex)
                        .put("ec", word.endCharIndexExclusive)
                        .put("bg", word.isBackgroundVocal)
                )
            }
            linesArray.put(
                JSONObject()
                    .put("s", line.startMs ?: JSONObject.NULL)
                    .put("e", line.endMs ?: JSONObject.NULL)
                    .put("t", line.text)
                    .put("inst", line.isInstrumental)
                    .put("bg", line.isBackgroundVocal)
                    .put("w", wordsArray)
                    .put("tr", line.translation ?: JSONObject.NULL)
                    .put("ro", line.romanization ?: JSONObject.NULL)
            )
        }
        val attrObj = JSONObject()
            .put("id", doc.attribution.sourceId.name)
            .put("name", doc.attribution.sourceDisplayName)
            .put("holder", doc.attribution.copyrightHolder ?: JSONObject.NULL)
            .put("ext", doc.attribution.isExternalLookup)
        return JSONObject()
            .put("mode", doc.syncMode.name)
            .put("lines", linesArray)
            .put("plain", doc.plainText ?: JSONObject.NULL)
            .put("attr", attrObj)
            .put("instTrack", doc.isInstrumentalTrack)
    }

    private fun deserializeDocument(obj: JSONObject): LyricsDocument? {
        val mode = runCatching { LyricsSyncMode.valueOf(obj.optString("mode")) }.getOrNull() ?: return null
        val attrObj = obj.optJSONObject("attr") ?: return null
        val sourceId = runCatching { LyricsSourceId.valueOf(attrObj.optString("id")) }.getOrNull() ?: return null
        val sourceName = attrObj.optString("name").takeIf { it.isNotBlank() } ?: return null
        val holder = attrObj.optString("holder").takeIf { it.isNotBlank() && it != "null" }
        val attribution = LyricsAttribution(
            sourceId = sourceId,
            sourceDisplayName = sourceName,
            copyrightHolder = holder,
            isExternalLookup = attrObj.optBoolean("ext", false),
        )
        val linesArray = obj.optJSONArray("lines") ?: JSONArray()
        val lines = ArrayList<RichLyricLine>(linesArray.length())
        for (i in 0 until linesArray.length()) {
            val lObj = linesArray.optJSONObject(i) ?: continue
            val text = lObj.optString("t")
            val startMs = if (lObj.isNull("s")) null else lObj.optLong("s", 0L)
            val endMs = if (lObj.isNull("e")) null else lObj.optLong("e", 0L)
            val wordsArray = lObj.optJSONArray("w") ?: JSONArray()
            val words = ArrayList<LyricWord>(wordsArray.length())
            for (j in 0 until wordsArray.length()) {
                val wObj = wordsArray.optJSONObject(j) ?: continue
                LyricWord.validated(
                    lineText = text,
                    startMs = wObj.optLong("s", -1L),
                    endMs = wObj.optLong("e", -1L),
                    startCharIndex = wObj.optInt("sc", -1),
                    endCharIndexExclusive = wObj.optInt("ec", -1),
                    isBackgroundVocal = wObj.optBoolean("bg", false),
                )?.let(words::add)
            }
            lines.add(
                RichLyricLine(
                    startMs = startMs,
                    endMs = endMs,
                    text = text,
                    isInstrumental = lObj.optBoolean("inst", false),
                    isBackgroundVocal = lObj.optBoolean("bg", false),
                    words = words,
                    translation = lObj.optString("tr").takeIf { it.isNotBlank() && it != "null" },
                    romanization = lObj.optString("ro").takeIf { it.isNotBlank() && it != "null" },
                )
            )
        }
        val plain = obj.optString("plain").takeIf { it.isNotBlank() && it != "null" }
        return LyricsDocument(
            syncMode = mode,
            lines = lines,
            plainText = plain,
            attribution = attribution,
            isInstrumentalTrack = obj.optBoolean("instTrack", false),
        )
    }

    companion object {
        const val MAX_TOTAL_BYTES = 16L * 1024 * 1024
        const val MAX_ENTRY_BYTES = 256 * 1024
    }
}
