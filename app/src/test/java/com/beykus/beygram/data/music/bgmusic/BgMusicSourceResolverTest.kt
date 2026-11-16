package com.beykus.beygram.data.music.bgmusic

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMusicSourceResolverTest {

    @Test
    fun firstHintResolvesToOriginWithShiftedMessageId() {
        val chats = FakeChats(mapOf("alpha" to 100L, "beta" to 200L))
        val origins = FakeOrigins()
        val manifest = testManifest(tracks = listOf(trackWith(1, hint("alpha", 5), hint("beta", 6))))

        val found = resolveWith(manifest, chats, origins)

        assertEquals(setOf(testTrackId(1)), found.keys)
        val track = found.getValue(testTrackId(1))
        val expected = origin(chatId = 100L, post = 5L)
        assertEquals(testTrackId(1), track.trackId)
        assertEquals(expected, track.origin)
        assertEquals(expected, track.item.origin)
        assertEquals(ACCOUNT, track.origin.accountId)
        assertEquals(listOf("alpha"), chats.calls)
        assertEquals(listOf(listOf(expected)), origins.rounds)
    }

    @Test
    fun unresolvedFirstUsernameFallsBackToSecondHintInRoundTwo() {
        val chats = FakeChats(mapOf("beta" to 200L))
        val origins = FakeOrigins()
        val manifest = testManifest(tracks = listOf(trackWith(1, hint("alpha", 5), hint("beta", 6))))

        val found = resolveWith(manifest, chats, origins)

        assertEquals(origin(chatId = 200L, post = 6L), found[testTrackId(1)]?.origin)
        // Round 1 had no playable candidate, so the origins resolver is only called in round 2.
        assertEquals(listOf(listOf(origin(chatId = 200L, post = 6L))), origins.rounds)
    }

    @Test
    fun deadFirstHintFallsBackToSecondHintInRoundTwo() {
        val chats = FakeChats(mapOf("alpha" to 100L, "beta" to 200L))
        val origins = FakeOrigins(deadPosts = setOf(5L))
        val manifest = testManifest(tracks = listOf(trackWith(1, hint("alpha", 5), hint("beta", 6))))

        val found = resolveWith(manifest, chats, origins)

        assertEquals(origin(chatId = 200L, post = 6L), found[testTrackId(1)]?.origin)
        assertEquals(
            listOf(
                listOf(origin(chatId = 100L, post = 5L)),
                listOf(origin(chatId = 200L, post = 6L)),
            ),
            origins.rounds,
        )
    }

    @Test
    fun trackWithoutHintsIsAbsent() {
        val chats = FakeChats(mapOf("alpha" to 100L))
        val origins = FakeOrigins()
        val manifest = testManifest(tracks = listOf(testTrack(1)))

        val found = resolveWith(manifest, chats, origins)

        assertTrue(found.isEmpty())
        assertTrue(origins.rounds.isEmpty())
        assertTrue(chats.calls.isEmpty())
    }

    @Test
    fun trackWhoseHintsAreAllDeadOrUnresolvedIsAbsent() {
        val chats = FakeChats(mapOf("alpha" to 100L, "beta" to 200L))
        val origins = FakeOrigins(deadPosts = setOf(5L, 6L))
        val manifest = testManifest(
            tracks = listOf(
                trackWith(1, hint("alpha", 5), hint("beta", 6)),
                trackWith(2, hint("gamma", 9)),
            ),
        )

        val found = resolveWith(manifest, chats, origins)

        assertTrue(found.isEmpty())
    }

    @Test
    fun sameUsernameIsResolvedOncePerCallAndUnresolvedUsernameIsRemembered() {
        val chats = FakeChats(mapOf("alpha" to 100L))
        val origins = FakeOrigins()
        val manifest = testManifest(
            tracks = listOf(
                trackWith(1, hint("alpha", 5)),
                trackWith(2, hint("alpha", 6)),
                trackWith(3, hint("alpha", 7), hint("missing", 8)),
                trackWith(4, hint("missing", 9)),
                trackWith(5, hint("missing", 10)),
            ),
        )

        val found = resolveWith(manifest, chats, origins)

        assertEquals(setOf(testTrackId(1), testTrackId(2), testTrackId(3)), found.keys)
        assertEquals(listOf("alpha", "missing"), chats.calls)
    }

    @Test
    fun trackFoundInRoundOneIsNotQueriedInRoundTwo() {
        val chats = FakeChats(mapOf("alpha" to 100L, "beta" to 200L))
        val origins = FakeOrigins(deadPosts = setOf(6L))
        val manifest = testManifest(
            tracks = listOf(
                trackWith(1, hint("alpha", 5), hint("beta", 6)),
                trackWith(2, hint("beta", 6), hint("alpha", 7)),
            ),
        )

        val found = resolveWith(manifest, chats, origins)

        assertEquals(2, origins.rounds.size)
        assertEquals(
            listOf(origin(chatId = 100L, post = 5L), origin(chatId = 200L, post = 6L)),
            origins.rounds[0],
        )
        assertEquals(listOf(origin(chatId = 100L, post = 7L)), origins.rounds[1])
        assertEquals(origin(chatId = 100L, post = 5L), found[testTrackId(1)]?.origin)
        assertEquals(origin(chatId = 100L, post = 7L), found[testTrackId(2)]?.origin)
    }

    @Test
    fun emptyManifestReturnsEmptyMapWithoutCallingOrigins() {
        val chats = FakeChats(mapOf("alpha" to 100L))
        val origins = FakeOrigins()
        val manifest = testManifest(tracks = emptyList())

        val found = resolveWith(manifest, chats, origins)

        assertTrue(found.isEmpty())
        assertTrue(origins.rounds.isEmpty())
        assertTrue(chats.calls.isEmpty())
    }

    private companion object {
        const val ACCOUNT = "account-1"
    }

    private fun hint(username: String, post: Long): BgMusicSourceHint.TelegramPublic =
        BgMusicSourceHint.TelegramPublic(username = username, messageId = post)

    private fun trackWith(n: Int, vararg sources: BgMusicSourceHint): BgMusicTrack =
        testTrack(n).copy(sources = sources.toList())

    private fun origin(chatId: Long, post: Long): PlaybackOrigin = PlaybackOrigin(
        chatId = chatId,
        messageId = post shl BgMusicLibraryMapping.TDLIB_MESSAGE_ID_SHIFT,
        accountId = ACCOUNT,
    )

    private fun resolveWith(
        manifest: BgMusicManifest,
        chats: FakeChats,
        origins: FakeOrigins,
    ): Map<String, BgMusicResolvedTrack> = runBlocking {
        BgMusicSourceResolver.resolve(
            accountId = ACCOUNT,
            manifest = manifest,
            resolveChat = { chats.resolve(it) },
            resolveOrigins = { origins.resolve(it) },
        )
    }

    /** Stands in for the username lookup. Records every call, so a test can count them. */
    private class FakeChats(private val chatIds: Map<String, Long>) {
        val calls = ArrayList<String>()

        fun resolve(username: String): Long? {
            calls.add(username)
            return chatIds[username]
        }
    }

    /** Stands in for resolveMusicOrigins. Records each round's origins; an origin whose post is in [deadPosts] is not playable. */
    private class FakeOrigins(private val deadPosts: Set<Long> = emptySet()) {
        val rounds = ArrayList<List<PlaybackOrigin?>>()

        fun resolve(origins: List<PlaybackOrigin?>): List<PlaybackItem?> {
            rounds.add(origins)
            return origins.map { origin ->
                if (origin == null) return@map null
                val post = origin.messageId shr BgMusicLibraryMapping.TDLIB_MESSAGE_ID_SHIFT
                if (post in deadPosts) null else playable(origin)
            }
        }

        private fun playable(origin: PlaybackOrigin): PlaybackItem = PlaybackItem(
            key = "key-${origin.chatId}-${origin.messageId}",
            kind = MediaKind.Audio,
            path = "/track.flac",
            title = "Track",
            durationSeconds = 180,
            origin = origin,
        )
    }
}
