package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.FolderChatPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChatListProjectionTest {
    private fun chat(
        id: Long,
        order: Long = 0L,
        pinned: Boolean = false,
        archiveOrder: Long = 0L,
        folders: Map<Int, FolderChatPosition> = emptyMap(),
        unread: Int = 0
    ) = ChatPreview(
        id = id, title = "chat $id", initials = "C", lastMessage = UiText.Raw(""), lastMessageTime = "",
        unreadCount = unread, avatarColorArgb = 0L, isPinned = pinned, order = order,
        archiveOrder = archiveOrder, folderPositions = folders
    )

    private fun project(
        projection: ChatListProjection,
        cache: Map<Long, ChatPreview>,
        folderIds: List<Int> = emptyList(),
        muted: Set<Long> = emptySet()
    ) = projection.update(cache, folderIds) { it in muted }

    @Test
    fun `main list puts pinned chats first and then follows Telegram position`() {
        val cache = mapOf(
            1L to chat(1L, order = 10L),
            2L to chat(2L, order = 30L, pinned = true),
            3L to chat(3L, order = 20L),
            4L to chat(4L, order = 0L)
        )
        assertEquals(listOf(2L, 3L, 1L), project(ChatListProjection(), cache).main.map { it.id })
    }

    @Test
    fun `equal positions are ordered by newest id first`() {
        val cache = mapOf(5L to chat(5L, order = 10L), 9L to chat(9L, order = 10L))
        assertEquals(listOf(9L, 5L), project(ChatListProjection(), cache).main.map { it.id })
    }

    @Test
    fun `archive is ordered by archive position alone`() {
        val cache = mapOf(
            1L to chat(1L, order = 99L, archiveOrder = 5L),
            2L to chat(2L, archiveOrder = 9L),
            3L to chat(3L, order = 7L)
        )
        val lists = project(ChatListProjection(), cache)
        assertEquals(listOf(2L, 1L), lists.archive.map { it.id })
        assertEquals(listOf(1L, 3L), lists.main.map { it.id })
    }

    @Test
    fun `a chat appears once in each list and hidden chats stay out`() {
        val cache = mapOf(
            1L to chat(1L, order = 10L, archiveOrder = 3L, folders = mapOf(7 to FolderChatPosition(4L, false))),
            2L to chat(2L, order = 0L, folders = mapOf(7 to FolderChatPosition(9L, true), 8 to FolderChatPosition(0L, true))),
            3L to chat(3L, order = 0L)
        )
        val lists = project(ChatListProjection(), cache, folderIds = listOf(7, 8))
        assertEquals(listOf(1L), lists.main.map { it.id })
        assertEquals(listOf(1L), lists.archive.map { it.id })
        assertEquals(listOf(2L, 1L), lists.folders.getValue(7).map { it.id })
        assertEquals(emptyList<Long>(), lists.folders.getValue(8).map { it.id })
        assertEquals(lists.main.map { it.id }.distinct(), lists.main.map { it.id })
    }

    @Test
    fun `a list no change reaches keeps its instance`() {
        val cache = mutableMapOf(
            1L to chat(1L, order = 10L),
            2L to chat(2L, order = 20L, folders = mapOf(7 to FolderChatPosition(50L, false))),
            3L to chat(3L, archiveOrder = 5L),
            4L to chat(4L, order = 30L, folders = mapOf(8 to FolderChatPosition(60L, false)))
        )
        val projection = ChatListProjection()
        val first = project(projection, cache, folderIds = listOf(7, 8))

        cache[2L] = cache.getValue(2L).copy(unreadCount = 4)
        val second = project(projection, cache, folderIds = listOf(7, 8))

        assertNotSame(first.main, second.main)
        assertEquals(listOf(4L, 2L, 1L), second.main.map { it.id })
        assertEquals(4, second.main.first { it.id == 2L }.unreadCount)
        assertSame(first.archive, second.archive)
        assertNotSame(first.folders.getValue(7), second.folders.getValue(7))
        assertSame(first.folders.getValue(8), second.folders.getValue(8))
    }

    @Test
    fun `an archive change leaves the Main list and folders in place`() {
        val cache = mutableMapOf(
            1L to chat(1L, order = 10L),
            2L to chat(2L, order = 20L, folders = mapOf(7 to FolderChatPosition(5L, false))),
            3L to chat(3L, archiveOrder = 5L)
        )
        val projection = ChatListProjection()
        val first = project(projection, cache, folderIds = listOf(7))

        cache[3L] = cache.getValue(3L).copy(archiveOrder = 9L)
        val second = project(projection, cache, folderIds = listOf(7))

        assertSame(first.main, second.main)
        assertSame(first.folders.getValue(7), second.folders.getValue(7))
        assertEquals(listOf(3L), second.archive.map { it.id })
        assertNotSame(first.archive, second.archive)
    }

    @Test
    fun `joining a folder rebuilds only that folder`() {
        val cache = mutableMapOf(
            1L to chat(1L, order = 0L),
            2L to chat(2L, order = 20L, folders = mapOf(8 to FolderChatPosition(60L, false))),
            3L to chat(3L, archiveOrder = 5L)
        )
        val projection = ChatListProjection()
        val first = project(projection, cache, folderIds = listOf(7, 8))
        assertEquals(emptyList<Long>(), first.folders.getValue(7).map { it.id })

        cache[1L] = cache.getValue(1L).copy(folderPositions = mapOf(7 to FolderChatPosition(40L, true)))
        val second = project(projection, cache, folderIds = listOf(7, 8))

        assertEquals(listOf(1L), second.folders.getValue(7).map { it.id })
        assertTrue(second.folders.getValue(7).single().isPinned)
        assertEquals(40L, second.folders.getValue(7).single().order)
        assertSame(first.main, second.main)
        assertSame(first.archive, second.archive)
        assertSame(first.folders.getValue(8), second.folders.getValue(8))
    }

    @Test
    fun `folder pinned state and order belong to the folder`() {
        val cache = mapOf(
            1L to chat(1L, order = 500L, pinned = false, folders = mapOf(7 to FolderChatPosition(100L, true))),
            2L to chat(2L, order = 5L, folders = mapOf(7 to FolderChatPosition(300L, false)))
        )
        val lists = project(ChatListProjection(), cache, folderIds = listOf(7))
        assertEquals(listOf(1L, 2L), lists.folders.getValue(7).map { it.id })
        assertTrue(lists.folders.getValue(7).first().isPinned)
        assertEquals(300L, lists.folders.getValue(7).last().order)
        assertEquals(listOf(1L, 2L), lists.main.map { it.id })
        assertEquals(500L, lists.main.first().order)
        assertEquals(5L, lists.main.last().order)
    }

    @Test
    fun `adding and removing folders builds and drops only their lists`() {
        val cache = mutableMapOf(
            1L to chat(1L, order = 10L),
            2L to chat(2L, folders = mapOf(7 to FolderChatPosition(5L, false))),
            3L to chat(3L, folders = mapOf(8 to FolderChatPosition(9L, false)))
        )
        val projection = ChatListProjection()
        val first = project(projection, cache, folderIds = listOf(7))

        val second = project(projection, cache, folderIds = listOf(8, 7))
        assertEquals(listOf(8, 7), second.folders.keys.toList())
        assertEquals(listOf(3L), second.folders.getValue(8).map { it.id })
        assertSame(first.folders.getValue(7), second.folders.getValue(7))
        assertSame(first.main, second.main)

        val third = project(projection, cache, folderIds = listOf(8))
        assertEquals(listOf(8), third.folders.keys.toList())
        assertSame(second.folders.getValue(8), third.folders.getValue(8))
    }

    @Test
    fun `a new cache entry with the same content rebuilds nothing`() {
        val cache = mutableMapOf(
            1L to chat(1L, order = 10L, folders = mapOf(7 to FolderChatPosition(5L, false))),
            2L to chat(2L, archiveOrder = 4L)
        )
        val projection = ChatListProjection()
        val first = project(projection, cache, folderIds = listOf(7))

        for (id in cache.keys.toList()) cache[id] = cache.getValue(id).copy()
        val second = project(projection, cache, folderIds = listOf(7))

        assertSame(first.main, second.main)
        assertSame(first.archive, second.archive)
        assertSame(first.folders, second.folders)
    }

    @Test
    fun `a mute change rebuilds Main with the new flag and leaves the archive alone`() {
        val cache = mapOf(
            1L to chat(1L, order = 10L),
            2L to chat(2L, order = 20L),
            3L to chat(3L, archiveOrder = 4L)
        )
        val projection = ChatListProjection()
        val first = project(projection, cache)
        val second = project(projection, cache, muted = setOf(2L))

        assertTrue(second.main.first { it.id == 2L }.isMuted)
        assertFalse(second.main.first { it.id == 1L }.isMuted)
        assertNotSame(first.main, second.main)
        assertSame(first.archive, second.archive)
    }

    @Test
    fun `a removed chat leaves every list`() {
        val cache = mutableMapOf(
            1L to chat(1L, order = 10L),
            2L to chat(2L, order = 20L, folders = mapOf(7 to FolderChatPosition(4L, false))),
            3L to chat(3L, archiveOrder = 5L, folders = mapOf(7 to FolderChatPosition(6L, false)))
        )
        val projection = ChatListProjection()
        project(projection, cache, folderIds = listOf(7))

        cache.remove(2L)
        val lists = project(projection, cache, folderIds = listOf(7))

        assertEquals(listOf(1L), lists.main.map { it.id })
        assertEquals(listOf(3L), lists.archive.map { it.id })
        assertEquals(listOf(3L), lists.folders.getValue(7).map { it.id })
    }

    @Test
    fun `repeating an update with nothing changed returns the same instances`() {
        val cache = mapOf(
            1L to chat(1L, order = 10L),
            2L to chat(2L, archiveOrder = 4L, folders = mapOf(7 to FolderChatPosition(3L, false)))
        )
        val projection = ChatListProjection()
        val first = project(projection, cache, folderIds = listOf(7))
        val second = project(projection, cache, folderIds = listOf(7))

        assertSame(first.main, second.main)
        assertSame(first.archive, second.archive)
        assertSame(first.folders, second.folders)
    }

    /** A from-scratch sort with the same rules as before the projection existed. */
    private class Expected(val main: List<ChatPreview>, val archive: List<ChatPreview>, val folders: Map<Int, List<ChatPreview>>)

    private fun expectedLists(chats: Map<Long, ChatPreview>, folderIds: List<Int>, muted: Set<Long>): Expected {
        val previews = chats.values.map { it.copy(isMuted = it.id in muted) }
        val listOrder = compareByDescending<ChatPreview> { it.isPinned }.thenByDescending { it.order }.thenByDescending { it.id }
        return Expected(
            main = previews.filter { it.order > 0L }.sortedWith(listOrder),
            archive = previews.filter { it.archiveOrder > 0L }
                .sortedWith(compareByDescending<ChatPreview> { it.archiveOrder }.thenByDescending { it.id }),
            folders = folderIds.associateWith { folderId ->
                previews.mapNotNull { preview ->
                    val position = preview.folderPositions[folderId]?.takeIf { it.order > 0L } ?: return@mapNotNull null
                    preview.copy(isPinned = position.pinned, order = position.order)
                }.sortedWith(listOrder)
            }
        )
    }

    private fun ChatPreview.randomised(random: Random): ChatPreview {
        val folders = (1..3).mapNotNull { folderId ->
            when (random.nextInt(4)) {
                0 -> null
                1 -> folderId to FolderChatPosition(0L, false)
                else -> folderId to FolderChatPosition(random.nextLong(1L, 40L), random.nextBoolean())
            }
        }.toMap()
        return copy(
            order = if (random.nextInt(3) == 0) 0L else random.nextLong(1L, 40L),
            isPinned = random.nextInt(5) == 0,
            archiveOrder = if (random.nextInt(4) == 0) random.nextLong(1L, 40L) else 0L,
            unreadCount = random.nextInt(3),
            folderPositions = folders
        )
    }

    @Test
    fun `every update equals a full sort of the same chats`() {
        val random = Random(20261103)
        val cache = HashMap<Long, ChatPreview>()
        val muted = HashSet<Long>()
        var folderIds = listOf(1, 2, 3)
        val projection = ChatListProjection()

        repeat(400) { step ->
            repeat(random.nextInt(1, 4)) {
                val id = random.nextLong(1L, 41L)
                val operation = random.nextInt(10)
                if (operation == 0) {
                    cache.remove(id)
                } else if (operation == 1) {
                    cache[id]?.let { cache[id] = it.copy() }
                } else if (operation == 2) {
                    if (!muted.remove(id)) muted.add(id)
                } else {
                    cache[id] = (cache[id] ?: chat(id)).randomised(random)
                }
            }
            if (random.nextInt(8) == 0) folderIds = (1..3).toList().shuffled(random).take(random.nextInt(0, 4))

            val snapshot = project(projection, cache, folderIds, muted)
            val expected = expectedLists(cache, folderIds, muted)
            assertEquals("main at step $step", expected.main, snapshot.main)
            assertEquals("archive at step $step", expected.archive, snapshot.archive)
            assertEquals("folders at step $step", expected.folders, snapshot.folders)
            assertEquals("folder order at step $step", folderIds, snapshot.folders.keys.toList())
        }
    }
}
