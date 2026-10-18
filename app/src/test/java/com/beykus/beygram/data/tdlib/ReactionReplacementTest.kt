package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionReplacementTest {
    private class Calls {
        val removed = mutableListOf<String>()
        val restored = mutableListOf<String>()
        var added = false
    }

    @Test fun removesEveryDisplacedReactionThenAdds() = runBlocking {
        val calls = Calls()
        replaceReactions(listOf("🔥", "👍"), add = { calls.added = true }, remove = { calls.removed.add(it) }, restore = { calls.restored.add(it) })
        assertEquals(listOf("🔥", "👍"), calls.removed)
        assertTrue(calls.added)
        assertEquals(emptyList<String>(), calls.restored)
    }

    @Test fun withoutDisplacedReactionsOnlyAdds() = runBlocking {
        val calls = Calls()
        replaceReactions(emptyList<String>(), add = { calls.added = true }, remove = { calls.removed.add(it) }, restore = { calls.restored.add(it) })
        assertTrue(calls.added)
        assertEquals(emptyList<String>(), calls.removed)
    }

    @Test fun failedRemovalRestoresOnlyTheReactionsAlreadyRemoved() = runBlocking {
        val calls = Calls()
        val failure = runCatching {
            replaceReactions(
                listOf("🔥", "👍", "❤"),
                add = { calls.added = true },
                remove = { if (it == "👍") error("limit"); calls.removed.add(it) },
                restore = { calls.restored.add(it) }
            )
        }.exceptionOrNull()
        assertEquals("limit", failure?.message)
        assertEquals(listOf("🔥"), calls.removed)
        assertEquals(listOf("🔥"), calls.restored)
        assertFalse(calls.added)
    }

    @Test fun failedAddRestoresAllRemovedInReverseOrder() = runBlocking {
        val calls = Calls()
        val failure = runCatching {
            replaceReactions(listOf("🔥", "👍"), add = { error("limit") }, remove = { calls.removed.add(it) }, restore = { calls.restored.add(it) })
        }.exceptionOrNull()
        assertEquals("limit", failure?.message)
        assertEquals(listOf("👍", "🔥"), calls.restored)
    }

    @Test fun cancelledAddStillRestoresReactionsAlreadyRemoved() = runBlocking {
        val calls = Calls()
        val addStarted = CompletableDeferred<Unit>()
        val job = launch {
            replaceReactions(
                listOf("🔥", "👍"),
                add = { addStarted.complete(Unit); awaitCancellation() },
                remove = { calls.removed.add(it) },
                restore = { calls.restored.add(it) }
            )
        }
        addStarted.await()
        job.cancelAndJoin()
        assertEquals(listOf("👍", "🔥"), calls.restored)
    }

    @Test fun failedRestoreIsKeptAsSuppressedOfTheOriginalFailure() = runBlocking {
        val failure = runCatching {
            replaceReactions(listOf("🔥"), add = { error("limit") }, remove = {}, restore = { error("network") })
        }.exceptionOrNull()
        assertEquals("limit", failure?.message)
        assertEquals("network", failure?.suppressed?.single()?.message)
    }
}
