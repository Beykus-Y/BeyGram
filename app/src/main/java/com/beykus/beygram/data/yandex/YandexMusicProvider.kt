package com.beykus.beygram.data.yandex

import android.util.Log
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderAccount
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderAlbum
import com.beykus.beygram.data.music.provider.ProviderAlbumTracks
import com.beykus.beygram.data.music.provider.ProviderAudio
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.data.music.provider.ProviderPlaylist
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Result of one sign-in poll. The UI repeats polls at the interval until [Signed] or [Failed]. */
internal sealed interface YandexSignInPoll {
    object Pending : YandexSignInPoll
    object SlowDown : YandexSignInPoll
    data class Signed(val account: MusicProviderAccount) : YandexSignInPoll
    data class Failed(val failure: MusicProviderFailure) : YandexSignInPoll
}

/**
 * Yandex Music source for the DEV build. Sign-in and sign-out are methods of this class (not of [MusicProvider]),
 * because only the DEV settings screen uses them.
 */
internal class YandexMusicProvider(
    private val store: YandexSessionStorage,
    private val http: YandexHttp,
    private val api: YandexApi,
    private val auth: YandexAuth,
    private val now: () -> Long = { System.currentTimeMillis() },
    /** Deletes what the app keeps about the account besides the session, such as the library snapshot. */
    private val onSignedOut: () -> Unit = {},
    /** False when the OAuth pair is missing. A seam for tests, where the build config is blank. */
    private val isConfigured: () -> Boolean = { YandexConfig.isConfigured },
) : MusicProvider {

    private val mutableState = MutableStateFlow<MusicProviderState>(MusicProviderState.SignedOut)
    override val state: StateFlow<MusicProviderState> = mutableState.asStateFlow()

    private val refreshLock = Any()

    /**
     * Identifies the signed-in account. Bumped, under [refreshLock], whenever the account is replaced or removed:
     * a sign-in commit, a sign-out, a refused refresh token. Not bumped by a plain token refresh. A request remembers
     * the value it started with, and its result is dropped if the value changed, so it can never act on another account.
     */
    @Volatile
    private var generation = 0L

    /**
     * The signed-in account and its generation, published together with [state] after every change, so a reader
     * never pairs one account's uid with another sign-in's generation.
     */
    @Volatile
    private var identity: YandexAccountIdentity? = null

    init {
        publish(store.load()?.let { MusicProviderState.SignedIn(it.toAccount()) } ?: MusicProviderState.SignedOut)
    }

    /** Publishes [value] and the matching identity. Under [refreshLock] wherever the account can change. */
    private fun publish(value: MusicProviderState) {
        identity = (value as? MusicProviderState.SignedIn)?.let { YandexAccountIdentity(it.account.uid, generation) }
        mutableState.value = value
    }

    /** The account signed in now with its generation, or null when signed out. Lock-free snapshot, like [accountGeneration]. */
    fun accountIdentity(): YandexAccountIdentity? = identity

    /**
     * The generation of the account signed in now, or null when signed out. A lock-free snapshot, safe on the main
     * thread (a token refresh holds [refreshLock] during its request). It lets a caller bind work to one sign-in; the
     * authoritative check is the one `withSessionGen` makes under the lock when it captures the session.
     */
    fun accountGeneration(): Long? = identity?.generation

    /** Moves to the next account generation and forgets what was cached for the previous one. Call under [refreshLock]. */
    private fun bumpGeneration() {
        generation += 1
        canvasDiagnostics = null
        synchronized(lyricsLock) { lyricsCache.clear() }
    }

    // Sign-in -----------------------------------------------------------------------------------------------------

    /** A code that was requested but not yet confirmed, with the time it stops being valid. */
    data class PendingSignIn(val code: YandexDeviceCode, val deadlineMs: Long)

    /**
     * Guards [pending]. Separate from [refreshLock], which is held during token refresh requests, so the screen can
     * cancel an attempt from the main thread without waiting for the network. Order: [refreshLock], then this one.
     */
    private val pendingLock = Any()

    /** The current sign-in attempt. Written under [pendingLock]; a poll of any other code is stale. */
    @Volatile
    private var pending: PendingSignIn? = null

    /** The pending code, if any. Kept here so a recreated screen can resume polling. */
    fun pendingSignIn(): PendingSignIn? = pending?.takeIf { now() < it.deadlineMs }

    /** Drops the pending attempt, so a poll still in flight for it cannot sign anyone in. */
    fun clearPendingSignIn() {
        synchronized(pendingLock) {
            attemptId += 1
            pending = null
        }
    }

    /**
     * Numbers the sign-in attempts. Written under [pendingLock]. An attempt reserves a number before it asks for a
     * device code; cancelling, signing out or starting another attempt bumps it, so a late code is never published.
     */
    private var attemptId = 0L

    /** True while [code] is the current attempt and has not expired. Call under [pendingLock]. */
    private fun isCurrentAttempt(code: YandexDeviceCode): Boolean {
        val current = pending ?: return false
        return current.code.deviceCode == code.deviceCode && now() < current.deadlineMs
    }

    suspend fun startSignIn(): ProviderResult<YandexDeviceCode> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext ProviderResult.Failure(MusicProviderFailure.Unavailable)
        // Reserve the attempt before the request: a cancel or a newer attempt during it makes this one stale.
        val attempt = synchronized(pendingLock) {
            attemptId += 1
            attemptId
        }
        when (val result = auth.requestDeviceCode(deviceId = store.deviceId())) {
            is ProviderResult.Failure -> result
            is ProviderResult.Success -> {
                // A cancelled screen does not interrupt the blocking request above, so check again here.
                ensureActive()
                // A new attempt replaces the old one, so polls of the old code become stale.
                val published = synchronized(pendingLock) {
                    if (attemptId == attempt) {
                        pending = PendingSignIn(result.value, now() + result.value.expiresInSec * 1_000L)
                        true
                    } else {
                        false
                    }
                }
                if (published) result else ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            }
        }
    }

    suspend fun pollSignIn(code: YandexDeviceCode): YandexSignInPoll = withContext(Dispatchers.IO) {
        val stale = YandexSignInPoll.Failed(MusicProviderFailure.NotSignedIn)
        // Cheap early exit: no request for an attempt that was cancelled, replaced or has expired.
        if (!synchronized(pendingLock) { isCurrentAttempt(code) }) return@withContext stale
        when (val outcome = auth.pollToken(code.deviceCode)) {
            YandexPollOutcome.Pending -> YandexSignInPoll.Pending
            YandexPollOutcome.SlowDown -> YandexSignInPoll.SlowDown
            is YandexPollOutcome.Failed -> {
                val wasCurrent = synchronized(pendingLock) {
                    val current = pending?.code?.deviceCode == code.deviceCode
                    if (current) pending = null
                    current
                }
                if (wasCurrent) YandexSignInPoll.Failed(outcome.failure) else stale
            }
            is YandexPollOutcome.Tokens -> {
                val tokens = outcome.tokens
                when (val account = api.account(tokens.accessToken)) {
                    is ProviderResult.Failure -> YandexSignInPoll.Failed(account.failure)
                    is ProviderResult.Success -> {
                        val session = YandexSession(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                            expiresAtMs = tokens.expiresAtMs,
                            uid = account.value.uid,
                            displayName = account.value.displayName,
                            hasSubscription = account.value.hasPlus,
                        )
                        // A cancelled screen does not interrupt the blocking requests above, so check again here.
                        ensureActive()
                        val committed = synchronized(refreshLock) {
                            val current = synchronized(pendingLock) {
                                isCurrentAttempt(code).also {
                                    if (it) {
                                        pending = null
                                        // A code request still in flight must not leave a new code pending after sign-in.
                                        attemptId += 1
                                    }
                                }
                            }
                            if (current) {
                                store.save(session)
                                bumpGeneration()
                                val signedIn = session.toAccount()
                                publish(MusicProviderState.SignedIn(signedIn))
                                signedIn
                            } else {
                                null
                            }
                        }
                        if (committed == null) {
                            stale
                        } else {
                            logSubscription(account.value)
                            YandexSignInPoll.Signed(committed)
                        }
                    }
                }
            }
        }
    }

    /** Last subscription status seen from the service, for the on-screen diagnostics row. No tokens or names. */
    @Volatile
    var subscriptionDiagnostics: String? = null
        private set

    /** Last failed lyrics request, redacted, for the diagnostics row. No tokens, links, lyrics or track ids. */
    @Volatile
    var lyricsDiagnostics: String? = null
        private set

    /** Last redacted track canvas/video field summary seen from API responses. No URLs, tokens or track ids. */
    @Volatile
    var canvasDiagnostics: String? = null
        private set

    internal fun recordCanvasDiagnostics(summary: String) {
        canvasDiagnostics = summary
    }

    /** DEV diagnostics: the flag the app derived and the key names the service sent. No tokens or names. */
    private fun logSubscription(account: YandexAccount) {
        val line = "hasPlus=${account.hasPlus}; ${account.plusSummary}"
        // The log is only a convenience; it must never break sign-in, and it is not available in plain unit tests.
        runCatching { Log.i(TAG_SUBSCRIPTION, line) }
        subscriptionDiagnostics = line
    }

    /** Removes the session from this device. The token is not revoked on the service side. */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        clearSession()
    }

    /** Deletes the stored session and publishes SignedOut. Under the lock, so a refresh cannot write it back. */
    private fun clearSession() {
        synchronized(refreshLock) {
            synchronized(pendingLock) {
                attemptId += 1
                pending = null
            }
            store.clear()
            bumpGeneration()
            publish(MusicProviderState.SignedOut)
        }
        runCatching { onSignedOut() }
    }

    /**
     * Deletes the stored session only if it is still the one of generation [gen]. A late failure of a request that
     * belonged to a previous account must not delete the account that signed in since. True when it cleared.
     */
    private fun clearSessionIf(gen: Long): Boolean {
        val cleared = synchronized(refreshLock) {
            if (generation != gen) {
                false
            } else {
                store.clear()
                bumpGeneration()
                publish(MusicProviderState.SignedOut)
                true
            }
        }
        if (cleared) runCatching { onSignedOut() }
        return cleared
    }

    /** Reloads uid, name and subscription from the service, for the account card. */
    suspend fun refreshAccount(): ProviderResult<MusicProviderAccount> = withSessionGen { session, gen ->
        when (val account = api.account(session.accessToken)) {
            is ProviderResult.Failure -> account
            is ProviderResult.Success -> {
                logSubscription(account.value)
                // Write back only if the account is still the one this request started with: a sign-out (or a
                // sign-in with another account) during the request must not be undone. The stored session is used
                // as the base, so a token refreshed meanwhile in the same generation is kept.
                val saved = synchronized(refreshLock) {
                    val current = store.load()
                    if (generation == gen && current != null) {
                        val updated = current.copy(
                            uid = account.value.uid,
                            displayName = account.value.displayName,
                            hasSubscription = account.value.hasPlus,
                        )
                        store.save(updated)
                        publish(MusicProviderState.SignedIn(updated.toAccount()))
                        updated.toAccount()
                    } else {
                        null
                    }
                }
                if (saved != null) ProviderResult.Success(saved)
                else ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            }
        }
    }

    // MusicProvider -----------------------------------------------------------------------------------------------

    override suspend fun search(query: String, limit: Int): ProviderResult<List<ProviderTrack>> =
        withSession { s -> api.search(s.accessToken, query, limit) }

    override suspend fun likedTracks(): ProviderResult<List<ProviderTrack>> = withSession { s ->
        when (val ids = api.likedTrackIds(s.accessToken, s.uid)) {
            is ProviderResult.Failure -> ids
            is ProviderResult.Success -> api.tracksByIds(s.accessToken, ids.value).map { tracks ->
                tracks.map { it.copy(liked = true) }
            }
        }
    }

    override suspend fun setLiked(trackId: String, liked: Boolean): ProviderResult<Unit> =
        withSession { s -> api.setLiked(s.accessToken, s.uid, trackId, liked) }

    override suspend fun setDisliked(trackId: String, disliked: Boolean): ProviderResult<Unit> =
        withSession { s -> api.setDisliked(s.accessToken, s.uid, trackId, disliked) }

    override suspend fun playlists(): ProviderResult<List<ProviderPlaylist>> =
        withSession { s -> api.playlists(s.accessToken, s.uid) }

    override suspend fun playlistTracks(kind: String, ownerUid: String?): ProviderResult<List<ProviderTrack>> =
        withSession { s -> api.playlistTracks(s.accessToken, ownerUid ?: s.uid, kind) }

    override suspend fun likedTrackIds(): ProviderResult<List<String>> =
        withSession { s -> api.likedTrackIds(s.accessToken, s.uid) }

    override suspend fun tracks(ids: List<String>): ProviderResult<List<ProviderTrack>> =
        if (ids.isEmpty()) ProviderResult.Success(emptyList()) else withSession { s -> api.tracksByIds(s.accessToken, ids) }

    override suspend fun likedPlaylists(): ProviderResult<List<ProviderPlaylist>> =
        withSession { s -> api.likedPlaylists(s.accessToken, s.uid) }

    /**
     * Looks the playlist up among the playlists the service lists for the account: its own, then the ones it follows.
     * The uuid the service reports must equal the link's, so no other playlist opens by mistake.
     */
    override suspend fun playlistByUuid(uuid: String): ProviderResult<ProviderPlaylist> {
        val wanted = uuid.trim().lowercase()
        val own = playlists()
        if (own is ProviderResult.Success) {
            own.value.firstOrNull { it.hasUuid(wanted) }?.let { return ProviderResult.Success(it) }
        }
        val liked = likedPlaylists()
        if (liked is ProviderResult.Success) {
            liked.value.firstOrNull { it.hasUuid(wanted) }?.let { return ProviderResult.Success(it) }
        }
        return ProviderResult.Failure(MusicProviderFailure.NotFound)
    }

    private fun ProviderPlaylist.hasUuid(wanted: String): Boolean = uuid?.trim()?.lowercase() == wanted

    override suspend fun likedAlbums(): ProviderResult<List<ProviderAlbum>> =
        withSession { s -> api.likedAlbums(s.accessToken, s.uid) }

    override suspend fun album(albumId: String): ProviderResult<ProviderAlbumTracks> =
        withSession { s -> api.album(s.accessToken, albumId) }

    /**
     * Lyrics per track. A result is reused for a while, so reopening the panel does not ask the service again.
     * Keyed by account generation and emptied whenever the generation changes; at most [LYRICS_CACHE_LIMIT] entries.
     */
    private val lyricsLock = Any()
    private val lyricsCache = object : LinkedHashMap<String, Pair<Long, ProviderResult<ProviderLyrics>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, ProviderResult<ProviderLyrics>>>?): Boolean =
            size > LYRICS_CACHE_LIMIT
    }

    /** Number of cached lyrics results. For tests. */
    internal fun lyricsCacheSize(): Int = synchronized(lyricsLock) { lyricsCache.size }

    override fun invalidateLyrics(trackId: String) {
        val key = "$generation:$trackId"
        synchronized(lyricsLock) { lyricsCache.remove(key) }
    }

    private fun lyricsTtl(result: ProviderResult<ProviderLyrics>): Long =
        if (result is ProviderResult.Success) YandexConfig.LYRICS_OK_TTL_MS else YandexConfig.LYRICS_FAILED_TTL_MS

    /** A failure that says something about the track is kept; one that says something about the session or network is not. */
    private fun isCacheable(result: ProviderResult<ProviderLyrics>): Boolean = when (result) {
        is ProviderResult.Success -> true
        is ProviderResult.Failure -> when (result.failure) {
            MusicProviderFailure.NotFound,
            MusicProviderFailure.ServiceChanged,
            MusicProviderFailure.Unknown -> true
            else -> false
        }
    }

    override suspend fun lyrics(trackId: String): ProviderResult<ProviderLyrics> {
        val gen = generation
        val key = "$gen:$trackId"
        val at = now()
        synchronized(lyricsLock) {
            val entry = lyricsCache[key]
            if (entry != null) {
                if (at - entry.first < lyricsTtl(entry.second)) return entry.second
                lyricsCache.remove(key)
            }
        }
        val outcome = withSession { s -> ProviderResult.Success(api.lyricsWithReport(s.accessToken, trackId)) }
        val result = when (outcome) {
            is ProviderResult.Success -> {
                val (result, report) = outcome.value
                if (result is ProviderResult.Failure) lyricsDiagnostics = "lyricsFailure=${result.failure}; $report"
                result
            }
            is ProviderResult.Failure -> ProviderResult.Failure(outcome.failure)
        }
        if (isCacheable(result)) {
            synchronized(lyricsLock) {
                // A result that arrives after the account changed belongs to nobody now.
                if (generation == gen) {
                    val stored = now()
                    val expired = lyricsCache.entries.filter { stored - it.value.first >= lyricsTtl(it.value.second) }
                    expired.forEach { lyricsCache.remove(it.key) }
                    lyricsCache[key] = stored to result
                }
            }
        }
        return result
    }

    override suspend fun audio(trackId: String): ProviderResult<ProviderAudio> = withSession { s ->
        if (!s.hasSubscription) ProviderResult.Failure(MusicProviderFailure.SubscriptionRequired)
        else api.audio(s.accessToken, trackId)
    }

    // «Моя волна» ---------------------------------------------------------------------------------------------------

    /**
     * Starts a radio session from [seeds]. Without a subscription nothing would play, so it fails at once. The calls
     * of a wave carry the [generation] (see [accountGeneration]) the wave belongs to: the session is captured under
     * the lock only if it is still that sign-in, otherwise the call fails with NotSignedIn before any request.
     */
    suspend fun waveStart(generation: Long, seeds: List<String>, queue: List<String>): ProviderResult<YandexWaveBatch> =
        withSessionGen(generation) { s, _ ->
            if (!s.hasSubscription) ProviderResult.Failure(MusicProviderFailure.SubscriptionRequired)
            else api.waveStart(s.accessToken, seeds, queue)
        }

    suspend fun waveNext(generation: Long, sessionId: String, queue: List<String>): ProviderResult<YandexWaveBatch> =
        withSessionGen(generation) { s, _ -> api.waveNext(s.accessToken, sessionId, queue) }

    suspend fun waveFeedback(generation: Long, sessionId: String, event: YandexWaveEvent, batchId: String?): ProviderResult<Unit> =
        withSessionGen(generation) { s, _ -> api.waveFeedback(s.accessToken, sessionId, event, batchId) }

    // Session handling --------------------------------------------------------------------------------------------

    private suspend fun <T> withSession(block: (YandexSession) -> ProviderResult<T>): ProviderResult<T> =
        withSessionGen { session, _ -> block(session) }

    /**
     * Runs [block] with a valid session. Refreshes the token first when it is about to expire, and once more
     * when the service answers 401. A refused refresh token signs the user out. The block also gets the account
     * generation the request belongs to. If the account changes at any point, the result is NotSignedIn: the
     * request never switches to another account and never publishes what an old account got.
     */
    private suspend fun <T> withSessionGen(block: (YandexSession, Long) -> ProviderResult<T>): ProviderResult<T> =
        withSessionGen(null, block)

    /**
     * As above. When [expectedGeneration] is not null, the session is captured only if the stored generation equals it,
     * checked in the same locked block that reads the store: a sign-in that completed after the caller looked can never
     * lend its token to the caller's request.
     */
    private suspend fun <T> withSessionGen(
        expectedGeneration: Long?,
        block: (YandexSession, Long) -> ProviderResult<T>,
    ): ProviderResult<T> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext ProviderResult.Failure(MusicProviderFailure.Unavailable)
            val (stored, gen) = synchronized(refreshLock) { store.load() to generation }
            if (expectedGeneration != null && gen != expectedGeneration) {
                return@withContext ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            }
            if (stored == null) return@withContext ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            val current = when (val fresh = ensureFresh(stored, gen)) {
                is ProviderResult.Failure -> return@withContext fresh
                is ProviderResult.Success -> fresh.value
            }
            val first = block(current, gen)
            if ((first as? ProviderResult.Failure)?.failure != MusicProviderFailure.SessionExpired) {
                return@withContext if (generation == gen) first else ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            }
            val retried = if (current.refreshToken == null) {
                first
            } else {
                when (val refreshed = refresh(current, gen)) {
                    is ProviderResult.Failure -> return@withContext refreshed
                    is ProviderResult.Success -> block(refreshed.value, gen)
                }
            }
            // Still rejected after the attempt: the session cannot be recovered, so the user is signed out here,
            // but only if it is still the session this request belongs to.
            if ((retried as? ProviderResult.Failure)?.failure == MusicProviderFailure.SessionExpired) {
                return@withContext if (clearSessionIf(gen)) retried else ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            }
            if (generation == gen) retried else ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
        }

    private fun ensureFresh(session: YandexSession, gen: Long): ProviderResult<YandexSession> =
        if (session.refreshToken == null || session.expiresAtMs - now() > REFRESH_MARGIN_MS) {
            ProviderResult.Success(session)
        } else {
            refresh(session, gen)
        }

    /** Refreshes the token of the session of generation [gen]. When the account changed meanwhile, nothing is done. */
    private fun refresh(session: YandexSession, gen: Long): ProviderResult<YandexSession> {
        var signedOut = false
        val result: ProviderResult<YandexSession> = synchronized(refreshLock) {
            if (generation != gen) {
                ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            } else {
                // Another thread may have refreshed while this one waited for the lock. Same generation, same account.
                val latest = store.load()
                val refreshToken = session.refreshToken
                if (latest == null) {
                    ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
                } else if (latest.accessToken != session.accessToken) {
                    ProviderResult.Success(latest)
                } else if (refreshToken == null) {
                    ProviderResult.Failure(MusicProviderFailure.SessionExpired)
                } else {
                    when (val refreshed = auth.refresh(refreshToken)) {
                        is ProviderResult.Failure -> {
                            if (refreshed.failure == MusicProviderFailure.SessionExpired) {
                                store.clear()
                                bumpGeneration()
                                publish(MusicProviderState.SignedOut)
                                signedOut = true
                            }
                            refreshed
                        }
                        is ProviderResult.Success -> {
                            val updated = session.copy(
                                accessToken = refreshed.value.accessToken,
                                refreshToken = refreshed.value.refreshToken ?: refreshToken,
                                expiresAtMs = refreshed.value.expiresAtMs,
                            )
                            store.save(updated)
                            ProviderResult.Success(updated)
                        }
                    }
                }
            }
        }
        if (signedOut) runCatching { onSignedOut() }
        return result
    }

    private fun YandexSession.toAccount(): MusicProviderAccount =
        MusicProviderAccount(uid = uid, displayName = displayName, hasSubscription = hasSubscription)

    private inline fun <T, R> ProviderResult<T>.map(transform: (T) -> R): ProviderResult<R> = when (this) {
        is ProviderResult.Success -> ProviderResult.Success(transform(value))
        is ProviderResult.Failure -> this
    }

    private companion object {
        /** Refresh this long before the access token expires. */
        const val REFRESH_MARGIN_MS = 5 * 60_000L

        /** Most lyrics results kept at once. */
        const val LYRICS_CACHE_LIMIT = 64
    }
}

private const val TAG_SUBSCRIPTION = "BeyGramYandexPlus"

/** Who is signed in and under which sign-in generation (see [YandexMusicProvider.accountGeneration]). */
internal data class YandexAccountIdentity(val uid: String, val generation: Long)
