package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMusicManifestCompatibilityTest {

    @Test
    fun baselineManifestJsonIsAccepted() {
        assertSuccess(readAll(manifestArchive(manifestJson())))
    }

    @Test
    fun unknownFieldsAreIgnoredAtEveryLevel() {
        val track = "{\"id\":\"${testTrackId(1)}\",\"title\":\"Song\",\"artist\":\"A\",\"album\":\"B\"," +
            "\"duration_sec\":200,\"color\":\"red\",\"sources\":[" +
            "{\"type\":\"spotify\",\"uri\":\"spotify:track:x\"}," +
            "{\"type\":\"telegram_public\",\"username\":\"chan_nel\",\"message_id\":5,\"extra\":true}]}"
        val json = manifestJson(
            extra = ",\"future_block\":{\"x\":[1,2,3]}",
            tracks = "[$track]",
        )

        val manifest = assertSuccess(readAll(manifestArchive(json))).manifest

        assertEquals(1, manifest.tracks.size)
        assertEquals(
            listOf(BgMusicSourceHint.TelegramPublic("chan_nel", 5L)),
            manifest.tracks.single().sources,
        )
    }

    @Test
    fun newerFormatWithCompatibleMinReaderIsReadable() {
        val manifest = assertSuccess(
            readAll(manifestArchive(manifestJson(formatVersion = "3", minReaderVersion = "1"))),
        ).manifest

        assertEquals(3, manifest.formatVersion)
        assertEquals(1, manifest.minReaderVersion)
    }

    @Test
    fun minReaderAboveSupportedIsUnsupportedAndLaterFieldsAreNotInterpreted() {
        // The tracks value would be invalid for v1, but Unsupported is decided first, so it is never looked at.
        val json = manifestJson(formatVersion = "2", minReaderVersion = "2", tracks = "\"layout changed in v2\"")

        assertEquals(BgMusicReadResult.Unsupported(2, 2), readAll(manifestArchive(json)))
    }

    @Test
    fun minReaderAboveFormatVersionIsBadField() {
        assertInvalid(
            readAll(manifestArchive(manifestJson(formatVersion = "1", minReaderVersion = "2"))),
            BgMusicInvalidReason.BadField,
        )
    }

    @Test
    fun missingOrWrongSignatureIsBadSignature() {
        assertInvalid(
            readAll(manifestArchive(manifestJson(signature = "\"other.format\""))),
            BgMusicInvalidReason.BadSignature,
        )
        val withoutFormat = manifestJson().replace("\"format\":\"beygram.music\",", "")
        assertInvalid(readAll(manifestArchive(withoutFormat)), BgMusicInvalidReason.BadSignature)
    }

    @Test
    fun malformedOrNonUtf8JsonIsBadJson() {
        assertInvalid(readAll(manifestArchive("{\"format\":")), BgMusicInvalidReason.BadJson)
        assertInvalid(readAll(manifestArchive("[1,2,3]")), BgMusicInvalidReason.BadJson)
        assertInvalid(readAll(manifestArchive("")), BgMusicInvalidReason.BadJson)
        val invalidUtf8 = rawZip(listOf("manifest.json" to byteArrayOf(0x7B, 0xFF.toByte(), 0x7D)))
        assertInvalid(readAll(invalidUtf8), BgMusicInvalidReason.BadJson)
    }

    @Test
    fun deeplyNestedJsonIsRejectedBeforeTheParserRecurses() {
        val nested = "[".repeat(100_000) + "]".repeat(100_000)

        assertInvalid(readAll(manifestArchive(nested)), BgMusicInvalidReason.BadJson)
    }

    @Test
    fun invalidRequiredFieldsAreBadField() {
        val cases = listOf(
            "revision as string" to manifestJson(revision = "\"7\""),
            "negative revision" to manifestJson(revision = "-1"),
            "decimal revision" to manifestJson(revision = "1.5"),
            "revision beyond Long" to manifestJson(revision = "99999999999999999999999"),
            "malformed playlist id" to manifestJson(playlistId = "\"not-a-uuid\""),
            "title of control characters only" to manifestJson(title = "\"\\u0007 \\u0000\""),
            "missing title" to manifestJson().replace("\"title\":\"Mix\",", ""),
            "negative updated_at" to manifestJson(updatedAt = "-5"),
            "fractional duration" to manifestJson(tracks = "[${trackJson(duration = "200.5")}]"),
            "duration as string" to manifestJson(tracks = "[${trackJson(duration = "\"200\"")}]"),
            "duration above the cap" to manifestJson(tracks = "[${trackJson(duration = "604801")}]"),
            "tracks is not an array" to manifestJson(tracks = "{}"),
            "track without title" to manifestJson(
                tracks = "[{\"id\":\"${testTrackId(1)}\",\"artist\":\"A\",\"album\":\"B\",\"duration_sec\":1}]",
            ),
            "duplicate track ids" to manifestJson(tracks = "[${trackJson()},${trackJson()}]"),
            "sources is not an array" to manifestJson(tracks = "[${trackJson(extra = ",\"sources\":\"x\"")}]"),
            "author without name" to manifestJson(author = "{}"),
        )
        for ((label, json) in cases) {
            val result = readAll(manifestArchive(json))
            val invalid = result as? BgMusicReadResult.Invalid ?: throw AssertionError("$label: expected Invalid, got $result")
            assertEquals(label, BgMusicInvalidReason.BadField, invalid.reason)
        }
    }

    @Test
    fun manifestDoesNotHaveToBeTheFirstEntry() {
        val archive = rawZip(
            listOf("cover.png" to PNG_MAGIC + ByteArray(8), BgMusicFormat.MANIFEST_ENTRY to validManifestBytes()),
        )

        val result = assertSuccess(readAll(archive))

        assertEquals(1, result.manifest.tracks.size)
        assertArrayEquals(PNG_MAGIC + ByteArray(8), result.cover)
    }

    @Test
    fun identifiersAreNormalizedToLowercaseAndInvalidAudioHashesAreIgnored() {
        val json = manifestJson(
            playlistId = "\"6F1D2C3A-4B5E-4F60-8A7B-9C0D1E2F3A4B\"",
            tracks = "[${trackJson(id = testTrackId(1).uppercase(), extra = ",\"audio_hash\":\"sha256:" + "A".repeat(64) + "\"")}]",
        )

        val manifest = assertSuccess(readAll(manifestArchive(json))).manifest

        assertEquals(TEST_PLAYLIST_ID, manifest.playlistId)
        assertEquals(testTrackId(1), manifest.tracks.single().id)
        assertNull(manifest.tracks.single().audioHash)
    }

    @Test
    fun updatedAtBeyondTheCalendarCapIsBadField() {
        assertInvalid(
            readAll(manifestArchive(manifestJson(updatedAt = "${BgMusicFormat.MAX_UPDATED_AT_MS + 1}"))),
            BgMusicInvalidReason.BadField,
        )
    }

    @Test
    fun unsafeTextIsSanitizedOnRead() {
        val json = manifestJson(
            title = "\"\\u0007Mix \\u202Etitle\\u2069  \"",
            description = "\"line1\\nline2\"",
        )

        val manifest = assertSuccess(readAll(manifestArchive(json))).manifest

        assertEquals("Mix title", manifest.title)
        assertEquals("line1\nline2", manifest.description)
    }

    @Test
    fun overlongTextIsTruncatedRatherThanRejected() {
        val manifest = assertSuccess(readAll(manifestArchive(manifestJson(title = "\"${"x".repeat(300)}\"")))).manifest

        assertEquals(128, manifest.title.length)
    }

    @Test
    fun nullOptionalFieldsReadAsDefaultsAndMalformedHintsAreDropped() {
        val json = manifestJson(
            description = "null",
            author = "null",
            tracks = "[${trackJson(extra = ",\"audio_hash\":\"md5:abc\",\"sources\":null")}]",
        )

        val manifest = assertSuccess(readAll(manifestArchive(json))).manifest

        assertEquals("", manifest.description)
        assertNull(manifest.author)
        assertNull(manifest.tracks.single().audioHash)
        assertTrue(manifest.tracks.single().sources.isEmpty())
    }

    @Test
    fun exactlyTheTrackLimitIsAccepted() {
        val tracks = (1..BgMusicFormat.MAX_TRACKS).joinToString(prefix = "[", postfix = "]") {
            trackJson(id = testTrackId(it))
        }

        val manifest = assertSuccess(readAll(manifestArchive(manifestJson(tracks = tracks)))).manifest

        assertEquals(BgMusicFormat.MAX_TRACKS, manifest.tracks.size)
    }

    @Test
    fun oneTrackAboveTheLimitIsTooManyTracks() {
        val tracks = (1..BgMusicFormat.MAX_TRACKS + 1).joinToString(prefix = "[", postfix = "]") {
            trackJson(id = testTrackId(it))
        }

        assertInvalid(readAll(manifestArchive(manifestJson(tracks = tracks))), BgMusicInvalidReason.TooManyTracks)
    }

    @Test
    fun manifestAtTheTwoMebibyteLimitIsAcceptedAndOneByteMoreIsTooLarge() {
        val limit = BgMusicFormat.MAX_MANIFEST_BYTES.toInt()
        val withEmptyPadding = manifestJson(extra = ",\"padding\":\"\"").length

        fun manifestOfBytes(bytes: Int): String =
            manifestJson(extra = ",\"padding\":\"" + "x".repeat(bytes - withEmptyPadding) + "\"")

        assertEquals(limit, manifestOfBytes(limit).length) // the JSON is ASCII, so characters are bytes
        assertSuccess(readAll(manifestArchive(manifestOfBytes(limit))))
        assertInvalid(
            readAll(manifestArchive(manifestOfBytes(limit + 1))),
            BgMusicInvalidReason.ManifestTooLarge,
        )
    }

    @Test
    fun lineBreaksAreNormalizedPerFieldOnRead() {
        val json = manifestJson(
            title = "\"Mix\\nTape\"",
            description = "\"a\\r\\n\\r\\n\\r\\nb\\rc\"",
        )

        val manifest = assertSuccess(readAll(manifestArchive(json))).manifest

        assertEquals("Mix Tape", manifest.title)
        assertEquals("a\n\nb\nc", manifest.description)
    }

    @Test
    fun singleQuotedPayloadThatHidesNestingIsBadJson() {
        // Android's org.json reads '"' as a whole string, so a scan that only knows double quotes loses the depth.
        val payload = manifestJson(extra = ",\"quote\":'\"',\"deep\":" + "[".repeat(100) + "]".repeat(100))

        assertInvalid(readAll(manifestArchive(payload)), BgMusicInvalidReason.BadJson)
    }

    @Test
    fun commentsTrailingDataAndLenientSyntaxAreBadJson() {
        val cases = listOf(
            "block comment" to manifestJson(extra = ",\"c\":1 /* note */"),
            "line comment" to manifestJson(extra = ",\"c\":1 // note\n"),
            "hash comment" to manifestJson(extra = ",\"c\":1 # note\n"),
            "trailing text" to (manifestJson() + "x"),
            "second document" to (manifestJson() + manifestJson()),
            "trailing NUL" to (manifestJson() + "\u0000"),
            "unquoted key" to manifestJson(extra = ",color:1"),
            "single-quoted key" to manifestJson(extra = ",'c':1"),
            "unquoted word" to manifestJson(extra = ",\"c\":undefined"),
            "NaN" to manifestJson(extra = ",\"c\":NaN"),
            "semicolon separator" to manifestJson(extra = ",\"c\":1;\"d\":2"),
            "equals separator" to manifestJson(extra = ",\"c\"=1"),
            "fat arrow separator" to manifestJson(extra = ",\"c\"=>1"),
        )
        for ((label, json) in cases) {
            val result = readAll(manifestArchive(json))
            val invalid = result as? BgMusicReadResult.Invalid ?: throw AssertionError("$label: expected Invalid, got $result")
            assertEquals(label, BgMusicInvalidReason.BadJson, invalid.reason)
        }
    }

    @Test
    fun nestingLimitCountsTheManifestObjectAsLevelOne() {
        val fifteenArrays = manifestJson(extra = ",\"deep\":" + "[".repeat(15) + "]".repeat(15))
        val sixteenArrays = manifestJson(extra = ",\"deep\":" + "[".repeat(16) + "]".repeat(16))

        assertSuccess(readAll(manifestArchive(fifteenArrays)))
        assertInvalid(readAll(manifestArchive(sixteenArrays)), BgMusicInvalidReason.BadJson)
    }

    @Test
    fun repeatedKeysAreBadJsonEvenWhenTheValuesAgree() {
        assertInvalid(readAll(manifestArchive(manifestJson(extra = ",\"title\":\"Mix\""))), BgMusicInvalidReason.BadJson)
        val track = "{\"id\":\"${testTrackId(1)}\",\"title\":\"Song\",\"artist\":\"A\",\"album\":\"B\"," +
            "\"duration_sec\":200,\"artist\":\"A\"}"
        assertInvalid(readAll(manifestArchive(manifestJson(tracks = "[$track]"))), BgMusicInvalidReason.BadJson)
    }
}
