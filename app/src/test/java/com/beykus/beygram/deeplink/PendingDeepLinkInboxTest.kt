package com.beykus.beygram.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDeepLinkInboxTest {
    private class FakeStorage(initial: PendingTelegramDeepLink? = null) : PendingDeepLinkStorage {
        var stored: PendingTelegramDeepLink? = initial
        override fun read(): PendingTelegramDeepLink? = stored
        override fun write(pending: PendingTelegramDeepLink) { stored = pending }
        override fun clear() { stored = null }
    }

    @Test
    fun coldStartRestoresAValidLinkAndClearsItOnlyForItsAccount() {
        val storage = FakeStorage()
        val beforeLogin = PendingDeepLinkInbox(storage)

        val pending = beforeLogin.offer("https://t.me/durov", 10L)
        assertNotNull(pending)
        assertNull(pending?.accountId)

        // A new process/Activity instance sees the same pending link after login.
        val afterRestart = PendingDeepLinkInbox(storage)
        val restored = afterRestart.restore()
        assertEquals("https://t.me/durov", restored?.raw)
        assertEquals(10L, restored?.token)

        val bound = afterRestart.bind(10L, "legacy")
        assertEquals("legacy", bound?.accountId)
        assertNull(afterRestart.take(10L, "a-second"))
        assertEquals(10L, storage.stored?.token)

        val taken = afterRestart.take(10L, "legacy")
        assertEquals("https://t.me/durov", taken?.raw)
        assertNull(storage.stored)
        assertNull(PendingDeepLinkInbox(storage).restore())
    }

    @Test
    fun warmStartKeepsTheLatestLinkAndLateResultsCannotClearIt() {
        val storage = FakeStorage()
        val inbox = PendingDeepLinkInbox(storage)

        inbox.offer("https://t.me/+InviteHash", 100L)
        inbox.offer("https://t.me/durov/42", 200L)
        assertEquals(200L, inbox.restore()?.token)

        // The old confirmation's result must not remove the newer pending link.
        assertNull(inbox.take(100L, "legacy"))
        assertEquals(200L, storage.stored?.token)
        assertNotNull(inbox.take(200L, "legacy"))
    }

    @Test
    fun malformedAndLoginLinksAreNeverStoredAndDoNotEraseAValidPendingLink() {
        val storage = FakeStorage()
        val inbox = PendingDeepLinkInbox(storage)
        val pending = inbox.offer("https://t.me/+InviteHash", 7L)
        assertEquals(7L, pending?.token)

        val rejected = listOf(
            "https://example.com/durov",
            "https://t.me@evil.com/durov",
            "tg://resolve?domain=bad%20name",
            "tg://proxy?server=proxy.example&port=0&secret=dd",
            "tg://login?token=superSecret",
            "javascript:alert(1)"
        )
        rejected.forEach { raw -> inbox.offer(raw, 8L) }
        assertEquals(7L, storage.stored?.token)
        assertEquals("https://t.me/+InviteHash", storage.stored?.raw)

        val fresh = FakeStorage()
        val freshInbox = PendingDeepLinkInbox(fresh)
        rejected.forEach { raw -> assertNull(freshInbox.offer(raw, 9L)) }
        assertNull(fresh.stored)
    }

    @Test
    fun bindingRejectsUnsafeAccountIdsAndNeverRebindsAnotherAccount() {
        val storage = FakeStorage()
        val inbox = PendingDeepLinkInbox(storage)
        inbox.offer("https://t.me/durov", 1L)

        assertNull(inbox.bind(1L, "../legacy"))
        assertNull(storage.stored?.accountId)

        assertEquals("legacy", inbox.bind(1L, "legacy")?.accountId)
        assertEquals("legacy", inbox.bind(1L, "a-second")?.accountId)
        assertNull(inbox.take(1L, "a-second"))
    }

    @Test
    fun aPendingInviteIsStoredForColdStartAndScopedToTheSelectedAccount() {
        val storage = FakeStorage()
        val inbox = PendingDeepLinkInbox(storage)
        inbox.offer("https://t.me/+JoinHash", 55L)
        inbox.bind(55L, "a-second")

        val restarted = PendingDeepLinkInbox(storage)
        assertEquals("a-second", restarted.restore()?.accountId)
        assertNull(restarted.take(55L, "legacy"))
        assertEquals(55L, storage.stored?.token)
        assertTrue(restarted.take(55L, "a-second") != null)
    }

    @Test
    fun proxyPendingStateCanBeRestoredWithoutLeakingItsSecretInDiagnostics() {
        val secret = "verySecretProxyValue"
        val storage = FakeStorage()
        val inbox = PendingDeepLinkInbox(storage)
        inbox.offer("tg://proxy?server=proxy.example&port=443&secret=$secret", 3L)

        val restored = PendingDeepLinkInbox(storage).restore()
        assertNotNull(restored)
        assertTrue(restored?.raw.orEmpty().contains(secret))
        assertFalse(restored.toString().contains(secret))
    }

    @Test
    fun existingLinkIsPreservedWhenAReplacementIntentIsMalformed() {
        val storage = FakeStorage()
        val inbox = PendingDeepLinkInbox(storage)
        inbox.offer("https://t.me/+KeepMe", 1L)

        val result = inbox.offer("not a telegram link", 2L)
        assertEquals(1L, result?.token)
        assertEquals("https://t.me/+KeepMe", storage.stored?.raw)
    }
}
