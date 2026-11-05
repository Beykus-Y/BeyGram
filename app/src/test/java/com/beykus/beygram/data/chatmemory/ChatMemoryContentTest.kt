package com.beykus.beygram.data.chatmemory

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMemoryContentTest {
    private fun formatted(value: String): TdApi.FormattedText = TdApi.FormattedText().apply { text = value }

    @Test
    fun textMessageKeepsItsText() {
        val content = TdApi.MessageText().apply { text = formatted("hello") }

        assertEquals(MemorySnapshot("hello", hasAttachment = false), memorySnapshotOf(content, isService = false))
    }

    @Test
    fun photoKeepsOnlyItsCaption() {
        val content = TdApi.MessagePhoto().apply { caption = formatted("look") }

        assertEquals(MemorySnapshot("look", hasAttachment = true), memorySnapshotOf(content, isService = false))
    }

    @Test
    fun mediaWithoutCaptionHasNoTextButIsMarked() {
        assertEquals(MemorySnapshot("", hasAttachment = true), memorySnapshotOf(TdApi.MessageVideoNote(), isService = false))
    }

    @Test
    fun animatedEmojiKeepsItsEmojiAsText() {
        val content = TdApi.MessageAnimatedEmoji().apply { emoji = "🙂" }

        assertEquals(MemorySnapshot("🙂", hasAttachment = false), memorySnapshotOf(content, isService = false))
    }

    @Test
    fun serviceEventsAreNotKept() {
        val content = TdApi.MessageText().apply { text = formatted("joined the group") }

        assertNull(memorySnapshotOf(content, isService = true))
    }
}
