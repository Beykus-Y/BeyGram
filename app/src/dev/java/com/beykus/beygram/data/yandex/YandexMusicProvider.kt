package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderAccount
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderAudio
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.data.music.provider.ProviderPlaylist
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val store: YandexSessionStore,
    private val http: YandexHttp,
    private val api: YandexApi,
    private val auth: YandexAuth,
    private val now: () -> Long = { System.currentTimeMillis() },
) : MusicProvider {

    private val mutableState = MutableStateFlow<MusicProviderState>(MusicProviderState.SignedOut)
    override val state: StateFlow<MusicProviderState> = mutableState.asStateFlow()

    private val refreshLock = Any()

    init {
        mutableState.value = store.load()?.let { MusicProviderState.SignedIn(it.toAccount()) } ?: MusicProviderState.SignedOut
    }

    // Sign-in -----------------------------------------------------------------------------------------------------

    suspend fun startSignIn(): ProviderResult<YandexDeviceCode> = withContext(Dispatchers.IO) {
        if (!YandexConfig.isConfigured) return@withContext ProviderResult.Failure(MusicProviderFailure.Unavailable)
        auth.requestDeviceCode(deviceId = java.util.UUID.randomUUID().toString())
    }

    suspend fun pollSignIn(code: YandexDeviceCode): YandexSignInPoll = withContext(Dispatchers.IO) {
        when (val outcome = auth.pollToken(code.deviceCode)) {
            YandexPollOutcome.Pending -> YandexSignInPoll.Pending
            YandexPollOutcome.SlowDown -> YandexSignInPoll.SlowDown
            is YandexPollOutcome.Failed -> YandexSignInPoll.Failed(outcome.failure)
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
                        store.save(session)
                        val signedIn = session.toAccount()
                        mutableState.value = MusicProviderState.SignedIn(signedIn)
                        YandexSignInPoll.Signed(signedIn)
                    }
                }
            }
        }
    }

    /** Removes the session from this device. The token is not revoked on the service side. */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        clearSession()
    }

    /** Deletes the stored session and publishes SignedOut. Under the lock, so a refresh cannot write it back. */
    private fun clearSession() {
        synchronized(refreshLock) {
            store.clear()
            mutableState.value = MusicProviderState.SignedOut
        }
    }

    /** Reloads uid, name and subscription from the service, for the account card. */
    suspend fun refreshAccount(): ProviderResult<MusicProviderAccount> = withSession { session ->
        when (val account = api.account(session.accessToken)) {
            is ProviderResult.Failure -> account
            is ProviderResult.Success -> {
                val updated = session.copy(
                    uid = account.value.uid,
                    displayName = account.value.displayName,
                    hasSubscription = account.value.hasPlus,
                )
                // Write back only if the session is still the one this request started with: a sign-out
                // (or a sign-in with another account) during the request must not be undone.
                val saved = synchronized(refreshLock) {
                    val current = store.load()
                    if (current?.accessToken == session.accessToken) {
                        store.save(updated)
                        mutableState.value = MusicProviderState.SignedIn(updated.toAccount())
                        true
                    } else {
                        false
                    }
                }
                if (saved) ProviderResult.Success(updated.toAccount())
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

    override suspend fun playlists(): ProviderResult<List<ProviderPlaylist>> =
        withSession { s -> api.playlists(s.accessToken, s.uid) }

    override suspend fun playlistTracks(kind: String): ProviderResult<List<ProviderTrack>> =
        withSession { s -> api.playlistTracks(s.accessToken, s.uid, kind) }

    override suspend fun lyrics(trackId: String): ProviderResult<ProviderLyrics> =
        withSession { s -> api.lyrics(s.accessToken, trackId) }

    override suspend fun audio(trackId: String): ProviderResult<ProviderAudio> = withSession { s ->
        if (!s.hasSubscription) ProviderResult.Failure(MusicProviderFailure.SubscriptionRequired)
        else api.audio(s.accessToken, trackId)
    }

    // Session handling --------------------------------------------------------------------------------------------

    /**
     * Runs [block] with a valid session. Refreshes the token first when it is about to expire, and once more
     * when the service answers 401. A refused refresh token signs the user out.
     */
    private suspend fun <T> withSession(block: (YandexSession) -> ProviderResult<T>): ProviderResult<T> =
        withContext(Dispatchers.IO) {
            if (!YandexConfig.isConfigured) return@withContext ProviderResult.Failure(MusicProviderFailure.Unavailable)
            val stored = store.load() ?: return@withContext ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
            val current = when (val fresh = ensureFresh(stored)) {
                is ProviderResult.Failure -> return@withContext fresh
                is ProviderResult.Success -> fresh.value
            }
            val first = block(current)
            if ((first as? ProviderResult.Failure)?.failure != MusicProviderFailure.SessionExpired) {
                return@withContext first
            }
            val retried = if (current.refreshToken == null) {
                first
            } else {
                when (val refreshed = refresh(current)) {
                    is ProviderResult.Failure -> refreshed
                    is ProviderResult.Success -> block(refreshed.value)
                }
            }
            // Still rejected after the attempt: the session cannot be recovered, so the user is signed out here.
            if ((retried as? ProviderResult.Failure)?.failure == MusicProviderFailure.SessionExpired) {
                clearSession()
            }
            retried
        }

    private fun ensureFresh(session: YandexSession): ProviderResult<YandexSession> =
        if (session.refreshToken == null || session.expiresAtMs - now() > REFRESH_MARGIN_MS) {
            ProviderResult.Success(session)
        } else {
            refresh(session)
        }

    private fun refresh(session: YandexSession): ProviderResult<YandexSession> = synchronized(refreshLock) {
        // Another thread may have refreshed while this one waited for the lock.
        val latest = store.load() ?: return ProviderResult.Failure(MusicProviderFailure.NotSignedIn)
        if (latest.accessToken != session.accessToken) return ProviderResult.Success(latest)
        val refreshToken = session.refreshToken ?: return ProviderResult.Failure(MusicProviderFailure.SessionExpired)
        when (val result = auth.refresh(refreshToken)) {
            is ProviderResult.Failure -> {
                if (result.failure == MusicProviderFailure.SessionExpired) {
                    store.clear()
                    mutableState.value = MusicProviderState.SignedOut
                }
                result
            }
            is ProviderResult.Success -> {
                val updated = session.copy(
                    accessToken = result.value.accessToken,
                    refreshToken = result.value.refreshToken ?: refreshToken,
                    expiresAtMs = result.value.expiresAtMs,
                )
                store.save(updated)
                ProviderResult.Success(updated)
            }
        }
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
    }
}
