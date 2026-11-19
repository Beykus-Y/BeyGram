package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChatTimelineGrouperTest {
    private fun message(
        id: Long,
        albumId: Long = 0L,
        hasMedia: Boolean = albumId != 0L,
        dateSeconds: Long = id,
        isOutgoing: Boolean = false,
        senderId: Long = 0L,
        senderName: String = "",
        text: String = "",
    ) = ChatMessage(
        id = id, chatId = 1L, text = text, time = "", isOutgoing = isOutgoing, dateSeconds = dateSeconds,
        mediaAlbumId = albumId, senderId = senderId, senderName = senderName,
        media = if (hasMedia) MessageMedia(MediaKind.Photo, fileId = id.toInt()) else null
    )

    private fun randomMessage(id: Long, random: Random) = message(
        id = id,
        albumId = listOf(0L, 0L, 3L, 4L)[random.nextInt(4)],
        hasMedia = random.nextInt(4) != 0,
        dateSeconds = random.nextLong(1L, 6L),
        isOutgoing = random.nextBoolean(),
        senderId = random.nextLong(0L, 3L),
        senderName = if (random.nextInt(4) == 0) "" else "sender${random.nextInt(3)}",
    )

    /** One random edit: an insert, a delete or a change to some messages. [newId] supplies ids for inserts. */
    private fun randomEdit(history: List<ChatMessage>, random: Random, newId: () -> Long): List<ChatMessage> {
        val edited = history.toMutableList()
        when (random.nextInt(10)) {
            0 -> edited.add(random.nextInt(edited.size + 1), randomMessage(newId(), random))
            1 -> if (edited.isNotEmpty()) edited.removeAt(random.nextInt(edited.size))
            // Content only: several messages may change at once, album members included.
            2 -> for (index in edited.indices) {
                if (random.nextInt(3) == 0) edited[index] = edited[index].copy(text = "edit ${random.nextInt()}")
            }
            3 -> if (edited.isNotEmpty()) {
                val index = random.nextInt(edited.size)
                edited[index] = edited[index].copy(mediaAlbumId = listOf(0L, 3L, 4L)[random.nextInt(3)])
            }
            4 -> if (edited.isNotEmpty()) {
                val index = random.nextInt(edited.size)
                edited[index] = edited[index].copy(dateSeconds = random.nextLong(1L, 6L))
                edited.sortWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.id })
            }
            5 -> if (edited.isNotEmpty()) {
                val index = random.nextInt(edited.size)
                val current = edited[index]
                edited[index] = current.copy(media = if (current.media == null) MessageMedia(MediaKind.Video, fileId = current.id.toInt()) else null)
            }
            6 -> if (edited.isNotEmpty()) {
                val index = random.nextInt(edited.size)
                edited[index] = edited[index].copy(senderId = random.nextLong(0L, 3L), senderName = "sender${random.nextInt(3)}")
            }
            7 -> if (edited.isNotEmpty()) {
                val index = random.nextInt(edited.size)
                val current = edited[index]
                edited[index] = current.copy(serviceText = if (current.serviceText == null) "joined" else null)
            }
            8 -> if (edited.isNotEmpty()) {
                val index = random.nextInt(edited.size)
                edited[index] = edited[index].copy(isOutgoing = !edited[index].isOutgoing)
            }
            // Otherwise the same messages come back in a new list.
        }
        return edited
    }

    private fun sameMembers(old: ChatMessageListItem, row: ChatMessageListItem) =
        old.messages.size == row.messages.size && old.messages.indices.all { old.messages[it] === row.messages[it] }

    @Test fun randomEditsMatchTheReferenceAtEveryStep() {
        val random = Random(11L)
        val grouper = ChatTimelineGrouper()
        var nextId = 1L
        var history: List<ChatMessage> = List(8) { randomMessage(nextId++, random) }
        var rows = grouper.group(history)
        assertEquals(groupTelegramMediaAlbums(history), rows.items)
        var contentSteps = 0
        var structureSteps = 0
        repeat(500) { step ->
            val previous = history
            val previousRows = rows
            history = randomEdit(previous, random) { nextId++ }
            rows = grouper.group(history)
            assertEquals("step $step", groupTelegramMediaAlbums(history), rows.items)

            val samePlaces = previous.size == history.size && previous.indices.all { sameTimelinePlace(previous[it], history[it]) }
            if (samePlaces) {
                contentSteps++
                assertEquals("step $step", previousRows.structureVersion, rows.structureVersion)
            } else {
                structureSteps++
                assertNotEquals("step $step", previousRows.structureVersion, rows.structureVersion)
            }
            // A row whose members are the same objects as before must still be the same row instance.
            val previousByKey = previousRows.items.associateBy { it.key }
            for (row in rows.items) {
                val old = previousByKey[row.key] ?: continue
                if (sameMembers(old, row)) assertSame("step $step", old, row)
            }
        }
        assertTrue(contentSteps > 0)
        assertTrue(structureSteps > 0)
    }

    @Test fun sameListInstanceReturnsTheSameRows() {
        val grouper = ChatTimelineGrouper()
        val history = listOf(message(1L), message(2L, albumId = 7L), message(3L, albumId = 7L))
        val rows = grouper.group(history)
        assertSame(rows, grouper.group(history))
        // A new list holding the same instances is not a change either.
        assertSame(rows, grouper.group(history.toList()))
    }

    @Test fun contentOnlyChangeKeepsTheVersionAndUntouchedRows() {
        val grouper = ChatTimelineGrouper()
        val edited = message(2L, text = "old")
        val history = listOf(message(1L), edited, message(3L, albumId = 7L), message(4L, albumId = 7L), message(5L))
        val before = grouper.group(history)
        val changed = edited.copy(text = "new")
        val changedHistory = history.map { if (it === edited) changed else it }
        val after = grouper.group(changedHistory)

        assertEquals(before.structureVersion, after.structureVersion)
        assertEquals(groupTelegramMediaAlbums(changedHistory), after.items)
        val beforeByKey = before.items.associateBy { it.key }
        val row = after.items.single { it.key == "message:2" }
        assertNotSame(beforeByKey.getValue("message:2"), row)
        assertSame(changed, row.messages.single())
        for (key in listOf("message:1", "album:7", "message:5")) {
            assertSame(beforeByKey.getValue(key), after.items.single { it.key == key })
        }
    }

    @Test fun insertBumpsTheVersionAndUntouchedRowsStayTheSameInstances() {
        val grouper = ChatTimelineGrouper()
        val history = listOf(message(1L), message(2L, albumId = 7L), message(3L, albumId = 7L), message(4L))
        val before = grouper.group(history)
        val grown = history + message(5L)
        val after = grouper.group(grown)

        assertNotEquals(before.structureVersion, after.structureVersion)
        assertEquals(groupTelegramMediaAlbums(grown), after.items)
        val beforeByKey = before.items.associateBy { it.key }
        for (key in listOf("message:1", "album:7", "message:4")) {
            assertSame(beforeByKey.getValue(key), after.items.single { it.key == key })
        }
    }

    @Test fun structureVersionsDoNotRepeatAcrossGroupers() {
        val history = listOf(message(1L), message(2L))
        val first = ChatTimelineGrouper().group(history).structureVersion
        val second = ChatTimelineGrouper().group(history).structureVersion
        assertNotEquals(first, second)
    }

    @Test fun albumMemberContentChangeReplacesOnlyThatAlbumRow() {
        val grouper = ChatTimelineGrouper()
        val first = message(2L, albumId = 7L)
        val second = message(3L, albumId = 7L, text = "caption")
        val history = listOf(message(1L), first, second, message(4L))
        val before = grouper.group(history)
        val editedSecond = second.copy(text = "new caption")
        val changedHistory = history.map { if (it === second) editedSecond else it }
        val after = grouper.group(changedHistory)

        assertEquals(before.structureVersion, after.structureVersion)
        assertEquals(groupTelegramMediaAlbums(changedHistory), after.items)
        val beforeByKey = before.items.associateBy { it.key }
        val album = after.items.single { it.key == "album:7" }
        assertNotSame(beforeByKey.getValue("album:7"), album)
        assertSame(first, album.messages[0])
        assertSame(editedSecond, album.messages[1])
        for (key in listOf("message:1", "message:4")) {
            assertSame(beforeByKey.getValue(key), after.items.single { it.key == key })
        }
    }

    @Test fun severalChangedAlbumMembersAllLandInTheirRow() {
        val grouper = ChatTimelineGrouper()
        val first = message(2L, albumId = 7L)
        val second = message(3L, albumId = 7L)
        val history = listOf(first, second, message(4L))
        grouper.group(history)
        val editedFirst = first.copy(text = "a")
        val editedSecond = second.copy(text = "b")
        val changedHistory = history.map { if (it === first) editedFirst else if (it === second) editedSecond else it }
        val after = grouper.group(changedHistory)

        assertEquals(groupTelegramMediaAlbums(changedHistory), after.items)
        assertEquals(listOf(editedFirst, editedSecond), after.items.single { it.key == "album:7" }.messages)
    }

    @Test fun repeatedMessageIdsAreAlwaysRegrouped() {
        val grouper = ChatTimelineGrouper()
        grouper.group(listOf(message(1L, text = "a"), message(1L, text = "b")))
        val next = listOf(message(1L, text = "a"), message(1L, text = "c"))
        assertEquals(groupTelegramMediaAlbums(next), grouper.group(next).items)
    }

    @Test fun onlyPlacementFieldsCountAsStructure() {
        val base = message(1L, albumId = 7L, senderId = 2L, senderName = "A")
        assertTrue(sameTimelinePlace(base, base.copy(text = "edited", isEdited = true)))
        assertFalse(sameTimelinePlace(base, base.copy(dateSeconds = 9L)))
        assertFalse(sameTimelinePlace(base, base.copy(senderName = "B")))
        assertFalse(sameTimelinePlace(base, base.copy(mediaAlbumId = 0L)))
        assertFalse(sameTimelinePlace(base, base.copy(serviceText = "joined")))
    }
}
