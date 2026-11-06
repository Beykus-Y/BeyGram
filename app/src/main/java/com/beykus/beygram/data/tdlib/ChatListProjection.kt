package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.archiveChatOrder
import com.beykus.beygram.domain.model.isInArchive
import com.beykus.beygram.domain.model.isInFolder
import com.beykus.beygram.domain.model.isInMainList
import com.beykus.beygram.domain.model.telegramFolderRow
import com.beykus.beygram.domain.model.telegramListOrder

/** The chat-list lists after an update. A list that no change reached is the same instance as before. */
internal class ChatListSnapshot(
    val main: List<ChatPreview>,
    val archive: List<ChatPreview>,
    val folders: Map<Int, List<ChatPreview>>
)

/**
 * Keeps the Main, Archive and server-folder lists without sorting every list on each publish.
 *
 * A chat has changed when the row it would show differs from the row the lists hold. A new cache entry that is equal
 * to the old row, or a mute state that leaves the row the same, is not a change. Only the lists that held a changed
 * chat before, or hold it now, are rebuilt: rows of unchanged chats are reused and the changed rows are merged back in
 * their order. A list that no change reaches keeps its instance, so a StateFlow holding it does not re-emit and
 * Compose can skip it.
 *
 * Each list equals a full sort of the same chats, because [telegramListOrder] and [archiveChatOrder] are total orders
 * over unique chat IDs. Not thread-safe: the chat-list publisher calls [update] one run at a time.
 */
internal class ChatListProjection {
    /** [preview] is the cache entry seen last; [row] is that chat as the lists hold it, with its mute state applied. */
    private class Known(val preview: ChatPreview, val row: ChatPreview)

    /** A chat whose row changed. [before] is the row the lists held, or null when they did not hold the chat. */
    private class Change(val id: Long, val before: ChatPreview?)

    private val known = HashMap<Long, Known>()
    private var knownFolderIds: List<Int> = emptyList()
    private var main: List<ChatPreview> = emptyList()
    private var archive: List<ChatPreview> = emptyList()
    private var folders: Map<Int, List<ChatPreview>> = emptyMap()

    fun update(chats: Map<Long, ChatPreview>, folderIds: List<Int>, isMuted: (Long) -> Boolean): ChatListSnapshot {
        val activeFolderIds = folderIds.distinct()
        val changes = recordChanges(chats, isMuted)
        if (changes.isEmpty() && activeFolderIds == knownFolderIds) return snapshot()

        val changedIds = LongArray(changes.size) { changes[it].id }
        changedIds.sort()

        if (changes.any { it.before?.isInMainList() == true || rowOf(it.id)?.isInMainList() == true }) {
            val added = changes.mapNotNull { rowOf(it.id) }.filter { it.isInMainList() }
            main = spliceRows(main, changedIds, added, telegramListOrder)
        }
        if (changes.any { it.before?.isInArchive() == true || rowOf(it.id)?.isInArchive() == true }) {
            val added = changes.mapNotNull { rowOf(it.id) }.filter { it.isInArchive() }
            archive = spliceRows(archive, changedIds, added, archiveChatOrder)
        }

        val nextFolders = LinkedHashMap<Int, List<ChatPreview>>()
        var foldersChanged = activeFolderIds != knownFolderIds
        for (folderId in activeFolderIds) {
            val previous = folders[folderId]
            val rows: List<ChatPreview> = if (previous == null) {
                // A folder the lists did not have yet: every known chat is a candidate.
                known.values.mapNotNull { telegramFolderRow(it.row, folderId) }.sortedWith(telegramListOrder)
            } else if (changes.any { it.before?.isInFolder(folderId) == true || rowOf(it.id)?.isInFolder(folderId) == true }) {
                val added = changes.mapNotNull { change -> rowOf(change.id)?.let { telegramFolderRow(it, folderId) } }
                spliceRows(previous, changedIds, added, telegramListOrder)
            } else {
                previous
            }
            nextFolders[folderId] = rows
            if (rows !== previous) foldersChanged = true
        }
        if (foldersChanged) folders = nextFolders
        knownFolderIds = activeFolderIds
        return snapshot()
    }

    private fun snapshot(): ChatListSnapshot = ChatListSnapshot(main, archive, folders)

    private fun rowOf(id: Long): ChatPreview? = known[id]?.row

    /** Compares the cache with the last update. A chat whose row is equal to the shown one is not a change. */
    private fun recordChanges(chats: Map<Long, ChatPreview>, isMuted: (Long) -> Boolean): List<Change> {
        val changes = ArrayList<Change>()
        for ((id, preview) in chats) {
            val muted = isMuted(id)
            val entry = known[id]
            if (entry != null && entry.preview === preview && entry.row.isMuted == muted) continue
            val row = if (preview.isMuted == muted) preview else preview.copy(isMuted = muted)
            if (entry != null && entry.row == row) {
                // A new cache entry that shows the same row: the lists keep the row they already hold.
                known[id] = Known(preview, entry.row)
            } else {
                known[id] = Known(preview, row)
                changes += Change(id, entry?.row)
            }
        }
        val removed = known.keys.filter { !chats.containsKey(it) }
        for (id in removed) {
            val entry = known.remove(id) ?: continue
            changes += Change(id, entry.row)
        }
        return changes
    }

    /**
     * [previous] without the rows of [changedIds], merged with the [added] rows in [order]. [previous] is already in
     * [order], so the result is too. [changedIds] must be sorted.
     */
    private fun spliceRows(
        previous: List<ChatPreview>,
        changedIds: LongArray,
        added: List<ChatPreview>,
        order: Comparator<ChatPreview>
    ): List<ChatPreview> {
        val incoming = added.sortedWith(order)
        val merged = ArrayList<ChatPreview>(previous.size + incoming.size)
        var next = 0
        for (row in previous) {
            if (changedIds.binarySearch(row.id) >= 0) continue
            while (next < incoming.size && order.compare(incoming[next], row) < 0) merged.add(incoming[next++])
            merged.add(row)
        }
        while (next < incoming.size) merged.add(incoming[next++])
        return merged
    }
}
