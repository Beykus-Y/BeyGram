package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderAccount
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.ProviderLyrics
import com.beykus.beygram.data.music.provider.ProviderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Requests, sign-out, sign-in and the lyrics cache must stay tied to the account they started with. The calls block on
 * real threads, so the tests order the events with latches.
 */
class YandexMusicProviderAccountTest {


    private class FakeStorage : YandexSessionStorage {
        @Volatile var session: YandexSession? = null
        override fun load(): YandexSession? = session
        override fun save(session: YandexSession) { this.session = session }
        override fun clear() { session = null }
        override fun deviceId(): String = "device"
    }

    private class FakeApi : YandexApi(YandexHttp()) {
        val likedTokens = CopyOnWriteArrayList<String>()
        val lyricsTokens = CopyOnWriteArrayList<String>()
        val waveTokens = CopyOnWriteArrayList<String>()
        @Volatile var likedHandler: (String) -> ProviderResult<Unit> = { ProviderResult.Success(Unit) }
        @Volatile var lyricsHandler: (String, String) -> ProviderResult<ProviderLyrics> =
            { token, _ -> ProviderResult.Success(lyricsOf(token)) }

        override fun account(token: String): ProviderResult<YandexAccount> =
            ProviderResult.Success(YandexAccount(uid = token.removePrefix("tok"), displayName = "name-$token", hasPlus = true))

        override fun setLiked(token: String, uid: String, trackId: String, liked: Boolean): ProviderResult<Unit> {
            likedTokens.add(token)
            return likedHandler(token)
        }

        override fun waveNext(token: String, sessionId: String, queue: List<String>): ProviderResult<YandexWaveBatch> {
            waveTokens.add(token)
            return ProviderResult.Failure(MusicProviderFailure.Unknown)
        }

        override fun lyricsWithReport(token: String, trackId: String): Pair<ProviderResult<ProviderLyrics>, String> {
            lyricsTokens.add(token)
            return lyricsHandler(token, trackId) to "report"
        }
    }

    private class FakeAuth : YandexAuth(YandexHttp()) {
        @Volatile var codes = 0
        /** Called inside requestDeviceCode with the number of that request; may block. */
        @Volatile var codeGate: ((Int) -> Unit)? = null
        val refreshCalls = CopyOnWriteArrayList<String>()
        /** Token of the next successful poll. */
        @Volatile var pollToken: String = "tokA"
        @Volatile var pollRefresh: String? = null
        @Volatile var pollGate: (() -> Unit)? = null

        override fun requestDeviceCode(deviceId: String): ProviderResult<YandexDeviceCode> {
            val n = synchronized(this) { ++codes }
            codeGate?.invoke(n)
            return ProviderResult.Success(YandexDeviceCode("device-code-$n", "user", "https://example.test", 300, 5))
        }

        override fun pollToken(deviceCode: String): YandexPollOutcome {
            val tokens = YandexTokens(pollToken, pollRefresh, 10_000_000_000L)
            pollGate?.invoke()
            return YandexPollOutcome.Tokens(tokens)
        }

        override fun refresh(refreshToken: String): ProviderResult<YandexTokens> {
            refreshCalls.add(refreshToken)
            return ProviderResult.Failure(MusicProviderFailure.SessionExpired)
        }
    }

    private class Fixture {
        val storage = FakeStorage()
        val api = FakeApi()
        val auth = FakeAuth()
        @Volatile var signedOutCalls = 0
        val provider = YandexMusicProvider(
            store = storage,
            http = YandexHttp(),
            api = api,
            auth = auth,
            now = { 1_000_000L },
            onSignedOut = { signedOutCalls += 1 },
            isConfigured = { true },
        )

        /** Full device flow: a code, then a poll that returns [token]. */
        suspend fun signIn(token: String, refresh: String?) {
            auth.pollToken = token
            auth.pollRefresh = refresh
            val code = (provider.startSignIn() as ProviderResult.Success).value
            val poll = provider.pollSignIn(code)
            assertTrue("sign-in of $token: $poll", poll is YandexSignInPoll.Signed)
        }
    }

    private fun accountOf(uid: String) = MusicProviderState.SignedIn(MusicProviderAccount(uid, "name-tok$uid", true))

    private fun await(latch: CountDownLatch) = assertTrue("latch timed out", latch.await(10, TimeUnit.SECONDS))

    @Test
    fun delayedUnauthorizedOfOldAccountDoesNotTouchNewAccount() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = "refreshA")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.api.likedHandler = { token ->
            if (token == "tokA") {
                started.countDown()
                await(release)
                ProviderResult.Failure(MusicProviderFailure.SessionExpired)
            } else {
                ProviderResult.Success(Unit)
            }
        }
        val request = async(Dispatchers.Default) { f.provider.setLiked("track", true) }
        await(started)

        f.provider.signOut()
        f.signIn("tokB", refresh = "refreshB")
        release.countDown()
        val result = request.await()

        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), result)
        assertEquals(listOf("tokA"), f.api.likedTokens.toList())
        assertTrue("no refresh of either account", f.auth.refreshCalls.isEmpty())
        assertEquals("tokB", f.storage.session?.accessToken)
        assertEquals(accountOf("B"), f.provider.state.value)
    }

    @Test
    fun lateSessionExpiredOfOldAccountDoesNotDeleteNewAccount() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = null)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.api.likedHandler = { token ->
            if (token == "tokA") {
                started.countDown()
                await(release)
            }
            ProviderResult.Failure(MusicProviderFailure.SessionExpired)
        }
        val request = async(Dispatchers.Default) { f.provider.setLiked("track", true) }
        await(started)

        f.provider.signOut()
        val signedOutBefore = f.signedOutCalls
        f.signIn("tokB", refresh = "refreshB")
        release.countDown()
        val result = request.await()

        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), result)
        assertEquals("tokB", f.storage.session?.accessToken)
        assertEquals(accountOf("B"), f.provider.state.value)
        assertEquals("the late failure must not run the sign-out cleanup", signedOutBefore, f.signedOutCalls)
    }

    @Test
    fun sessionExpiredOfCurrentAccountSignsOut() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = null)
        f.api.likedHandler = { ProviderResult.Failure(MusicProviderFailure.SessionExpired) }

        val result = f.provider.setLiked("track", true)

        assertEquals(ProviderResult.Failure(MusicProviderFailure.SessionExpired), result)
        assertNull(f.storage.session)
        assertEquals(MusicProviderState.SignedOut, f.provider.state.value)
        assertEquals(1, f.signedOutCalls)
    }

    @Test
    fun lyricsAreNotServedAcrossAccounts() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = null)

        val first = f.provider.lyrics("1")
        assertEquals(ProviderResult.Success(lyricsOf("tokA")), first)
        assertEquals(first, f.provider.lyrics("1"))
        assertEquals("the second call is served from the cache", listOf("tokA"), f.api.lyricsTokens.toList())

        f.provider.signOut()
        assertEquals(0, f.provider.lyricsCacheSize())
        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), f.provider.lyrics("1"))
        assertEquals(listOf("tokA"), f.api.lyricsTokens.toList())

        f.signIn("tokB", refresh = null)
        assertEquals(ProviderResult.Success(lyricsOf("tokB")), f.provider.lyrics("1"))
        assertEquals(listOf("tokA", "tokB"), f.api.lyricsTokens.toList())
        assertEquals(1, f.provider.lyricsCacheSize())
    }

    @Test
    fun lateLyricsOfOldAccountAreNotCached() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = null)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.api.lyricsHandler = { token, _ ->
            started.countDown()
            await(release)
            ProviderResult.Success(lyricsOf(token))
        }
        val request = async(Dispatchers.Default) { f.provider.lyrics("1") }
        await(started)

        f.provider.signOut()
        release.countDown()
        val result = request.await()

        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), result)
        assertEquals(0, f.provider.lyricsCacheSize())
    }

    @Test
    fun lyricsCacheIsBounded() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = null)

        for (i in 0 until 200) f.provider.lyrics("track-$i")

        assertEquals(64, f.provider.lyricsCacheSize())
    }

    @Test
    fun pollFinishingAfterCancelDoesNotSignIn() = runBlocking {
        val f = Fixture()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.auth.pollGate = {
            started.countDown()
            await(release)
        }
        val code = (f.provider.startSignIn() as ProviderResult.Success).value
        val poll = async(Dispatchers.Default) { f.provider.pollSignIn(code) }
        await(started)

        f.provider.clearPendingSignIn()
        release.countDown()
        val result = poll.await()

        assertTrue("stale poll: $result", result is YandexSignInPoll.Failed)
        assertNull(f.storage.session)
        assertEquals(MusicProviderState.SignedOut, f.provider.state.value)
        assertNull(f.provider.pendingSignIn())
    }

    @Test
    fun pollOfReplacedCodeDoesNotSignIn() = runBlocking {
        val f = Fixture()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.auth.pollGate = {
            started.countDown()
            await(release)
        }
        val oldCode = (f.provider.startSignIn() as ProviderResult.Success).value
        val poll = async(Dispatchers.Default) { f.provider.pollSignIn(oldCode) }
        await(started)

        val newCode = (f.provider.startSignIn() as ProviderResult.Success).value
        release.countDown()
        val result = poll.await()

        assertTrue("stale poll: $result", result is YandexSignInPoll.Failed)
        assertNull(f.storage.session)
        assertEquals(MusicProviderState.SignedOut, f.provider.state.value)
        assertEquals(newCode, f.provider.pendingSignIn()?.code)
    }

    @Test
    fun waveCallOfOldSignInIsRefusedBeforeAnyRequest() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = null)
        val genA = f.provider.accountGeneration()!!

        f.provider.signOut()
        assertNull(f.provider.accountGeneration())
        f.signIn("tokB", refresh = null)
        val result = f.provider.waveNext(genA, "session", emptyList())

        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), result)
        assertTrue("no request with any token", f.api.waveTokens.isEmpty())
        // The same call under the current generation reaches the service with the current token.
        f.provider.waveNext(f.provider.accountGeneration()!!, "session", emptyList())
        assertEquals(listOf("tokB"), f.api.waveTokens.toList())
    }

    @Test
    fun reSignInOfTheSameAccountChangesTheGeneration() = runBlocking {
        val f = Fixture()
        f.signIn("tokA", refresh = null)
        val first = f.provider.accountGeneration()!!
        f.provider.signOut()
        f.signIn("tokA", refresh = null)

        assertTrue(f.provider.accountGeneration()!! != first)
        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), f.provider.waveNext(first, "s", emptyList()))
        assertTrue(f.api.waveTokens.isEmpty())
    }

    @Test
    fun startSignInsAnsweringInReverseOrderKeepTheNewerAttempt() = runBlocking {
        val f = Fixture()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        f.auth.codeGate = { n ->
            if (n == 1) {
                firstStarted.countDown()
                await(releaseFirst)
            }
        }
        val older = async(Dispatchers.Default) { f.provider.startSignIn() }
        await(firstStarted)

        val newer = f.provider.startSignIn() as ProviderResult.Success
        releaseFirst.countDown()
        val olderResult = older.await()

        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), olderResult)
        assertEquals(newer.value, f.provider.pendingSignIn()?.code)
    }

    @Test
    fun identityPairsUidWithItsOwnGeneration() = runBlocking {
        val f = Fixture()
        assertNull(f.provider.accountIdentity())
        f.signIn("tokA", refresh = null)
        val identity = f.provider.accountIdentity()!!
        assertEquals((f.provider.state.value as MusicProviderState.SignedIn).account.uid, identity.uid)
        assertEquals(f.provider.accountGeneration(), identity.generation)
        f.provider.signOut()
        assertNull(f.provider.accountIdentity())
        assertNull(f.provider.accountGeneration())
    }

    @Test
    fun codeRequestInFlightDuringSignInLeavesNothingPending() = runBlocking {
        val f = Fixture()
        f.auth.pollToken = "tokA"
        f.auth.pollRefresh = null
        val code = (f.provider.startSignIn() as ProviderResult.Success).value
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.auth.codeGate = { n ->
            if (n == 2) {
                started.countDown()
                await(release)
            }
        }
        val second = async(Dispatchers.Default) { f.provider.startSignIn() }
        await(started)

        // The first code is confirmed while the second request is still on the network.
        assertTrue(f.provider.pollSignIn(code) is YandexSignInPoll.Signed)
        release.countDown()

        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), second.await())
        assertNull(f.provider.pendingSignIn())
        assertTrue(f.provider.state.value is MusicProviderState.SignedIn)
    }

    @Test
    fun clearPendingSignInDuringTheCodeRequestLeavesNothingPending() = runBlocking {
        val f = Fixture()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.auth.codeGate = {
            started.countDown()
            await(release)
        }
        val attempt = async(Dispatchers.Default) { f.provider.startSignIn() }
        await(started)

        f.provider.clearPendingSignIn()
        release.countDown()
        val result = attempt.await()

        assertEquals(ProviderResult.Failure(MusicProviderFailure.NotSignedIn), result)
        assertNull(f.provider.pendingSignIn())
    }
}

private fun lyricsOf(token: String) = ProviderLyrics(lines = emptyList(), plainText = "text-$token", copyrightHolder = null)
