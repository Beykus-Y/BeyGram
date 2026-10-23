package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TdSecondaryStartupReplayTest {
    @Test fun transientChatsUsersAndReadCountersAreNotLostBeforeRepositoryAttachment() {
        val buffer = TdSecondaryStartupReplay()
        val newChat = TdApi.UpdateNewChat()
        val newUser = TdApi.UpdateUser()
        val inbox = TdApi.UpdateChatReadInbox()
        val unread = TdApi.UpdateUnreadChatCount()
        val newMessage = TdApi.UpdateNewMessage()
        listOf(newChat, newUser, inbox, unread, newMessage).forEach {
            buffer.record(it, hasObservers = false)
        }
        val received = mutableListOf<TdApi.Object>()
        buffer.replay { received.add(it) }
        assertEquals(listOf(newChat, newUser, inbox, unread, newMessage), received)
    }

    @Test fun snapshotAndFollowingDeltasReplayInOrder() {
        val buffer = TdSecondaryStartupReplay()
        val snapshot = TdApi.UpdateActiveNotifications()
        val group = TdApi.UpdateNotificationGroup()
        val notification = TdApi.UpdateNotification()
        val pending = TdApi.UpdateHavePendingNotifications()
        buffer.record(snapshot, hasObservers = false)
        buffer.record(group, hasObservers = false)
        buffer.record(notification, hasObservers = false)
        buffer.record(pending, hasObservers = false)

        val received = mutableListOf<TdApi.Object>()
        buffer.replay { received.add(it) }
        assertEquals(4, received.size)
        assertSame(snapshot, received[0])
        assertSame(group, received[1])
        assertSame(notification, received[2])
        assertSame(pending, received[3])

        val repeated = mutableListOf<TdApi.Object>()
        buffer.replay { repeated.add(it) }
        // Stateful snapshot remains available; one-time deltas are not resent.
        assertEquals(listOf(snapshot), repeated)
    }

    @Test fun newerSnapshotSupersedesAllEarlierNotificationDeltas() {
        val buffer = TdSecondaryStartupReplay()
        val old = TdApi.UpdateActiveNotifications()
        val changed = TdApi.UpdateNotificationGroup()
        val latest = TdApi.UpdateActiveNotifications()
        val delta = TdApi.UpdateNotification()
        buffer.record(old, hasObservers = false)
        buffer.record(changed, hasObservers = false)
        buffer.record(latest, hasObservers = false)
        buffer.record(delta, hasObservers = false)

        val received = mutableListOf<TdApi.Object>()
        buffer.replay { received.add(it) }
        assertEquals(listOf(latest, delta), received)
    }

    @Test fun liveObserverAlreadyGetsDeltasAndLogoutClearsBufferedState() {
        val buffer = TdSecondaryStartupReplay()
        buffer.record(TdApi.UpdateActiveNotifications(), hasObservers = false)
        buffer.record(TdApi.UpdateNotificationGroup(), hasObservers = true)
        val beforeClear = mutableListOf<TdApi.Object>()
        buffer.replay { beforeClear.add(it) }
        assertEquals(1, beforeClear.size)
        buffer.clear()
        val afterClear = mutableListOf<TdApi.Object>()
        buffer.replay { afterClear.add(it) }
        assertTrue(afterClear.isEmpty())
    }
}
