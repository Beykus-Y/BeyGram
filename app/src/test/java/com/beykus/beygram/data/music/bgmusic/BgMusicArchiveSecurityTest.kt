package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Random

/** Serves [data] up to [failAt] bytes, then fails the way a broken file provider would. */
private class FailingSource(private val data: ByteArray, private val failAt: Int) : InputStream() {
    private var position = 0

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= failAt) throw IOException("source failed")
        if (position >= data.size) return -1
        val count = minOf(length, failAt - position, data.size - position)
        data.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }
}

class BgMusicArchiveSecurityTest {

    private val manifest = validManifestBytes()

    @Test
    fun zipSlipAndOtherUnsafeEntryNamesAreRejected() {
        val names = listOf(
            "../evil.json",
            "..\\evil.json",
            "/abs.json",
            "\\abs.json",
            "a/b.json",
            "a\\b.json",
            "C:evil.json",
            ".hidden",
            "with space.json",
            "emoji-😀.json",
            "",
        )
        for (name in names) {
            val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, name to byteArrayOf(1, 2, 3)))
            assertInvalid(readAll(archive), BgMusicInvalidReason.BadEntryName)
        }
    }

    @Test
    fun directoryEntriesAreRejected() {
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "folder/" to ByteArray(0)))

        assertInvalid(readAll(archive), BgMusicInvalidReason.BadEntryName)
    }

    @Test
    fun duplicateManifestIsRejected() {
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, BgMusicFormat.MANIFEST_ENTRY to manifest))

        assertInvalid(readAll(archive), BgMusicInvalidReason.DuplicateEntry)
    }

    @Test
    fun caseOnlyDuplicateNamesAreRejected() {
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "MANIFEST.JSON" to manifest))

        assertInvalid(readAll(archive), BgMusicInvalidReason.DuplicateEntry)
    }

    @Test
    fun twoCoversAreADuplicate() {
        val archive = rawZip(
            listOf(
                BgMusicFormat.MANIFEST_ENTRY to manifest,
                "cover.png" to PNG_MAGIC,
                "cover.jpg" to JPEG_MAGIC,
            ),
        )

        assertInvalid(readAll(archive), BgMusicInvalidReason.DuplicateEntry)
    }

    @Test
    fun archiveWithoutManifestIsMissingManifest() {
        val archive = rawZip(listOf("cover.png" to PNG_MAGIC))

        assertInvalid(readAll(archive), BgMusicInvalidReason.MissingManifest)
    }

    @Test
    fun nestedArchiveByNameIsRejected() {
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "inner.zip" to byteArrayOf(1, 2, 3)))

        assertInvalid(readAll(archive), BgMusicInvalidReason.BadEntryName)
    }

    @Test
    fun nestedArchiveByContentIsRejectedEvenUnderAHarmlessName() {
        val innerZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(32)
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "payload.dat" to innerZip))

        assertInvalid(readAll(archive), BgMusicInvalidReason.BadEntryName)
    }

    @Test
    fun entryCountIsCappedAtEight() {
        val extras = (1..7).map { "extra$it.bin" to ByteArray(4) }
        assertSuccess(readAll(rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest) + extras)))

        val tooMany = (1..8).map { "extra$it.bin" to ByteArray(4) }
        assertInvalid(
            readAll(rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest) + tooMany)),
            BgMusicInvalidReason.TooManyEntries,
        )
    }

    @Test
    fun zipBombAsManifestIsRejectedQuicklyWithoutInflatingItAll() {
        val bomb = zeroBombZip(BgMusicFormat.MANIFEST_ENTRY, megabytes = 50)
        assertTrue("the bomb itself must be small on disk", bomb.size < 1_000_000)

        val started = System.nanoTime()
        assertInvalid(readAll(bomb), BgMusicInvalidReason.ManifestTooLarge)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue("rejection took $elapsedMs ms", elapsedMs < 10_000)
    }

    @Test
    fun zipBombAsUnknownEntryIsBoundedByTheTotalBudget() {
        val bomb = zeroBombZip("payload.bin", megabytes = 50, manifest = manifest)

        assertInvalid(readAll(bomb), BgMusicInvalidReason.TooLarge)
    }

    @Test
    fun oversizedCoverIsRejected() {
        val cover = PNG_MAGIC + ByteArray(1024 * 1024 + 1 - PNG_MAGIC.size)
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "cover.png" to cover))

        assertInvalid(readAll(archive), BgMusicInvalidReason.CoverTooLarge)
    }

    @Test
    fun coverWithTheWrongMagicIsBadCover() {
        val jpegUnderPngName = rawZip(
            listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "cover.png" to JPEG_MAGIC + ByteArray(16)),
        )
        assertInvalid(readAll(jpegUnderPngName), BgMusicInvalidReason.BadCover)

        val waveUnderWebpName = rawZip(
            listOf(
                BgMusicFormat.MANIFEST_ENTRY to manifest,
                "cover.webp" to "RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray(Charsets.ISO_8859_1),
            ),
        )
        assertInvalid(readAll(waveUnderWebpName), BgMusicInvalidReason.BadCover)

        val empty = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "cover.jpg" to ByteArray(0)))
        assertInvalid(readAll(empty), BgMusicInvalidReason.BadCover)
    }

    @Test
    fun unknownImageNamesAreIgnoredForwardCompatibly() {
        val archive = rawZip(
            listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "cover.gif" to "GIF89a".toByteArray(Charsets.ISO_8859_1)),
        )

        assertNull(assertSuccess(readAll(archive)).cover)
    }

    @Test
    fun declaredSizeOverTheLimitIsRejectedWithoutReadingAnyByte() {
        val untouched = object : InputStream() {
            override fun read(): Int = throw AssertionError("a file already known to be too large must not be read")
        }

        assertInvalid(
            BgMusicReader.read(untouched, declaredSize = BgMusicFormat.MAX_ARCHIVE_BYTES + 1),
            BgMusicInvalidReason.TooLarge,
        )
    }

    @Test
    fun fileOverTheCompressedLimitIsRejectedWhileBeingRead() {
        // Random bytes do not compress, so 4.5 MiB of them is a file that exceeds the 4 MiB cap.
        val noise = ByteArray(4_718_592).also { Random(42L).nextBytes(it) }
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest, "blob.bin" to noise))

        assertInvalid(readAll(archive), BgMusicInvalidReason.TooLarge)
    }

    @Test
    fun inputsThatAreNotZipAreNotZip() {
        assertInvalid(readAll(ByteArray(0)), BgMusicInvalidReason.NotZip)
        assertInvalid(readAll("hello, world".toByteArray()), BgMusicInvalidReason.NotZip)
        // An end-of-central-directory record alone is an empty ZIP, not a playlist.
        assertInvalid(readAll(byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(18)), BgMusicInvalidReason.NotZip)
    }

    @Test
    fun truncatedArchivesAreCorruptNotIoErrors() {
        val complete = writeToBytes(testManifest(), cover = webpBytes(payload = 64))
        assertInvalid(readAll(complete.copyOf(complete.size / 2)), BgMusicInvalidReason.CorruptArchive)

        val storedCut = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest))
        assertInvalid(readAll(storedCut.copyOf(storedCut.size - 10)), BgMusicInvalidReason.CorruptArchive)
    }

    @Test
    fun failingSourceIsIoErrorBecauseTheFileItselfMayBeFine() {
        val archive = writeToBytes(testManifest())
        val failingSource = FailingSource(archive, failAt = 40)

        assertInvalid(BgMusicReader.read(failingSource, null), BgMusicInvalidReason.IoError)
    }

    @Test
    fun encryptedEntryIsCorruptArchive() {
        val archive = rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to manifest))
        archive[6] = 0x01 // general purpose flag bit 0 = encrypted

        assertInvalid(readAll(archive), BgMusicInvalidReason.CorruptArchive)
    }

    @Test
    fun readNeverClosesTheCallersStream() {
        val tracker = object : ByteArrayInputStream(writeToBytes(testManifest())) {
            var closed = false

            override fun close() {
                closed = true
                super.close()
            }
        }

        assertSuccess(BgMusicReader.read(tracker, null))

        assertFalse("the reader must leave the caller's stream open", tracker.closed)
    }

    @Test
    fun writeNeverClosesTheCallersStream() {
        val tracker = object : ByteArrayOutputStream() {
            var closed = false

            override fun close() {
                closed = true
                super.close()
            }
        }

        BgMusicWriter.write(testManifest(), cover = PNG_MAGIC, out = tracker)

        assertFalse("the writer must leave the caller's stream open", tracker.closed)
        assertTrue(tracker.size() > 0)
    }
}
