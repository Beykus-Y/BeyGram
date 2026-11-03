package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class MessageAlbumGroupingTest {
    private fun message(id: Long, albumId: Long = 0L, hasMedia: Boolean = albumId != 0L) = ChatMessage(
        id = id, chatId = 1L, text = "", time = "", isOutgoing = false, dateSeconds = id,
        mediaAlbumId = albumId, media = if (hasMedia) MessageMedia(MediaKind.Photo, fileId = id.toInt()) else null
    )

    /** The plain definition: an album is one row at its newest member and holds its members in chronological order. */
    private fun reference(messages: List<ChatMessage>): List<ChatMessageListItem> {
        val items = ArrayList<ChatMessageListItem>()
        val emitted = HashSet<Long>()
        for (message in messages.asReversed()) {
            if (message.mediaAlbumId != 0L && message.media != null) {
                if (emitted.add(message.mediaAlbumId)) {
                    val members = messages.filter { it.mediaAlbumId == message.mediaAlbumId && it.media != null }
                    items.add(ChatMessageListItem("album:${message.mediaAlbumId}", members, message.mediaAlbumId))
                }
            } else {
                items.add(ChatMessageListItem("message:${message.id}", listOf(message)))
            }
        }
        return items
    }

    @Test fun albumIsOneRowAtItsNewestMemberAndKeepsItsMembersInOrder() {
        val messages = listOf(message(1L), message(2L, albumId = 7L), message(3L, albumId = 7L), message(4L), message(5L, albumId = 7L))
        val rows = groupTelegramMediaAlbums(messages)
        assertEquals(listOf("album:7", "message:4", "message:1"), rows.map { it.key })
        assertEquals(listOf(2L, 3L, 5L), rows[0].messages.map { it.id })
    }

    @Test fun albumIdWithoutMediaStaysASingleRow() {
        val rows = groupTelegramMediaAlbums(listOf(message(1L, albumId = 7L, hasMedia = false), message(2L, albumId = 7L)))
        assertEquals(listOf("album:7", "message:1"), rows.map { it.key })
        assertEquals(listOf(2L), rows[0].messages.map { it.id })
    }

    @Test fun groupingMatchesTheReferenceOnRandomHistories() {
        val random = Random(5L)
        repeat(1_000) { step ->
            val messages = List(random.nextInt(0, 30)) { index ->
                message(index + 1L, albumId = listOf(0L, 0L, 3L, 4L)[random.nextInt(4)], hasMedia = random.nextInt(4) != 0)
            }
            assertEquals("step $step", reference(messages), groupTelegramMediaAlbums(messages))
        }
    }
}
