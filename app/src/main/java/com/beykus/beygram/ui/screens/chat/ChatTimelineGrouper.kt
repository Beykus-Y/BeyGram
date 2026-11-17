package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import java.util.concurrent.atomic.AtomicInteger

/** Rows of the feed plus a counter that changes only when the row structure changes. */
internal class TimelineRows(val items: List<ChatMessageListItem>, val structureVersion: Int)

/**
 * Source of [TimelineRows.structureVersion] for every grouper. Rows can come from two groupers (the screen and the
 * background preparation), and day separators are keyed on the version, so a number must never repeat across them.
 */
private val structureVersions = AtomicInteger()

/**
 * The fields that decide where a message sits in the feed and how its row looks around it: grouping into albums,
 * order, sender headers and day separators. A change in anything else (reactions, text, send status...) is content only.
 */
internal fun sameTimelinePlace(a: ChatMessage, b: ChatMessage): Boolean =
    a.id == b.id && a.mediaAlbumId == b.mediaAlbumId && (a.media == null) == (b.media == null) &&
        a.dateSeconds == b.dateSeconds && a.isOutgoing == b.isOutgoing && (a.serviceText == null) == (b.serviceText == null) &&
        a.senderId == b.senderId && a.senderName == b.senderName

/**
 * Keeps the feed rows between history updates. A content-only update replaces just the rows that hold a changed
 * message, so the other rows keep their instances and Compose skips them. Not thread-safe: one caller at a time. Each
 * caller, such as the composition or one background collection, uses its own instance.
 */
internal class ChatTimelineGrouper {
    private var lastMessages: List<ChatMessage>? = null
    private var lastRows = TimelineRows(emptyList(), 0)
    /** Message id -> index of its row in [lastRows]. Trusted only while every id is unique, see [samePlaces]. */
    private val rowIndexById = HashMap<Long, Int>()

    fun group(messages: List<ChatMessage>): TimelineRows {
        if (messages === lastMessages) return lastRows
        val previous = lastMessages
        val patched = if (previous != null && samePlaces(previous, messages)) patchContent(previous, messages) else null
        val rows = patched ?: regroup(messages)
        lastMessages = messages
        lastRows = rows
        return rows
    }

    /**
     * Same length and every message in the same place, so only content can differ. A repeated id would make
     * [rowIndexById] ambiguous, so such a list is always regrouped.
     */
    private fun samePlaces(previous: List<ChatMessage>, messages: List<ChatMessage>): Boolean =
        previous.size == messages.size && rowIndexById.size == previous.size &&
            previous.indices.all { sameTimelinePlace(previous[it], messages[it]) }

    /** Swaps the changed messages into their rows. Returns null when a changed message has no known row. */
    private fun patchContent(previous: List<ChatMessage>, messages: List<ChatMessage>): TimelineRows? {
        val changed = messages.filterIndexed { index, message -> previous[index] !== message }
        if (changed.isEmpty()) return lastRows
        val items = ArrayList<ChatMessageListItem>(lastRows.items)
        for (message in changed) {
            val rowIndex = rowIndexById[message.id] ?: return null
            // Read the row from the edited copy, so several changed members of one album all land in it.
            val row = items[rowIndex]
            items[rowIndex] = row.copy(messages = row.messages.map { if (it.id == message.id) message else it })
        }
        return TimelineRows(items, lastRows.structureVersion)
    }

    /** Groups from scratch. A row keeps its old instance when its members are the same objects as before. */
    private fun regroup(messages: List<ChatMessage>): TimelineRows {
        val oldRowByKey = HashMap<String, ChatMessageListItem>()
        for (row in lastRows.items) oldRowByKey[row.key] = row
        val fresh = groupTelegramMediaAlbums(messages)
        val items = ArrayList<ChatMessageListItem>(fresh.size)
        for (row in fresh) {
            val old = oldRowByKey[row.key]
            items.add(if (old != null && sameMembers(old.messages, row.messages)) old else row)
        }
        rowIndexById.clear()
        for (index in items.indices) {
            for (member in items[index].messages) rowIndexById[member.id] = index
        }
        return TimelineRows(items, structureVersions.incrementAndGet())
    }

    private fun sameMembers(old: List<ChatMessage>, fresh: List<ChatMessage>): Boolean =
        old.size == fresh.size && old.indices.all { old[it] === fresh[it] }
}
