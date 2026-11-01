package com.beykus.beygram.data.music.bgmusic

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Outcome of [BgMusicReader.read]. Reading never throws. */
sealed class BgMusicReadResult {
    /** A snapshot this reader can use. [cover] holds image bytes that passed the magic-byte check, or null. */
    class Success(
        val manifest: BgMusicManifest,
        val cover: ByteArray?,
    ) : BgMusicReadResult()

    /** The file needs a newer reader. Nothing is imported and the file itself is left untouched. */
    data class Unsupported(
        val formatVersion: Int,
        val minReaderVersion: Int,
    ) : BgMusicReadResult()

    /** The file is not a valid `.bgmusic`. [detail] is a developer hint and never contains user text. */
    data class Invalid(
        val reason: BgMusicInvalidReason,
        val detail: String = "",
    ) : BgMusicReadResult()
}

/** Why a file was rejected. Each value maps to one rule in docs/formats/bgmusic-v1.md. */
enum class BgMusicInvalidReason {
    /** The compressed file or the total uncompressed content is over its limit, or the device runs out of memory. */
    TooLarge,

    /** The input is shorter than four bytes or does not start with a ZIP local file header, which an empty ZIP lacks. */
    NotZip,

    /** The ZIP structure is damaged or truncated, or it uses features v1 does not accept, such as encryption. */
    CorruptArchive,

    /**
     * The input stream failed (for example a file provider error). The file itself may be valid, so the caller
     * should offer a retry instead of reporting the file as damaged.
     */
    IoError,

    MissingManifest,

    /** An unsafe, directory or nested-archive entry name. */
    BadEntryName,

    /** The same name appears twice (ignoring case), or a second cover is present. */
    DuplicateEntry,

    TooManyEntries,
    ManifestTooLarge,
    CoverTooLarge,

    /** The manifest is not valid UTF-8, is not strict JSON, is not an object, repeats a key, or is nested too deeply. */
    BadJson,

    /** The `format` field is missing or is not `beygram.music`. */
    BadSignature,

    /** A required field is missing, has the wrong type or range, or breaks a uniqueness rule. */
    BadField,

    TooManyTracks,

    /** The cover bytes do not match the image type that the entry name declares. */
    BadCover,
}

/** Reads `.bgmusic` v1 archives from a stream. Every input produces a result; nothing escapes as an exception. */
object BgMusicReader {

    /**
     * Reads one archive from [input]. The stream is read sequentially and is never closed; the caller owns it.
     * Every input produces a result, with one exception: a [CancellationException] is rethrown, so cancellation is
     * never reported as a bad file.
     *
     * Limits are enforced while the bytes are read, never from the sizes declared in the archive. [declaredSize]
     * (for example the size reported by the file provider) is used only to reject a file before reading it.
     */
    fun read(input: InputStream, declaredSize: Long? = null): BgMusicReadResult {
        return try {
            if (declaredSize != null && declaredSize > BgMusicFormat.MAX_ARCHIVE_BYTES) {
                BgMusicReadResult.Invalid(BgMusicInvalidReason.TooLarge, "declared size")
            } else {
                readArchive(input)
            }
        } catch (unsupported: UnsupportedVersion) {
            BgMusicReadResult.Unsupported(unsupported.formatVersion, unsupported.minReaderVersion)
        } catch (failure: ReadFailure) {
            BgMusicReadResult.Invalid(failure.reason, failure.detail)
        } catch (overflow: StackOverflowError) {
            BgMusicReadResult.Invalid(BgMusicInvalidReason.BadJson, "nesting")
        } catch (exhausted: OutOfMemoryError) {
            // The archive cannot be held in memory on this device, so it is treated as too large.
            BgMusicReadResult.Invalid(BgMusicInvalidReason.TooLarge, "memory")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            // The read stopped because the thread was interrupted. The file may be fine, so this is an I/O failure.
            Thread.currentThread().interrupt()
            BgMusicReadResult.Invalid(BgMusicInvalidReason.IoError, "interrupted")
        } catch (unexpected: Exception) {
            // Damage and truncation are properties of the file. Any other I/O failure belongs to the source,
            // so the file itself may be fine and the caller can retry.
            val reason = if (unexpected is IOException && unexpected !is ZipException && unexpected !is EOFException) {
                BgMusicInvalidReason.IoError
            } else {
                BgMusicInvalidReason.CorruptArchive
            }
            BgMusicReadResult.Invalid(reason, unexpected.javaClass.simpleName)
        }
    }

    /**
     * A cheap hint for file pickers: true for a `.bgmusic` name or the registered MIME type. It never decides
     * validity, because the content is checked by [read] only, and a renamed file is still judged by its content.
     */
    fun looksLikeCandidate(fileName: String?, mimeType: String?): Boolean {
        if (mimeType?.substringBefore(';')?.trim()?.lowercase() == BgMusicFormat.MIME_TYPE) return true
        val name = fileName?.trim()?.lowercase() ?: return false
        return name.endsWith(".${BgMusicFormat.FILE_EXTENSION}")
    }

    private fun readArchive(input: InputStream): BgMusicReadResult {
        val counted = CountingInputStream(NonClosingInputStream(input))
        val pushback = PushbackInputStream(counted, LOCAL_HEADER_LENGTH)
        val signature = ByteArray(LOCAL_HEADER_LENGTH)
        val got = readUpTo(pushback, signature)
        if (got < LOCAL_HEADER_LENGTH || !BgMusicFormat.hasBytesAt(signature, 0, 0x50, 0x4B, 0x03, 0x04)) {
            fail(BgMusicInvalidReason.NotZip)
        }
        pushback.unread(signature)
        val zip = ZipInputStream(pushback)
        try {
            return scanArchive(zip)
        } finally {
            zip.close()
        }
    }

    private fun scanArchive(zip: ZipInputStream): BgMusicReadResult {
        val budget = Budget()
        var manifestBytes: ByteArray? = null
        var coverName: String? = null
        var coverBytes: ByteArray? = null
        val seenNames = HashSet<String>()
        var entryCount = 0

        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount++
            if (entryCount > BgMusicFormat.MAX_ENTRIES) fail(BgMusicInvalidReason.TooManyEntries)

            val name = entry.name
            if (entry.isDirectory || !BgMusicFormat.isSafeEntryName(name) || BgMusicFormat.hasNestedArchiveSuffix(name)) {
                fail(BgMusicInvalidReason.BadEntryName)
            }
            if (!seenNames.add(name.lowercase())) fail(BgMusicInvalidReason.DuplicateEntry)

            when {
                name == BgMusicFormat.MANIFEST_ENTRY -> {
                    manifestBytes = readEntry(
                        zip, budget, BgMusicFormat.MAX_MANIFEST_BYTES, BgMusicInvalidReason.ManifestTooLarge, keep = true,
                    )
                }
                BgMusicFormat.isCoverEntryName(name) -> {
                    if (coverName != null) fail(BgMusicInvalidReason.DuplicateEntry, "second cover entry")
                    coverName = name
                    coverBytes = readEntry(
                        zip, budget, BgMusicFormat.MAX_COVER_BYTES, BgMusicInvalidReason.CoverTooLarge, keep = true,
                    )
                }
                else -> {
                    // Unknown entries are skipped so newer writers stay readable. They still count toward the
                    // entry limit and the total budget, and they are sniffed for nested archives.
                    val head = readEntry(
                        zip, budget, BgMusicFormat.MAX_TOTAL_UNCOMPRESSED_BYTES, BgMusicInvalidReason.TooLarge, keep = false,
                    )
                    if (BgMusicFormat.looksLikeArchive(head)) fail(BgMusicInvalidReason.BadEntryName, "nested archive")
                }
            }
        }
        if (entryCount == 0) fail(BgMusicInvalidReason.CorruptArchive, "no entries")

        val rawManifest = manifestBytes ?: fail(BgMusicInvalidReason.MissingManifest)
        val manifest = parseManifest(rawManifest)
        val cover = coverBytes
        if (cover != null && BgMusicFormat.coverEntryNameFor(cover) != coverName) {
            fail(BgMusicInvalidReason.BadCover)
        }
        return BgMusicReadResult.Success(manifest, cover)
    }

    /**
     * Reads the current entry up to [limit] bytes. Returns the whole content when [keep] is true, otherwise only
     * the first few bytes, which are used for sniffing. The entry size in the header is never trusted.
     */
    private fun readEntry(
        zip: ZipInputStream,
        budget: Budget,
        limit: Long,
        overflow: BgMusicInvalidReason,
        keep: Boolean,
    ): ByteArray {
        val sink = ByteArrayOutputStream()
        val head = ByteArray(HEAD_SNIFF_LENGTH)
        var headLength = 0
        var entryBytes = 0L
        val buffer = ByteArray(BUFFER_LENGTH)
        while (true) {
            val count = zip.read(buffer, 0, buffer.size)
            if (count < 0) break
            entryBytes += count
            budget.usedBytes += count
            if (entryBytes > limit) fail(overflow)
            if (budget.usedBytes > BgMusicFormat.MAX_TOTAL_UNCOMPRESSED_BYTES) fail(BgMusicInvalidReason.TooLarge)
            if (headLength < HEAD_SNIFF_LENGTH) {
                val copied = minOf(count, HEAD_SNIFF_LENGTH - headLength)
                buffer.copyInto(head, headLength, 0, copied)
                headLength += copied
            }
            if (keep) sink.write(buffer, 0, count)
        }
        return if (keep) sink.toByteArray() else head.copyOf(headLength)
    }

    /**
     * Parses the manifest in a fixed order: UTF-8 text, strict JSON, JSON object, signature, versions, then fields.
     * The version check comes before any field check, so a newer file is reported as Unsupported even if its layout
     * changed.
     */
    private fun parseManifest(raw: ByteArray): BgMusicManifest {
        val text = decodeUtf8(raw)
        BgMusicStrictJson.problem(text, BgMusicFormat.MAX_NESTING_DEPTH)?.let { fail(BgMusicInvalidReason.BadJson, it) }
        val root = try {
            JSONObject(text)
        } catch (malformed: Exception) {
            fail(BgMusicInvalidReason.BadJson, "not a JSON object")
        }
        if (root.opt("format") != BgMusicFormat.SIGNATURE) fail(BgMusicInvalidReason.BadSignature)

        val formatVersion = root.requireInt("format_version", 1, Int.MAX_VALUE)
        val minReaderVersion = root.requireInt("min_reader_version", 1, formatVersion)
        if (minReaderVersion > BgMusicFormat.READER_VERSION) {
            throw UnsupportedVersion(formatVersion, minReaderVersion)
        }

        return BgMusicManifest(
            playlistId = root.requireUuid("playlist_id"),
            revision = root.requireLong("revision", 0L, Long.MAX_VALUE),
            title = root.requireText("title", BgMusicFormat.MAX_PLAYLIST_TITLE),
            updatedAtMs = root.requireLong("updated_at", 0L, BgMusicFormat.MAX_UPDATED_AT_MS),
            tracks = parseTracks(root),
            description = root.optionalText("description", BgMusicFormat.MAX_DESCRIPTION, keepLineBreaks = true),
            author = parseAuthor(root),
            formatVersion = formatVersion,
            minReaderVersion = minReaderVersion,
        )
    }
}

/** Failure with a precise reason. It is caught only inside [BgMusicReader.read]. */
private class ReadFailure(val reason: BgMusicInvalidReason, val detail: String) : RuntimeException(detail)

/** Signals a file that needs a newer reader. It is caught only inside [BgMusicReader.read]. */
private class UnsupportedVersion(val formatVersion: Int, val minReaderVersion: Int) : RuntimeException()

/** Total uncompressed bytes seen so far across all entries of one archive. */
private class Budget {
    var usedBytes: Long = 0L
}

private const val LOCAL_HEADER_LENGTH = 4
private const val HEAD_SNIFF_LENGTH = 8
private const val BUFFER_LENGTH = 8 * 1024

private fun fail(reason: BgMusicInvalidReason, detail: String = ""): Nothing = throw ReadFailure(reason, detail)

private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val got = input.read(buffer, total, buffer.size - total)
        if (got < 0) break
        total += got
    }
    return total
}

/** Strict UTF-8 decoding. A UTF-8 byte order mark is accepted and removed. */
private fun decodeUtf8(raw: ByteArray): String {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val text = try {
        decoder.decode(ByteBuffer.wrap(raw)).toString()
    } catch (malformed: CharacterCodingException) {
        fail(BgMusicInvalidReason.BadJson, "manifest is not UTF-8")
    }
    return if (text.startsWith("\uFEFF")) text.substring(1) else text
}

private fun integerValue(value: Any?): Long? = when (value) {
    is Int -> value.toLong()
    is Long -> value
    else -> null
}

private fun JSONObject.requireInt(key: String, min: Int, max: Int): Int =
    requireLong(key, min.toLong(), max.toLong()).toInt()

/** Accepts JSON integers only. Strings, decimals and numbers outside Long are rejected rather than coerced. */
private fun JSONObject.requireLong(key: String, min: Long, max: Long): Long {
    val value = integerValue(opt(key)) ?: fail(BgMusicInvalidReason.BadField, key)
    if (value < min || value > max) fail(BgMusicInvalidReason.BadField, key)
    return value
}

private fun JSONObject.requireUuid(key: String): String {
    val raw = opt(key) as? String ?: fail(BgMusicInvalidReason.BadField, key)
    return BgMusicFormat.normalizeUuid(raw) ?: fail(BgMusicInvalidReason.BadField, key)
}

/**
 * Required single-line text. Line breaks and tabs become spaces, other control characters are removed, and the
 * text is capped. Empty text fails unless [allowEmpty].
 */
private fun JSONObject.requireText(key: String, maxCodePoints: Int, allowEmpty: Boolean = false): String {
    val raw = opt(key) as? String ?: fail(BgMusicInvalidReason.BadField, key)
    val text = BgMusicFormat.sanitizeText(raw, maxCodePoints)
    if (text.isEmpty() && !allowEmpty) fail(BgMusicInvalidReason.BadField, key)
    return text
}

/**
 * Optional text. A missing key or a JSON null reads as an empty string. Line breaks are kept only when
 * [keepLineBreaks] is set, which is the case for the description; every other field is single-line.
 */
private fun JSONObject.optionalText(key: String, maxCodePoints: Int, keepLineBreaks: Boolean = false): String {
    val value = opt(key)
    if (value == null || value == JSONObject.NULL) return ""
    val raw = value as? String ?: fail(BgMusicInvalidReason.BadField, key)
    return if (keepLineBreaks) {
        BgMusicFormat.sanitizeMultilineText(raw, maxCodePoints)
    } else {
        BgMusicFormat.sanitizeText(raw, maxCodePoints)
    }
}

private fun parseAuthor(root: JSONObject): BgMusicAuthor? {
    val value = root.opt("author")
    if (value == null || value == JSONObject.NULL) return null
    val author = value as? JSONObject ?: fail(BgMusicInvalidReason.BadField, "author")
    return BgMusicAuthor(name = author.requireText("name", BgMusicFormat.MAX_AUTHOR_NAME))
}

private fun parseTracks(root: JSONObject): List<BgMusicTrack> {
    val array = root.opt("tracks") as? JSONArray ?: fail(BgMusicInvalidReason.BadField, "tracks")
    if (array.length() > BgMusicFormat.MAX_TRACKS) fail(BgMusicInvalidReason.TooManyTracks)
    val ids = HashSet<String>()
    val tracks = ArrayList<BgMusicTrack>(array.length())
    for (index in 0 until array.length()) {
        val item = array.opt(index) as? JSONObject ?: fail(BgMusicInvalidReason.BadField, "track")
        val track = parseTrack(item)
        if (!ids.add(track.id)) fail(BgMusicInvalidReason.BadField, "track id")
        tracks.add(track)
    }
    return tracks
}

private fun parseTrack(item: JSONObject): BgMusicTrack = BgMusicTrack(
    id = item.requireUuid("id"),
    title = item.requireText("title", BgMusicFormat.MAX_TRACK_TITLE),
    artist = item.requireText("artist", BgMusicFormat.MAX_ARTIST, allowEmpty = true),
    album = item.requireText("album", BgMusicFormat.MAX_ALBUM, allowEmpty = true),
    durationSec = item.requireLong("duration_sec", 0L, BgMusicFormat.MAX_DURATION_SEC),
    // A malformed or unknown-algorithm hash is ignored instead of failing the whole file.
    audioHash = (item.opt("audio_hash") as? String)?.let { BgMusicFormat.normalizeAudioHash(it) },
    sources = parseSources(item),
)

/** Malformed or unknown source hints are dropped, because they are only hints. A non-array value fails. */
private fun parseSources(item: JSONObject): List<BgMusicSourceHint> {
    val value = item.opt("sources")
    if (value == null || value == JSONObject.NULL) return emptyList()
    val array = value as? JSONArray ?: fail(BgMusicInvalidReason.BadField, "sources")
    val hints = ArrayList<BgMusicSourceHint>()
    for (index in 0 until array.length()) {
        if (hints.size >= BgMusicFormat.MAX_SOURCES_PER_TRACK) break
        val hint = array.opt(index) as? JSONObject ?: continue
        val parsed = parseSourceHint(hint) ?: continue
        hints.add(parsed)
    }
    return hints
}

private fun parseSourceHint(hint: JSONObject): BgMusicSourceHint? {
    val type = hint.opt("type") as? String ?: return null
    // Unknown source types are ignored, so future hint kinds do not break older readers.
    if (type != BgMusicFormat.TYPE_TELEGRAM_PUBLIC) return null
    val username = (hint.opt("username") as? String)?.let { BgMusicFormat.normalizeTelegramUsername(it) } ?: return null
    val messageId = integerValue(hint.opt("message_id")) ?: return null
    if (messageId !in 1L..BgMusicFormat.MAX_TELEGRAM_MESSAGE_ID) return null
    return BgMusicSourceHint.TelegramPublic(username = username, messageId = messageId)
}

/** Stream wrapper that counts compressed bytes and fails once the archive exceeds its size limit. */
private class CountingInputStream(stream: InputStream) : FilterInputStream(stream) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1L)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val got = super.read(buffer, offset, length)
        if (got > 0) account(got.toLong())
        return got
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0L) account(skipped)
        return skipped
    }

    private fun account(bytes: Long) {
        consumed += bytes
        if (consumed > BgMusicFormat.MAX_ARCHIVE_BYTES) fail(BgMusicInvalidReason.TooLarge, "compressed size")
    }
}

/** Forwards reads but leaves the caller's stream open. */
private class NonClosingInputStream(stream: InputStream) : FilterInputStream(stream) {
    override fun close() {
        // The caller owns the stream and closes it.
    }
}
