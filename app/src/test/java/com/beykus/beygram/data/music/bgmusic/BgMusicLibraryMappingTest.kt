package com.beykus.beygram.data.music.bgmusic

import com.beykus.beygram.data.music.MUSIC_FAVORITES_PLAYLIST_ID
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.playback.PlaybackOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMusicLibraryMappingTest {

    private val account = "tg-account-zz"
    private val privateChat = 987_654_321L
    private val publicChat = 1_234_567_890L
    private val playlistId = "11111111-2222-4333-8444-555555555555"
    private val importedId = "99999999-8888-4777-8666-555555555555"

    /** The TDLib message id of public post [post]: the post number with the local bits clear. */
    private fun tdlibId(post: Long): Long = post shl 20

    private fun track(itemId: String, title: String, origin: PlaybackOrigin?, performer: String = "Artist") = PlaylistTrack(
        itemId = itemId,
        title = title,
        performer = performer,
        album = "Album",
        durationSeconds = 215,
        origin = origin,
        addedAt = 1_000L,
    )

    private fun entryId(n: Int): String = "aaaaaaaa-bbbb-4ccc-8ddd-${n.toString().padStart(12, '0')}"

    private fun playlist(
        vararg tracks: PlaylistTrack,
        sourceId: String? = null,
        description: String = "",
    ) = MusicPlaylist(
        id = playlistId,
        title = "Morning",
        description = description,
        tracks = tracks.toList(),
        createdAt = 1L,
        updatedAt = 1_700_000_000_000L,
        revision = 4L,
        sourcePlaylistId = sourceId,
    )

    /** A copy of [importedId] that came from revision [sourceRevision] of its file, with [localRevision] local changes since. */
    private fun copyOf(sourceRevision: Long, localRevision: Long, vararg tracks: PlaylistTrack): MusicPlaylist =
        playlist(*tracks, sourceId = importedId).copy(
            revision = localRevision,
            sourceRevision = sourceRevision,
            revisionAtSource = 0L,
        )

    private fun manifest(
        source: MusicPlaylist,
        options: BgMusicExportOptions = BgMusicExportOptions(),
        publicChats: Map<Long, String> = emptyMap(),
    ): BgMusicManifest = BgMusicLibraryMapping.toManifest(
        playlist = source,
        accountId = account,
        options = options,
        publicChats = publicChats,
        nowMs = 1_700_000_000_123L,
        favoritesTitle = "Favorites",
        untitled = "Untitled",
    )

    @Test
    fun defaultExportCarriesNoTelegramIdentifiersLinksOrAuthor() {
        val source = playlist(
            track(entryId(1), "Private", PlaybackOrigin(privateChat, tdlibId(42), account)),
            track(entryId(2), "Public", PlaybackOrigin(publicChat, tdlibId(7), account)),
        )

        val result = manifest(source, publicChats = mapOf(publicChat to "open_channel"))
        assertTrue(result.tracks.all { it.sources.isEmpty() })
        assertNull(result.author)
        assertNull(result.tracks.first().audioHash)

        val json = entryText(writeToBytes(result), "manifest.json")
        listOf(privateChat.toString(), publicChat.toString(), "open_channel", "telegram_public", "sources", account, "audio_hash")
            .forEach { leak -> assertFalse("manifest leaks $leak", json.contains(leak)) }
    }

    @Test
    fun publicLinksNeedOptInAPublicChatAndTheSameAccount() {
        val source = playlist(
            track(entryId(1), "Public", PlaybackOrigin(publicChat, tdlibId(42), account)),
            track(entryId(2), "Private", PlaybackOrigin(privateChat, tdlibId(43), account)),
            track(entryId(3), "Foreign", PlaybackOrigin(publicChat, tdlibId(44), "another-account")),
        )
        val publics = mapOf(publicChat to "some_channel")

        assertTrue(manifest(source, publicChats = publics).tracks.all { it.sources.isEmpty() })

        val linked = manifest(source, BgMusicExportOptions(includePublicLinks = true), publics)
        assertEquals(listOf(BgMusicSourceHint.TelegramPublic("some_channel", 42L)), linked.tracks[0].sources)
        assertTrue(linked.tracks[1].sources.isEmpty())
        assertTrue(linked.tracks[2].sources.isEmpty())
    }

    @Test
    fun originsWithLocalBitsOrInvalidUsernamesGiveNoHint() {
        val withLocalBits = PlaybackOrigin(publicChat, tdlibId(42) + 1, account)
        assertTrue(BgMusicLibraryMapping.publicHints(withLocalBits, account, mapOf(publicChat to "some_channel")).isEmpty())

        val shortName = PlaybackOrigin(publicChat, tdlibId(42), account)
        assertTrue(BgMusicLibraryMapping.publicHints(shortName, account, mapOf(publicChat to "abc")).isEmpty())
    }

    @Test
    fun authorIsWrittenOnlyWithConsent() {
        val source = playlist(track(entryId(1), "Song", null))

        assertNull(manifest(source, BgMusicExportOptions(includeAuthor = false, authorName = "Ilya")).author)
        assertEquals("Ilya", manifest(source, BgMusicExportOptions(includeAuthor = true, authorName = "  Ilya  ")).author?.name)
        assertNull(manifest(source, BgMusicExportOptions(includeAuthor = true, authorName = "   ")).author)
    }

    @Test
    fun favoritesGetAFreshIdOnEveryExportAndTheLocalizedTitle() {
        val favorites = MusicPlaylist(id = MUSIC_FAVORITES_PLAYLIST_ID, title = "Любимые", revision = 3L)

        val first = BgMusicLibraryMapping.exportPlaylistId(favorites)
        assertNotEquals(MUSIC_FAVORITES_PLAYLIST_ID, first)
        assertNotEquals(first, BgMusicLibraryMapping.exportPlaylistId(favorites))
        assertEquals("Favorites", manifest(favorites).title)
    }

    @Test
    fun uneditedCopyIsExportedAsTheFileItCameFrom() {
        val copy = copyOf(sourceRevision = 2L, localRevision = 0L, track(entryId(1), "Song", null))

        assertEquals(BgMusicExportIdentity(importedId, 2L), BgMusicLibraryMapping.exportIdentity(copy))
        assertEquals(importedId, manifest(copy).playlistId)
        assertEquals(2L, manifest(copy).revision)
    }

    @Test
    fun editedCopyIsExportedAsItsOwnPlaylistWithItsOwnRevision() {
        // The file is at revision 5; two local changes since the import make the copy a collection of the user's own.
        val copy = copyOf(sourceRevision = 5L, localRevision = 2L, track(entryId(1), "Song", null))

        assertEquals(BgMusicExportIdentity(playlistId, 2L), BgMusicLibraryMapping.exportIdentity(copy))
        assertEquals(playlistId, manifest(copy).playlistId)
        assertEquals(2L, manifest(copy).revision)
    }

    @Test
    fun copyIsExportedAsItsFileAgainAfterAnUpdate() {
        val updated = copyOf(sourceRevision = 5L, localRevision = 2L, track(entryId(1), "Song", null))
            .copy(sourceRevision = 6L, revision = 3L, revisionAtSource = 3L)

        assertEquals(BgMusicExportIdentity(importedId, 6L), BgMusicLibraryMapping.exportIdentity(updated))
    }

    @Test
    fun copyWithoutRevisionAtSourceCountsAsEdited() {
        val copy = copyOf(sourceRevision = 2L, localRevision = 0L, track(entryId(1), "Song", null))
            .copy(revisionAtSource = null)

        assertEquals(BgMusicExportIdentity(playlistId, 0L), BgMusicLibraryMapping.exportIdentity(copy))
    }

    @Test
    fun ownPlaylistKeepsItsOwnIdAndRevision() {
        assertEquals(BgMusicExportIdentity(playlistId, 4L), BgMusicLibraryMapping.exportIdentity(playlist()))
    }

    @Test
    fun exportThenImportKeepsOrderUnicodeAndDescription() {
        val rawTitles = listOf("Утро — ☕", "Café Ünïcödé 👨‍👩‍👧", "  Spaces\tand tabs  ", "Ж-Ж")
        val source = playlist(
            *rawTitles.mapIndexed { index, title ->
                track(entryId(index + 1), title, null, performer = "Исполнитель $index")
            }.toTypedArray(),
            description = "Первая строка\r\nВторая строка\n\n\n\nТретья",
        )

        val read = assertSuccess(readAll(writeToBytes(manifest(source)))).manifest
        val copy = BgMusicLibraryMapping.toPlaylistCopy(read, now = 5_000L, origins = emptyMap())

        assertEquals(read.playlistId, copy.sourcePlaylistId)
        assertEquals(0L, copy.revision)
        assertEquals(4L, copy.sourceRevision)
        assertEquals(0L, copy.revisionAtSource)
        assertEquals(listOf("Утро — ☕", "Café Ünïcödé 👨‍👩‍👧", "Spaces and tabs", "Ж-Ж"), copy.tracks.map { it.title })
        assertEquals(source.tracks.map { it.itemId }, copy.tracks.map { it.itemId })
        assertEquals(listOf("Исполнитель 0", "Исполнитель 1", "Исполнитель 2", "Исполнитель 3"), copy.tracks.map { it.performer })
        assertEquals("Первая строка\nВторая строка\n\nТретья", copy.description)
        assertEquals(copy.tracks.map { it.addedAt }.sorted(), copy.tracks.map { it.addedAt })
    }

    @Test
    fun importedTrackGetsAnOriginOnlyWhenItWasResolved() {
        val source = playlist(track(entryId(1), "First", null), track(entryId(2), "Second", null))
        val read = assertSuccess(readAll(writeToBytes(manifest(source)))).manifest
        val resolved = PlaybackOrigin(chatId = 1_234L, messageId = 9L, accountId = "recipient")

        val copy = BgMusicLibraryMapping.toPlaylistCopy(read, now = 1L, origins = mapOf(read.tracks[0].id to resolved))

        assertEquals(resolved, copy.tracks[0].origin)
        assertNull(copy.tracks[1].origin)
    }

    @Test
    fun fileNamesAreSafeAndKeepUnicode() {
        assertEquals("a_b_c.bgmusic", BgMusicLibraryMapping.fileNameFor("a/b:c"))
        assertEquals("playlist.bgmusic", BgMusicLibraryMapping.fileNameFor("  ..  "))
        assertEquals("playlist.bgmusic", BgMusicLibraryMapping.fileNameFor(""))
        assertEquals("Утро ☕.bgmusic", BgMusicLibraryMapping.fileNameFor("Утро ☕"))
    }
}
