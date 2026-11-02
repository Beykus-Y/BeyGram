package com.beykus.beygram.media.bgmix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMixStatusTest {

    @Test
    fun parsesQueueDecksAndTransitionState() {
        val json = """
        {"paused":false,"master":1,"transitionActive":true,"transitionAvailable":true,
         "transitionType":"xfade","transitionProgress":0.25,"preparingNext":false,
         "preparingName":"","preparingProgress":0.0,"lastError":"","status":"Mixing",
         "decks":[{"loaded":true,"playing":false,"ended":false,"positionSeconds":12.5,"durationSeconds":200.0,"bpm":123.0},
                  {"loaded":true,"playing":true,"ended":false,"positionSeconds":0.5,"durationSeconds":180.0,"bpm":120.0}],
         "queue":[{"id":7,"name":"Трек ✨ один","state":"played","progress":1.0,"bpm":123.0,"durationSeconds":200.0,"error":""}]}
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val status = BgMixStatus.parse(json)!!

        assertEquals(1, status.master)
        assertTrue(status.transitionActive)
        assertEquals(2, status.decks.size)
        assertEquals(12.5, status.decks[0].positionSeconds, 1e-9)
        assertTrue(status.decks[1].playing)
        assertEquals(1, status.queue.size)
        assertEquals("Трек ✨ один", status.queue[0].name)
        assertEquals("played", status.queue[0].state)
        assertEquals(7L, status.queue[0].id)
    }

    @Test
    fun malformedOrEmptyInputReturnsNull() {
        assertNull(BgMixStatus.parse(null))
        assertNull(BgMixStatus.parse(ByteArray(0)))
        assertNull(BgMixStatus.parse("{not json".toByteArray()))
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val status = BgMixStatus.parse("{}".toByteArray())!!
        assertEquals(false, status.paused)
        assertEquals(0, status.queue.size)
        assertEquals(0, status.decks.size)
    }
}
