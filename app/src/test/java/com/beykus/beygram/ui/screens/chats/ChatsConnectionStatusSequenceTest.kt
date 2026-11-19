package com.beykus.beygram.ui.screens.chats

import com.beykus.beygram.R
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Повторяет поведение LaunchedEffect(tracker, accountId, connection) в Compose: каждое новое
 * состояние отменяет предыдущую корутину и запускает новую на том же трекере.
 */
private class ScreenHarness(
    private val scope: CoroutineScope,
    private val tracker: ChatsConnectionStatusTracker,
    private val published: MutableList<ChatsConnectionStatus>
) {
    private var job: Job? = null

    fun show(accountId: String, connection: TelegramConnectionState) {
        job?.cancel()
        job = scope.launch { tracker.follow(accountId, connection) { published += it } }
    }

    /** Уход с экрана: композиция выходит, отложенная корутина отменяется. */
    fun leave() {
        job?.cancel()
        job = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsConnectionStatusSequenceTest {
    private val holdMillis = 1_000L

    @Test fun connectingUpdatingConnectedRestartsNeverFlicker() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val screen = ScreenHarness(this, ChatsConnectionStatusTracker(holdMillis), published)

        screen.show("legacy", TelegramConnectionState.Connecting)
        advanceTimeBy(300)
        screen.show("legacy", TelegramConnectionState.Updating)
        advanceTimeBy(300)
        screen.show("legacy", TelegramConnectionState.Connected)
        runCurrent()
        advanceTimeBy(holdMillis * 2)

        assertEquals(listOf(ChatsConnectionStatus.None), published)
        screen.leave()
    }

    @Test fun longWaitingForNetworkShowsAfterHoldAndClearsAtOnceOnRecovery() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val screen = ScreenHarness(this, ChatsConnectionStatusTracker(holdMillis), published)

        screen.show("legacy", TelegramConnectionState.WaitingForNetwork)
        advanceTimeBy(holdMillis - 1)
        assertEquals(listOf(ChatsConnectionStatus.None), published)
        advanceTimeBy(2)
        assertEquals(
            listOf(ChatsConnectionStatus.None, ChatsConnectionStatus.WaitingForNetwork),
            published
        )

        // Recovery is a restart with Connected: the banner must disappear without waiting for a hold.
        screen.show("legacy", TelegramConnectionState.Connected)
        runCurrent()
        assertEquals(ChatsConnectionStatus.None, published.last())
        assertEquals(3, published.size)
        advanceTimeBy(holdMillis * 2)
        assertEquals(ChatsConnectionStatus.None, published.last())
        assertEquals(3, published.size)
        screen.leave()
    }

    @Test fun frequentDropsBelowTheHoldNeverPublishAStatus() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val screen = ScreenHarness(this, ChatsConnectionStatusTracker(holdMillis), published)

        repeat(6) {
            screen.show("legacy", TelegramConnectionState.WaitingForNetwork)
            advanceTimeBy(500)
            screen.show("legacy", TelegramConnectionState.Connected)
            advanceTimeBy(500)
        }
        advanceTimeBy(holdMillis * 2)

        assertFalse(published.any { it != ChatsConnectionStatus.None })
        screen.leave()
    }

    @Test fun pendingStatusOfALeftScreenNeverSurfacesInTheNextOne() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val leaving = ScreenHarness(this, ChatsConnectionStatusTracker(holdMillis), published)
        leaving.show("legacy", TelegramConnectionState.Connecting)
        advanceTimeBy(holdMillis / 2)
        leaving.leave()
        advanceTimeBy(holdMillis * 3)
        // Only the immediate plain title from the first follow so far: the held Connecting never landed.
        assertEquals(listOf(ChatsConnectionStatus.None), published)

        // Re-entering the chat creates a fresh tracker; the old pending line must not appear in it.
        val entering = ScreenHarness(this, ChatsConnectionStatusTracker(holdMillis), published)
        entering.show("legacy", TelegramConnectionState.Connected)
        advanceTimeBy(holdMillis * 2)

        assertFalse(published.contains(ChatsConnectionStatus.Connecting))
        assertEquals(ChatsConnectionStatus.None, published.last())
        entering.leave()
    }

    @Test fun switchingAccountsAndBackClearsTheLineEachTime() = runTest {
        val published = mutableListOf<ChatsConnectionStatus>()
        val screen = ScreenHarness(this, ChatsConnectionStatusTracker(holdMillis), published)

        screen.show("account-a", TelegramConnectionState.WaitingForNetwork)
        advanceTimeBy(holdMillis + 1)
        assertEquals(ChatsConnectionStatus.WaitingForNetwork, published.last())

        // Account B is also offline, but its line must start from the plain title and then wait its own hold.
        screen.show("account-b", TelegramConnectionState.WaitingForNetwork)
        runCurrent()
        assertEquals(ChatsConnectionStatus.None, published.last())
        advanceTimeBy(holdMillis - 1)
        assertEquals(ChatsConnectionStatus.None, published.last())
        advanceTimeBy(2)
        assertEquals(ChatsConnectionStatus.WaitingForNetwork, published.last())

        // Back to account A, already connected: no stale line from B.
        screen.show("account-a", TelegramConnectionState.Connected)
        runCurrent()
        assertEquals(ChatsConnectionStatus.None, published.last())
        advanceTimeBy(holdMillis * 2)
        assertEquals(ChatsConnectionStatus.None, published.last())
        screen.leave()
    }

    @Test fun everyNonNoneStatusHasItsChatsConnectionLabel() {
        assertEquals(
            R.string.chats_connection_waiting_network,
            ChatsConnectionStatus.WaitingForNetwork.labelResource()
        )
        assertEquals(R.string.chats_connection_connecting, ChatsConnectionStatus.Connecting.labelResource())
        assertEquals(R.string.chats_connection_updating, ChatsConnectionStatus.Updating.labelResource())
        // The plain title has no label, so asking for one is a programming error.
        assertThrows(IllegalStateException::class.java) { ChatsConnectionStatus.None.labelResource() }
    }
}
