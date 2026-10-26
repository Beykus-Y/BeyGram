package com.beykus.beygram.ui.screens.chats

import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsConnectionStatusTest {
    private val holdMillis = 1_000L

    @Test fun connectedAndUnknownKeepThePlainTitle() {
        assertEquals(ChatsConnectionStatus.None, TelegramConnectionState.Connected.toChatsConnectionStatus())
        // Unknown is not a real TDLib answer, so the header must not invent a status for it.
        assertEquals(ChatsConnectionStatus.None, TelegramConnectionState.Unknown.toChatsConnectionStatus())
    }

    @Test fun everyReportedStateHasItsOwnHeaderLine() {
        assertEquals(
            ChatsConnectionStatus.WaitingForNetwork,
            TelegramConnectionState.WaitingForNetwork.toChatsConnectionStatus()
        )
        assertEquals(
            ChatsConnectionStatus.Connecting,
            TelegramConnectionState.Connecting.toChatsConnectionStatus()
        )
        assertEquals(
            ChatsConnectionStatus.Updating,
            TelegramConnectionState.Updating.toChatsConnectionStatus()
        )
    }

    @Test fun transientStatusAppearsOnlyAfterItHolds() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val tracker = ChatsConnectionStatusTracker(holdMillis)
        val job = launch {
            tracker.follow("legacy", TelegramConnectionState.WaitingForNetwork) { published += it }
        }
        runCurrent()
        assertEquals(listOf<ChatsConnectionStatus>(ChatsConnectionStatus.None), published)

        advanceTimeBy(holdMillis - 1)
        assertEquals(listOf<ChatsConnectionStatus>(ChatsConnectionStatus.None), published)

        advanceTimeBy(2)
        assertEquals(
            listOf(ChatsConnectionStatus.None, ChatsConnectionStatus.WaitingForNetwork),
            published
        )
        job.cancel()
    }

    @Test fun connectingAndUpdatingUseTheSameHold() = runTest {
        listOf(TelegramConnectionState.Connecting, TelegramConnectionState.Updating).forEach { state ->
            val published = mutableListOf<ChatsConnectionStatus>()
            val tracker = ChatsConnectionStatusTracker(holdMillis)
            val job = launch { tracker.follow("legacy", state) { published += it } }
            advanceTimeBy(holdMillis + 1)
            assertEquals(state.toChatsConnectionStatus(), published.last())
            job.cancel()
        }
    }

    @Test fun restoringTheConnectionHidesTheLineImmediately() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val tracker = ChatsConnectionStatusTracker(holdMillis)
        val offline = launch {
            tracker.follow("legacy", TelegramConnectionState.WaitingForNetwork) { published += it }
        }
        advanceTimeBy(holdMillis + 1)
        assertEquals(ChatsConnectionStatus.WaitingForNetwork, published.last())
        offline.cancel()

        // A new TDLib state restarts the effect: the plain title must not wait for the hold.
        val online = launch {
            tracker.follow("legacy", TelegramConnectionState.Connected) { published += it }
        }
        runCurrent()
        assertEquals(ChatsConnectionStatus.None, published.last())
        advanceTimeBy(holdMillis * 2)
        assertEquals(ChatsConnectionStatus.None, published.last())
        online.cancel()
    }

    @Test fun aCancelledHoldNeverPublishesTheStatus() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val tracker = ChatsConnectionStatusTracker(holdMillis)
        val job = launch {
            tracker.follow("legacy", TelegramConnectionState.Updating) { published += it }
        }
        runCurrent()
        advanceTimeBy(holdMillis / 2)
        job.cancel()
        advanceTimeBy(holdMillis * 2)

        assertEquals(listOf<ChatsConnectionStatus>(ChatsConnectionStatus.None), published)
    }

    @Test fun anAlreadyShownLineStaysUntilTheNextStateHolds() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val tracker = ChatsConnectionStatusTracker(holdMillis)
        val offline = launch {
            tracker.follow("legacy", TelegramConnectionState.WaitingForNetwork) { published += it }
        }
        advanceTimeBy(holdMillis + 1)
        assertEquals(ChatsConnectionStatus.WaitingForNetwork, published.last())
        offline.cancel()

        // Same account: the old line stays instead of blinking back to the title while Connecting settles.
        val connecting = launch {
            tracker.follow("legacy", TelegramConnectionState.Connecting) { published += it }
        }
        runCurrent()
        assertEquals(ChatsConnectionStatus.WaitingForNetwork, published.last())
        advanceTimeBy(holdMillis + 1)
        assertEquals(ChatsConnectionStatus.Connecting, published.last())
        connecting.cancel()
    }

    @Test fun switchingAccountsClearsThePreviousAccountLineAtOnce() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val tracker = ChatsConnectionStatusTracker(holdMillis)
        val first = launch {
            tracker.follow("account-a", TelegramConnectionState.WaitingForNetwork) { published += it }
        }
        advanceTimeBy(holdMillis + 1)
        assertEquals(ChatsConnectionStatus.WaitingForNetwork, published.last())
        first.cancel()

        val second = launch {
            tracker.follow("account-b", TelegramConnectionState.Connecting) { published += it }
        }
        runCurrent()
        assertEquals(ChatsConnectionStatus.None, published.last())
        advanceTimeBy(holdMillis - 1)
        assertEquals(ChatsConnectionStatus.None, published.last())
        advanceTimeBy(2)
        assertEquals(ChatsConnectionStatus.Connecting, published.last())
        second.cancel()
    }

    @Test fun pendingStatusOfThePreviousAccountNeverReachesTheNewAccount() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val tracker = ChatsConnectionStatusTracker(holdMillis)
        val previous = launch {
            tracker.follow("account-a", TelegramConnectionState.Connecting) { published += it }
        }
        runCurrent()
        advanceTimeBy(holdMillis / 2)
        previous.cancel()

        // Account B is already connected: it must show the plain title, never A's pending "Connecting…".
        val current = launch {
            tracker.follow("account-b", TelegramConnectionState.Connected) { published += it }
        }
        advanceTimeBy(holdMillis * 2)

        assertFalse(published.contains(ChatsConnectionStatus.Connecting))
        assertEquals(ChatsConnectionStatus.None, published.last())
        current.cancel()
    }

    @Test fun rapidTdlibFlappingNeverReachesTheHeader() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val tracker = ChatsConnectionStatusTracker(holdMillis)
        var job = launch {
            tracker.follow("legacy", TelegramConnectionState.Connecting) { published += it }
        }
        runCurrent()
        advanceTimeBy(holdMillis / 2)
        job.cancel()

        job = launch {
            tracker.follow("legacy", TelegramConnectionState.Updating) { published += it }
        }
        runCurrent()
        advanceTimeBy(holdMillis / 2)
        job.cancel()

        // Cold start and reconnect end in Connected before the hold expires: only the title remains.
        job = launch {
            tracker.follow("legacy", TelegramConnectionState.Connected) { published += it }
        }
        runCurrent()
        advanceTimeBy(holdMillis * 2)

        assertEquals(setOf(ChatsConnectionStatus.None), published.toSet())
        job.cancel()
    }
}
