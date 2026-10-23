package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TdSecondaryChatStoreTest {
    private fun chat(account: String, id: Long, order: Long) = TdSecondaryChatSummary(
        key = TdAccountChatKey(account, id), title = "Chat $id", order = order
    )
    private fun msg(account: String, chat: Long, id: Long, date: Int) = TdSecondaryTextMessage(
        key = TdAccountMessageKey(account, chat, id), text = "Message $id",
        outgoing = false, date = date
    )

    @Test fun sameNumericChatIdsNeverMixBetweenAccounts() {
        val a = TdSecondaryChatStore("account-a")
        val b = TdSecondaryChatStore("account-b")
        a.upsert(chat("account-a", 42, 500))
        b.upsert(chat("account-b", 42, 600))
        a.replaceHistory(TdAccountChatKey("account-a", 42), listOf(msg("account-a",42,7,10)))
        b.replaceHistory(TdAccountChatKey("account-b", 42), listOf(msg("account-b",42,7,20)))
        assertEquals("account-a", a.chats.value.single().key.accountId)
        assertEquals("account-b", b.chats.value.single().key.accountId)
        assertEquals(10, a.history.value.values.single().single().date)
        assertEquals(20, b.history.value.values.single().single().date)
        a.clear()
        assertTrue(a.chats.value.isEmpty())
        assertTrue(a.history.value.isEmpty())
        assertEquals(1, b.chats.value.size)
        assertEquals(1, b.history.value.values.single().size)
    }

    @Test fun foreignAccountChatAndMessageAreRejectedWithoutPartialWrites() {
        val own = TdSecondaryChatStore("local")
        try { own.upsert(chat("other", 42, 20)); fail("Cross-account chat accepted") }
        catch (_: IllegalArgumentException) { }
        try {
            own.replaceHistory(TdAccountChatKey("local", 42), listOf(msg("other",42,5,30)))
            fail("Cross-account message accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            own.replaceHistory(TdAccountChatKey("other", 42), emptyList())
            fail("Foreign account history accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(own.chats.value.isEmpty())
        assertTrue(own.history.value.isEmpty())
    }

    @Test fun updatesFromAnotherNativeClientAreRejected() {
        val own = TdSecondaryChatStore("account-a")
        try {
            own.receive(TdAccountUpdateRouter.TdAccountRoutedUpdate(
                "account-b", 123, TdApi.Ok()))
            fail("Foreign source account accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(own.chats.value.isEmpty())
    }

    @Test fun mainChatsAreOrderedAndHiddenZeroOrderChatsRemainStored() {
        val own = TdSecondaryChatStore("account-a")
        own.upsert(chat("account-a", 9, 0))
        own.upsert(chat("account-a", 1, 5))
        own.upsert(chat("account-a", 2, 10))
        assertEquals(listOf(2L, 1L), own.chats.value.map { it.key.chatId })
        own.upsert(chat("account-a", 9, 20))
        assertEquals(listOf(9L,2L,1L), own.chats.value.map { it.key.chatId })
        own.upsert(chat("account-a",2,0))
        assertFalse(own.chats.value.any { it.key.chatId == 2L })
    }

    @Test fun secondaryHistoryCacheIsBoundedForLowMemoryDevices() {
        val own = TdSecondaryChatStore("account-a")
        val key = TdAccountChatKey("account-a", 1L)
        own.replaceHistory(key, (1..170).map {
            msg("account-a", 1L, it.toLong(), it)
        })
        val history = own.history.value.getValue(key)
        assertEquals(100, history.size)
        assertEquals(71L, history.first().key.messageId)
        assertEquals(170L, history.last().key.messageId)
    }

    @Test fun historyDeduplicatesPerMessageAndSortsChronologically() {
        val own = TdSecondaryChatStore("account-a")
        val key = TdAccountChatKey("account-a", 77)
        own.replaceHistory(key, listOf(
            msg("account-a",77,3,400),
            msg("account-a",77,2,200),
            msg("account-a",77,2,200),
            msg("account-a",77,1,100)
        ))
        assertEquals(listOf(1L,2L,3L),
            own.history.value.getValue(key).map { it.key.messageId })
    }
}
