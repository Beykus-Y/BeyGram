package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers

internal const val TEST_ACCOUNT = "acc"

/** In-memory persistence. [failSaves] simulates a full disk; [loadOverride] simulates an unreadable file. */
internal class FakeMusicPersistence : MusicLibraryPersistence {
    val stored = HashMap<String, MusicLibrary>()
    var loadOverride: MusicLibraryLoad? = null
    var failSaves = false
    var saves = 0

    override suspend fun load(accountId: String): MusicLibraryLoad {
        loadOverride?.let { return it }
        val library = stored[accountId] ?: return MusicLibraryLoad.Empty
        return MusicLibraryLoad.Loaded(library)
    }

    override suspend fun save(library: MusicLibrary) {
        if (failSaves) throw IOException("disk full")
        stored[library.accountId] = library
        saves++
    }

    override suspend fun clear(accountId: String) {
        stored.remove(accountId)
    }
}

/** Persistence whose loads wait for [releaseLoads], so a test can look at the state while a load is in progress. */
internal class GatedLoadPersistence(private val inner: MusicLibraryPersistence) : MusicLibraryPersistence {
    val releaseLoads = CompletableDeferred<Unit>()

    override suspend fun load(accountId: String): MusicLibraryLoad {
        releaseLoads.await()
        return inner.load(accountId)
    }

    override suspend fun save(library: MusicLibrary) = inner.save(library)

    override suspend fun clear(accountId: String) = inner.clear(accountId)
}

internal class FakeClock(var now: Long = 1_000L)

/** Repository with deterministic ids ("id-1", "id-2", ...) and a clock the test controls. */
internal fun repositoryFor(
    persistence: MusicLibraryPersistence,
    clock: FakeClock,
    coverRoot: File? = null,
    coverOwners: MusicCoverOwners = MemoryMusicCoverOwners()
): MusicLibraryRepository {
    var counter = 0
    return MusicLibraryRepository(
        persistence = persistence,
        coverRoot = coverRoot,
        clock = { clock.now },
        newId = { "id-${++counter}" },
        ioDispatcher = Dispatchers.Unconfined,
        coverOwners = coverOwners
    )
}

/** A Telegram-origin track. Blank [itemId] lets the repository assign a fresh entry id. */
internal fun testTrack(
    message: Long,
    title: String = "Track $message",
    account: String = TEST_ACCOUNT,
    itemId: String = "",
    addedAt: Long = 0L,
    durationSeconds: Int = 180
): PlaylistTrack = PlaylistTrack(
    itemId = itemId,
    title = title,
    performer = "Artist",
    album = "Album",
    durationSeconds = durationSeconds,
    origin = PlaybackOrigin(chatId = 1L, messageId = message, accountId = account),
    addedAt = addedAt
)

internal fun <T> MusicOutcome<T>.doneValue(): T {
    val outcome = this
    return when (outcome) {
        is MusicOutcome.Done -> outcome.value
        is MusicOutcome.Failed -> throw AssertionError("expected success but was ${outcome.failure}")
    }
}

internal fun MusicOutcome<*>.failureOf(): MusicFailure {
    val outcome = this
    return when (outcome) {
        is MusicOutcome.Failed -> outcome.failure
        is MusicOutcome.Done<*> -> throw AssertionError("expected a failure but was ${outcome.value}")
    }
}
