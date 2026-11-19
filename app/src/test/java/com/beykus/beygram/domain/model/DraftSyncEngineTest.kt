package com.beykus.beygram.domain.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftSyncEngineTest {
    private var clock = 1_720_000_000_000L

    /** Keeps drafts as codec strings, so every test also goes through [MessageDraftCodec]. */
    private class CodecStore<K> : DraftStore<K> {
        val raw = LinkedHashMap<K, String>()
        override fun read(key: K): StoredMessageDraft? = raw[key]?.let(MessageDraftCodec::decode)
        override fun write(key: K, value: StoredMessageDraft) {
            raw[key] = MessageDraftCodec.encode(value)
        }
        override fun unsyncedKeys(): List<K> = raw.keys.filter { read(it)?.synced == false }
        override fun clearAll() = raw.clear()
    }

    private class FakePush<K> {
        val pushed = mutableListOf<Pair<K, MessageDraft>>()
        var succeed = true
        /** When set, the next push suspends until this deferred completes. */
        var suspendNext: CompletableDeferred<Boolean>? = null

        suspend fun push(key: K, draft: MessageDraft): Boolean {
            pushed.add(key to draft)
            val gate = suspendNext
            suspendNext = null
            return gate?.await() ?: succeed
        }

        fun texts(key: K): List<String> = pushed.filter { it.first == key }.map { it.second.text }
    }

    private fun <K> TestScope.engine(
        store: DraftStore<K>,
        push: FakePush<K>,
        fetch: suspend (K) -> RemoteDraft? = { null }
    ): DraftSyncEngine<K> = DraftSyncEngine<K>(
        store = store,
        scope = backgroundScope,
        now = { clock },
        push = { key, draft -> push.push(key, draft) },
        fetch = fetch,
        writeDelayMillis = 400L
    )

    private fun TestScope.settle(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test fun fastTypingPushesOnlyTheLastDraft() = runTest {
        val store = CodecStore<Long>()
        val push = FakePush<Long>()
        val engine = engine(store, push)
        for (i in 1..5) {
            engine.update(1L) { MessageDraft("черновик $i") }
            settle(100)
        }
        settle(400)
        assertEquals(listOf("черновик 5"), push.texts(1L))
        assertTrue(store.read(1L)!!.synced)
    }

    @Test fun failedPushStaysUnsyncedAndRetriesOnReconnect() = runTest {
        val store = CodecStore<Long>()
        val push = FakePush<Long>().apply { succeed = false }
        val engine = engine(store, push)
        engine.update(1L) { MessageDraft("офлайн") }
        settle(400)
        assertEquals(listOf("офлайн"), push.texts(1L))
        assertFalse(store.read(1L)!!.synced)

        push.succeed = true
        engine.connectionReady()
        advanceUntilIdle()
        assertEquals(listOf("офлайн", "офлайн"), push.texts(1L))
        assertTrue(store.read(1L)!!.synced)
    }

    @Test fun unsyncedDraftSurvivesRestartAndIsPushedOverAnEmptyCloud() = runTest {
        val store = CodecStore<Long>()
        store.write(1L, StoredMessageDraft(MessageDraft("до перезапуска"), 1_000L))
        val push = FakePush<Long>()
        val engine = engine(store, push, fetch = { RemoteDraft(MessageDraft(), 0L) })
        engine.connectionReady()
        advanceUntilIdle()
        assertEquals(listOf("до перезапуска"), push.texts(1L))
        assertTrue(store.read(1L)!!.synced)
        assertEquals("до перезапуска", engine.observe(1L).value.text)
    }

    @Test fun unsyncedDraftYieldsToANewerCloudDraftAfterRestart() = runTest {
        val store = CodecStore<Long>()
        store.write(1L, StoredMessageDraft(MessageDraft("локальный"), 1_000L))
        val push = FakePush<Long>()
        val remote = RemoteDraft(MessageDraft("с другого устройства"), 5_000L)
        val engine = engine(store, push, fetch = { remote })
        engine.connectionReady()
        advanceUntilIdle()
        assertEquals("с другого устройства", engine.observe(1L).value.text)
        assertEquals(StoredMessageDraft(MessageDraft("с другого устройства"), 5_000L, synced = true), store.read(1L))
        assertTrue(push.pushed.isEmpty())
    }

    @Test fun aDraftClearedOnAnotherDeviceIsNotResurrected() = runTest {
        val store = CodecStore<Long>()
        store.write(1L, StoredMessageDraft(MessageDraft("старый"), 1_000L, synced = true))
        val push = FakePush<Long>()
        val engine = engine(store, push)
        engine.receive(1L, RemoteDraft(MessageDraft(), 0L))
        engine.connectionReady()
        advanceUntilIdle()
        assertEquals(MessageDraft(), engine.observe(1L).value)
        assertTrue(push.pushed.isEmpty())
        assertTrue(store.read(1L)!!.synced)
    }

    @Test fun aStaleCloudDraftDoesNotReplaceTextTypedInAnOpenChat() = runTest {
        val store = CodecStore<Long>()
        val push = FakePush<Long>()
        val engine = engine(store, push)
        engine.opened(1L)
        engine.update(1L) { MessageDraft("новый") }
        advanceUntilIdle()
        assertTrue(store.read(1L)!!.synced)

        engine.receive(1L, RemoteDraft(MessageDraft("старый"), 0L))
        assertEquals("новый", engine.observe(1L).value.text)
    }

    @Test fun aStaleCloudDraftDoesNotReplaceTextTypedInAnOpenTopic() = runTest {
        val store = CodecStore<Pair<Long, Long>>()
        val push = FakePush<Pair<Long, Long>>()
        val engine = engine(store, push)
        val topic = -100123L to 5L
        engine.opened(topic)
        engine.update(topic) { MessageDraft("новый") }
        advanceUntilIdle()
        assertTrue(store.read(topic)!!.synced)

        engine.receive(topic, RemoteDraft(MessageDraft("старый"), 0L))
        assertEquals("новый", engine.observe(topic).value.text)
    }

    @Test fun aConfirmationForAnEarlierDraftKeepsANewerEditUnsynced() = runTest {
        val store = CodecStore<Long>()
        val push = FakePush<Long>()
        val engine = engine(store, push)
        val gate = CompletableDeferred<Boolean>()
        push.suspendNext = gate

        engine.update(1L) { MessageDraft("первый") }
        settle(400)
        assertEquals(listOf("первый"), push.texts(1L))

        // Typing while the first request is in flight must not be marked synced by its success.
        engine.update(1L) { MessageDraft("первый и второй") }
        gate.complete(true)
        runCurrent()
        assertFalse(store.read(1L)!!.synced)

        advanceUntilIdle()
        assertEquals(listOf("первый", "первый и второй"), push.texts(1L))
        assertTrue(store.read(1L)!!.synced)
    }

    @Test fun sendClearsOnlyTheDraftThatWasSent() = runTest {
        val store = CodecStore<Long>()
        val engine = engine(store, FakePush<Long>())
        val sent = MessageDraft("отправлено")
        engine.update(1L) { sent }
        engine.update(1L) { MessageDraft("отправлено, и дальше") }
        engine.clearIfUnchanged(1L, sent)
        assertEquals("отправлено, и дальше", engine.observe(1L).value.text)

        engine.update(1L) { MessageDraft("тест") }
        engine.clearIfUnchanged(1L, MessageDraft("тест"))
        assertEquals(MessageDraft(), engine.observe(1L).value)
    }

    @Test fun anUnsupportedCloudDraftIsNeverOverwrittenOrPushedOver() = runTest {
        val store = CodecStore<Long>()
        store.write(1L, StoredMessageDraft(MessageDraft("локальный"), 1_000L))
        val push = FakePush<Long>()
        val engine = engine(store, push, fetch = { RemoteDraft(null, 9_999_999L) })
        engine.receive(1L, RemoteDraft(null, 9_999_999L))
        engine.connectionReady()
        advanceUntilIdle()
        assertEquals("локальный", engine.observe(1L).value.text)
        assertEquals(StoredMessageDraft(MessageDraft("локальный"), 1_000L), store.read(1L))
        assertTrue(push.pushed.isEmpty())
    }

    @Test fun signOutDropsAnInFlightPushAndEveryLocalDraft() = runTest {
        val store = CodecStore<Long>()
        val push = FakePush<Long>()
        // Never completes: the push is still in flight when the session ends.
        push.suspendNext = CompletableDeferred()
        val engine = engine(store, push)
        engine.update(1L) { MessageDraft("секрет") }
        settle(400)
        assertEquals(listOf("секрет"), push.texts(1L))

        engine.signedOut()
        advanceUntilIdle()
        assertEquals(listOf("секрет"), push.texts(1L))
        assertEquals(MessageDraft(), engine.observe(1L).value)
        assertTrue(store.raw.isEmpty())
    }

    @Test fun unicodeEntitiesAndReplySurviveTheStore() = runTest {
        val draft = MessageDraft("Привет 👋🏽 мир", listOf(MessageTextEntity(7, 4, TextStyleKind.Bold)), 42L)
        val store = CodecStore<Long>()
        engine(store, FakePush<Long>()).update(1L) { draft }

        val restarted = engine(store, FakePush<Long>())
        assertEquals(draft, restarted.observe(1L).value)

        val stored = StoredMessageDraft(draft, clock, synced = false)
        assertEquals(stored, MessageDraftCodec.decode(MessageDraftCodec.encode(stored)))
    }
}
