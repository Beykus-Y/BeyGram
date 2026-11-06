package com.beykus.beygram.data.music.bgmusic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val TEST_PLAYLIST_ID = "6f1d2c3a-4b5e-4f60-8a7b-9c0d1e2f3a4b"

internal val PNG_MAGIC: ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)

internal val JPEG_MAGIC: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

/** Deterministic canonical lowercase UUID for the n-th test track. */
internal fun testTrackId(n: Int): String = UUID(0x1234_5678_9ABC_DEF0L, n.toLong()).toString()

internal fun testTrack(n: Int, title: String = "Track $n"): BgMusicTrack = BgMusicTrack(
    id = testTrackId(n),
    title = title,
    artist = "Artist $n",
    album = "Album ${n / 10}",
    durationSec = 180L + n,
)

internal fun testManifest(
    tracks: List<BgMusicTrack> = listOf(testTrack(1), testTrack(2), testTrack(3)),
    title: String = "Morning mix",
    revision: Long = 7L,
    description: String = "Coffee and rain",
    author: BgMusicAuthor? = null,
): BgMusicManifest = BgMusicManifest(
    playlistId = TEST_PLAYLIST_ID,
    revision = revision,
    title = title,
    updatedAtMs = 1_700_000_000_000L,
    tracks = tracks,
    description = description,
    author = author,
)

internal fun writeToBytes(manifest: BgMusicManifest, cover: ByteArray? = null): ByteArray {
    val out = ByteArrayOutputStream()
    BgMusicWriter.write(manifest, cover, out)
    return out.toByteArray()
}

internal fun readAll(bytes: ByteArray): BgMusicReadResult = BgMusicReader.read(ByteArrayInputStream(bytes), null)

internal fun assertSuccess(result: BgMusicReadResult): BgMusicReadResult.Success {
    if (result !is BgMusicReadResult.Success) throw AssertionError("expected Success, got $result")
    return result
}

internal fun assertInvalid(result: BgMusicReadResult, reason: BgMusicInvalidReason) {
    val invalid = result as? BgMusicReadResult.Invalid
        ?: throw AssertionError("expected Invalid($reason), got $result")
    if (invalid.reason != reason) {
        throw AssertionError("expected $reason, got ${invalid.reason} (${invalid.detail})")
    }
}

/** Names of the entries in archive order, read through the same streaming API the app uses. */
internal fun entryNames(archive: ByteArray): List<String> {
    val names = ArrayList<String>()
    ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            names += entry.name
        }
    }
    return names
}

/** UTF-8 text of one entry of [archive]. */
internal fun entryText(archive: ByteArray, name: String): String {
    ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == name) return zip.readBytes().toString(StandardCharsets.UTF_8)
        }
    }
    throw AssertionError("entry $name is missing")
}

/**
 * Builds a ZIP of stored entries by hand. Unlike ZipOutputStream it accepts duplicate and unsafe names, so the
 * tests can express attacks that a normal writer refuses to produce.
 */
internal fun rawZip(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    for ((name, data) in entries) {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val crc = CRC32()
        crc.update(data)
        val header = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x04034B50)
        header.putShort(20) // version needed to extract
        header.putShort(0) // flags: not encrypted, no data descriptor
        header.putShort(0) // method: stored
        header.putShort(0) // time 00:00:00
        header.putShort(0x21) // date 1980-01-01
        header.putInt(crc.value.toInt())
        header.putInt(data.size) // compressed size
        header.putInt(data.size) // uncompressed size
        header.putShort(nameBytes.size.toShort())
        header.putShort(0) // extra field length
        out.write(header.array())
        out.write(nameBytes)
        out.write(data)
    }
    return out.toByteArray()
}

/** A stored archive whose manifest entry holds [json] as UTF-8, followed by any [extra] entries. */
internal fun manifestArchive(json: String, vararg extra: Pair<String, ByteArray>): ByteArray =
    rawZip(listOf("manifest.json" to json.toByteArray(StandardCharsets.UTF_8)) + extra.toList())

/**
 * A deflated archive whose [bombName] entry is [megabytes] of zeros. The content is streamed, so the test never
 * holds it in memory. The compressed output is about 1 KB per MB.
 */
internal fun zeroBombZip(bombName: String, megabytes: Int, manifest: ByteArray? = null): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        if (manifest != null) {
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry(bombName))
        val chunk = ByteArray(64 * 1024)
        repeat(megabytes * 16) { zip.write(chunk) }
        zip.closeEntry()
    }
    return out.toByteArray()
}

/** A WebP header ("RIFF" size "WEBP") followed by [payload] zero bytes. */
internal fun webpBytes(payload: Int = 16): ByteArray {
    val bytes = ByteArray(12 + payload)
    "RIFF".toByteArray(StandardCharsets.US_ASCII).copyInto(bytes, 0)
    "WEBP".toByteArray(StandardCharsets.US_ASCII).copyInto(bytes, 8)
    return bytes
}

internal fun trackJson(
    id: String = testTrackId(1),
    title: String = "\"Song\"",
    duration: String = "200",
    extra: String = "",
): String = "{\"id\":\"$id\",\"title\":$title,\"artist\":\"Artist\",\"album\":\"Album\"," +
    "\"duration_sec\":$duration$extra}"

/**
 * Builds manifest JSON text. Each argument is raw JSON, so a test can break exactly one field. Pass [extra] as a
 * string that starts with a comma to add top-level fields.
 */
internal fun manifestJson(
    signature: String = "\"beygram.music\"",
    formatVersion: String = "1",
    minReaderVersion: String = "1",
    playlistId: String = "\"$TEST_PLAYLIST_ID\"",
    revision: String = "7",
    title: String = "\"Mix\"",
    description: String = "\"\"",
    updatedAt: String = "1700000000000",
    author: String? = null,
    tracks: String = "[${trackJson()}]",
    extra: String = "",
): String {
    val fields = mutableListOf(
        "\"format\":$signature",
        "\"format_version\":$formatVersion",
        "\"min_reader_version\":$minReaderVersion",
        "\"playlist_id\":$playlistId",
        "\"revision\":$revision",
        "\"title\":$title",
        "\"description\":$description",
        "\"updated_at\":$updatedAt",
        "\"tracks\":$tracks",
    )
    if (author != null) fields += "\"author\":$author"
    return "{" + fields.joinToString(",") + extra + "}"
}

/** Bytes of a valid manifest JSON document, for tests that only need some valid manifest. */
internal fun validManifestBytes(): ByteArray = manifestJson().toByteArray(StandardCharsets.UTF_8)
