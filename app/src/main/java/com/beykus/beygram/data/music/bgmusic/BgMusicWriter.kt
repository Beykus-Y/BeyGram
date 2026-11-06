package com.beykus.beygram.data.music.bgmusic

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes `.bgmusic` v1 archives.
 *
 * Everything is validated and built in memory before the first byte reaches the output, so invalid input never
 * leaves a partial file behind. Entry order is fixed (manifest first, then the cover), and the timestamps are
 * constant, so the same input always produces the same bytes. The writer never closes the `out` stream it is given.
 */
object BgMusicWriter {

    /**
     * Writes [manifest] and an optional [cover] image (WebP, JPEG or PNG, detected from the magic bytes).
     *
     * Strings are sanitized and capped the same way the reader caps them. Values that cannot be fixed by
     * sanitizing (bad UUIDs, empty required text, out-of-range numbers, unsupported images) are rejected.
     *
     * @throws IllegalArgumentException when the input violates the v1 rules. Nothing is written in that case.
     */
    fun write(manifest: BgMusicManifest, cover: ByteArray?, out: OutputStream) {
        val manifestBytes = encodeManifest(manifest)
        val coverEntry = cover?.let { prepareCover(it) }
        writeArchive(manifestBytes, coverEntry, out)
    }

    private const val ENTRY_TIME_MS = 315_662_400_000L // 1980-01-02T12:00:00Z: inside the DOS date range in every time zone

    private fun encodeManifest(manifest: BgMusicManifest): ByteArray {
        require(manifest.formatVersion == BgMusicFormat.FORMAT_VERSION) {
            "format_version must be ${BgMusicFormat.FORMAT_VERSION} for this writer"
        }
        require(manifest.minReaderVersion == BgMusicFormat.FORMAT_VERSION) {
            "min_reader_version must be ${BgMusicFormat.FORMAT_VERSION} for this writer"
        }
        val playlistId = BgMusicFormat.normalizeUuid(manifest.playlistId)
            ?: throw IllegalArgumentException("playlistId must be a UUID")
        require(manifest.revision >= 0L) { "revision must be >= 0" }
        require(manifest.updatedAtMs in 0L..BgMusicFormat.MAX_UPDATED_AT_MS) { "updatedAtMs is out of range" }
        val title = BgMusicFormat.sanitizeText(manifest.title, BgMusicFormat.MAX_PLAYLIST_TITLE)
        require(title.isNotEmpty()) { "title must not be empty" }
        val description = BgMusicFormat.sanitizeMultilineText(manifest.description, BgMusicFormat.MAX_DESCRIPTION)
        require(manifest.tracks.size <= BgMusicFormat.MAX_TRACKS) { "a playlist holds at most ${BgMusicFormat.MAX_TRACKS} tracks" }

        val fields = mutableListOf<Pair<String, String>>()
        fields += "format" to jsonString(BgMusicFormat.SIGNATURE)
        fields += "format_version" to BgMusicFormat.FORMAT_VERSION.toString()
        fields += "min_reader_version" to BgMusicFormat.FORMAT_VERSION.toString()
        fields += "playlist_id" to jsonString(playlistId)
        fields += "revision" to manifest.revision.toString()
        fields += "title" to jsonString(title)
        fields += "description" to jsonString(description)
        fields += "updated_at" to manifest.updatedAtMs.toString()
        val author = manifest.author
        if (author != null) {
            fields += "author" to jsonObject(listOf("name" to jsonString(encodeAuthorName(author))))
        }
        fields += "tracks" to encodeTracks(manifest.tracks)

        val bytes = jsonObject(fields).toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong() <= BgMusicFormat.MAX_MANIFEST_BYTES) { "manifest exceeds 2 MiB" }
        return bytes
    }

    private fun encodeAuthorName(author: BgMusicAuthor): String {
        val name = BgMusicFormat.sanitizeText(author.name, BgMusicFormat.MAX_AUTHOR_NAME)
        require(name.isNotEmpty()) { "author name must not be empty" }
        return name
    }

    private fun encodeTracks(tracks: List<BgMusicTrack>): String {
        val seenIds = HashSet<String>()
        return jsonArray(tracks.map { encodeTrack(it, seenIds) })
    }

    private fun encodeTrack(track: BgMusicTrack, seenIds: MutableSet<String>): String {
        val id = BgMusicFormat.normalizeUuid(track.id) ?: throw IllegalArgumentException("track id must be a UUID")
        require(seenIds.add(id)) { "track ids must be unique inside a playlist" }
        val title = BgMusicFormat.sanitizeText(track.title, BgMusicFormat.MAX_TRACK_TITLE)
        require(title.isNotEmpty()) { "track title must not be empty" }
        require(track.durationSec in 0L..BgMusicFormat.MAX_DURATION_SEC) { "duration_sec is out of range" }
        require(track.sources.size <= BgMusicFormat.MAX_SOURCES_PER_TRACK) { "too many sources for one track" }

        val fields = mutableListOf<Pair<String, String>>()
        fields += "id" to jsonString(id)
        fields += "title" to jsonString(title)
        fields += "artist" to jsonString(BgMusicFormat.sanitizeText(track.artist, BgMusicFormat.MAX_ARTIST))
        fields += "album" to jsonString(BgMusicFormat.sanitizeText(track.album, BgMusicFormat.MAX_ALBUM))
        fields += "duration_sec" to track.durationSec.toString()
        val audioHash = track.audioHash
        if (audioHash != null) {
            val normalized = BgMusicFormat.normalizeAudioHash(audioHash)
                ?: throw IllegalArgumentException("audioHash must be sha256:<64 lowercase hex>")
            fields += "audio_hash" to jsonString(normalized)
        }
        if (track.sources.isNotEmpty()) {
            fields += "sources" to jsonArray(track.sources.map { encodeSource(it) })
        }
        return jsonObject(fields)
    }

    private fun encodeSource(hint: BgMusicSourceHint): String = when (hint) {
        is BgMusicSourceHint.TelegramPublic -> {
            val username = BgMusicFormat.normalizeTelegramUsername(hint.username)
                ?: throw IllegalArgumentException("telegram username is not a valid public username")
            require(hint.messageId in 1L..BgMusicFormat.MAX_TELEGRAM_MESSAGE_ID) { "telegram message id is out of range" }
            jsonObject(
                listOf(
                    "type" to jsonString(BgMusicFormat.TYPE_TELEGRAM_PUBLIC),
                    "username" to jsonString(username),
                    "message_id" to hint.messageId.toString(),
                ),
            )
        }
    }

    private fun prepareCover(bytes: ByteArray): Pair<String, ByteArray> {
        require(bytes.size.toLong() <= BgMusicFormat.MAX_COVER_BYTES) { "cover exceeds 1 MiB" }
        val name = BgMusicFormat.coverEntryNameFor(bytes)
            ?: throw IllegalArgumentException("cover must be a WebP, JPEG or PNG image")
        return name to bytes
    }

    private fun writeArchive(manifest: ByteArray, cover: Pair<String, ByteArray>?, out: OutputStream) {
        ZipOutputStream(NonClosingOutputStream(out)).use { zip ->
            zip.putNextEntry(deflatedEntry(BgMusicFormat.MANIFEST_ENTRY))
            zip.write(manifest)
            zip.closeEntry()
            if (cover != null) {
                zip.putNextEntry(storedEntry(cover.first, cover.second))
                zip.write(cover.second)
                zip.closeEntry()
            }
        }
    }

    private fun deflatedEntry(name: String): ZipEntry {
        val entry = ZipEntry(name)
        entry.method = ZipEntry.DEFLATED
        entry.time = ENTRY_TIME_MS
        return entry
    }

    /** Images are already compressed, so they are stored. Stored entries need their size and CRC up front. */
    private fun storedEntry(name: String, bytes: ByteArray): ZipEntry {
        val checksum = CRC32()
        checksum.update(bytes)
        val entry = ZipEntry(name)
        entry.method = ZipEntry.STORED
        entry.time = ENTRY_TIME_MS
        entry.size = bytes.size.toLong()
        entry.compressedSize = bytes.size.toLong()
        entry.crc = checksum.value
        return entry
    }

    /** JSON text is built by hand so that key order is fixed on every platform. Strings are escaped per RFC 8259. */
    private fun jsonObject(fields: List<Pair<String, String>>): String =
        fields.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:$value"
        }

    private fun jsonArray(items: List<String>): String =
        items.joinToString(separator = ",", prefix = "[", postfix = "]")

    private fun jsonString(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        for (ch in value) {
            when {
                ch == '"' -> builder.append("\\\"")
                ch == '\\' -> builder.append("\\\\")
                ch < ' ' -> builder.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> builder.append(ch)
            }
        }
        builder.append('"')
        return builder.toString()
    }
}

/** Forwards writes but leaves the caller's stream open. Closing the zip flushes it instead. */
private class NonClosingOutputStream(stream: OutputStream) : FilterOutputStream(stream) {
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        out.write(buffer, offset, length)
    }

    override fun close() {
        flush()
    }
}
