package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatJumpCancellationTest {
    @Test
    fun delayedJumpResponseIsRejectedAfterCancelMessageJump() = runBlocking {
        val gate = RequestEpochMap<Long>()
        val chatId = 100L
        val replacedWindows = mutableListOf<Long>()
        val tdlibResponse = CompletableDeferred<Long>()

        val firstToken = gate.next(chatId)
        val firstJump = async {
            val targetId = tdlibResponse.await()
            if (gate.isCurrent(chatId, firstToken)) {
                replacedWindows += targetId
                true
            } else {
                false
            }
        }

        // User jumps to an already-loaded message, closes search, or reopens search results while TDLib is still loading.
        gate.invalidate(chatId)
        tdlibResponse.complete(42L)

        assertFalse(firstJump.await())
        assertTrue(replacedWindows.isEmpty())
    }

    @Test
    fun newerForumTopicJumpSupersedesDelayedEarlierJump() = runBlocking {
        val gate = RequestEpochMap<Pair<Long, Long>>()
        val key = 100L to 7L
        val replacedWindows = mutableListOf<Long>()
        val slowResponse = CompletableDeferred<Long>()
        val fastResponse = CompletableDeferred<Long>()

        val tokenA = gate.next(key)
        val jumpA = async {
            val target = slowResponse.await()
            if (gate.isCurrent(key, tokenA)) {
                replacedWindows += target
                true
            } else {
                false
            }
        }

        val tokenB = gate.next(key)
        val jumpB = async {
            val target = fastResponse.await()
            if (gate.isCurrent(key, tokenB)) {
                replacedWindows += target
                true
            } else {
                false
            }
        }

        fastResponse.complete(200L)
        assertTrue(jumpB.await())

        slowResponse.complete(100L)
        assertFalse(jumpA.await())
        assertEquals(listOf(200L), replacedWindows)
    }
}
