package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Random

/** Seeded, so every failure can be reproduced from the case number alone. */
class BgMusicFuzzTest {

    @Test
    fun randomBytesAlwaysProduceAResult() {
        val random = Random(20_260_101L)
        repeat(500) { iteration ->
            val bytes = ByteArray(random.nextInt(4096)).also { random.nextBytes(it) }
            if (iteration % 2 == 0 && bytes.size >= 4) {
                // Start like a real ZIP so that the parser goes past the signature check.
                bytes[0] = 0x50
                bytes[1] = 0x4B
                bytes[2] = 0x03
                bytes[3] = 0x04
            }
            assertReturns(iteration, bytes)
        }
    }

    @Test
    fun mutatedArchivesAlwaysProduceAResult() {
        val base = writeToBytes(
            testManifest(tracks = (1..3).map { testTrack(it) }),
            cover = PNG_MAGIC + ByteArray(32),
        )
        val random = Random(7L)
        repeat(500) { iteration ->
            val bytes = base.copyOf()
            repeat(1 + random.nextInt(8)) { mutateOnce(bytes, random) }
            val candidate = if (iteration % 5 == 0) bytes.copyOf(random.nextInt(bytes.size)) else bytes
            assertReturns(iteration, candidate)
        }
    }

    @Test
    fun mutatedManifestJsonAlwaysProducesAResult() {
        val json = manifestJson(
            tracks = "[${trackJson()},${trackJson(id = testTrackId(2), title = "\"Other\"")}]",
        ).toByteArray(Charsets.UTF_8)
        val random = Random(99L)
        repeat(500) { iteration ->
            val bytes = json.copyOf()
            repeat(1 + random.nextInt(6)) { mutateOnce(bytes, random) }
            assertReturns(iteration, rawZip(listOf(BgMusicFormat.MANIFEST_ENTRY to bytes)))
        }
    }

    private fun mutateOnce(bytes: ByteArray, random: Random) {
        if (bytes.isEmpty()) return
        val index = random.nextInt(bytes.size)
        when (random.nextInt(3)) {
            0 -> bytes[index] = random.nextInt(256).toByte()
            1 -> bytes[index] = (bytes[index].toInt() xor (1 shl random.nextInt(8))).toByte()
            else -> bytes[index] = 0xFF.toByte()
        }
    }

    private fun assertReturns(iteration: Int, bytes: ByteArray) {
        val result = try {
            BgMusicReader.read(ByteArrayInputStream(bytes), null)
        } catch (error: Throwable) {
            throw AssertionError("case $iteration: reader threw $error", error)
        }
        assertTrue(
            "case $iteration: unexpected result $result",
            result is BgMusicReadResult.Success ||
                result is BgMusicReadResult.Unsupported ||
                result is BgMusicReadResult.Invalid,
        )
    }
}
