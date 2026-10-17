package com.beykus.beygram.domain.model

import java.io.Serializable

enum class TextStyleKind { Bold, Italic, Underline, Strike, Code, Pre, Quote, Spoiler, Url, Mention, MentionName, Email, Phone, Command, Hashtag, CustomEmoji }

/** Telegram and Kotlin both measure these offsets in UTF-16 code units. */
data class MessageTextEntity(val offset: Int, val length: Int, val kind: TextStyleKind, val argument: String = "") : Serializable

fun validTextEntities(text: String, entities: List<MessageTextEntity>): List<MessageTextEntity> = entities.filter {
    it.offset >= 0 && it.length > 0 && it.offset.toLong() + it.length <= text.length &&
        !(it.offset > 0 && text[it.offset].isLowSurrogate() && text[it.offset - 1].isHighSurrogate()) &&
        !(it.offset + it.length < text.length && text[it.offset + it.length].isLowSurrogate() && text[it.offset + it.length - 1].isHighSurrogate())
}

/** Preserve formatting around a single edit (including paste, replacement and deletion). */
fun adjustTextEntities(before: String, after: String, entities: List<MessageTextEntity>): List<MessageTextEntity> {
    if (before == after) return entities
    var start = 0
    while (start < minOf(before.length, after.length) && before[start] == after[start]) start++
    var oldEnd = before.length
    var newEnd = after.length
    while (oldEnd > start && newEnd > start && before[oldEnd - 1] == after[newEnd - 1]) { oldEnd--; newEnd-- }
    val delta = newEnd - oldEnd
    return validTextEntities(after, entities.mapNotNull {
        val end = it.offset + it.length
        when {
            end <= start -> it
            it.offset >= oldEnd -> it.copy(offset = it.offset + delta)
            else -> {
                val left = minOf(it.offset, start)
                val right = if (end >= oldEnd) end + delta else newEnd
                if (right > left) it.copy(offset = left, length = right - left) else null
            }
        }
    })
}

enum class BotButtonKind { Text, Callback, Url, Copy, User, Unsupported }
data class BotButton(val text: String, val kind: BotButtonKind, val value: String = "", val data: List<Byte> = emptyList())
data class BotKeyboard(val messageId: Long, val rows: List<List<BotButton>>, val oneTime: Boolean = false, val persistent: Boolean = false, val placeholder: String = "", val forceReply: Boolean = false)
data class BotCallbackAnswer(val text: String, val showAlert: Boolean, val url: String)

data class MessageTextPart(val text: String, val entities: List<MessageTextEntity>, val block: TextStyleKind? = null)

/** Extract actual quote/code blocks while keeping nested styles relative to each fragment. */
fun splitMessageText(text: String, source: List<MessageTextEntity>): List<MessageTextPart> {
    val entities = validTextEntities(text, source)
    val blocks = entities.filter { it.kind == TextStyleKind.Quote || it.kind == TextStyleKind.Pre }.sortedBy { it.offset }
    if (blocks.isEmpty()) return listOf(MessageTextPart(text, entities))
    return buildList {
        fun part(from: Int, to: Int, kind: TextStyleKind?) {
            var start = from
            var end = to
            if (kind == null) {
                if (start > 0 && start < end && text[start] == '\n') start++
                if (end < text.length && end > start && text[end - 1] == '\n') end--
            }
            if (start >= end) return
            val nested = entities.filter { it.kind != TextStyleKind.Quote && it.kind != TextStyleKind.Pre }.mapNotNull {
                val left = maxOf(start, it.offset)
                val right = minOf(end, it.offset + it.length)
                if (left < right) it.copy(offset = left - start, length = right - left) else null
            }
            add(MessageTextPart(text.substring(start, end), nested, kind))
        }
        var cursor = 0
        blocks.forEach {
            if (it.offset >= cursor) {
                part(cursor, it.offset, null)
                part(it.offset, it.offset + it.length, it.kind)
                cursor = it.offset + it.length
            }
        }
        part(cursor, text.length, null)
    }
}

fun applyTextFormat(text: String, entities: List<MessageTextEntity>, from: Int, until: Int, kind: TextStyleKind?): List<MessageTextEntity> {
    if (from !in 0..text.length || until !in 0..text.length || from >= until) return entities
    val start = if (kind == TextStyleKind.Quote || kind == TextStyleKind.Pre) text.lastIndexOf('\n', from - 1).let { it + 1 } else from
    val end = if (kind == TextStyleKind.Quote || kind == TextStyleKind.Pre) text.indexOf('\n', until).let { if (it < 0) text.length else it } else until
    val mono = setOf(TextStyleKind.Code, TextStyleKind.Pre)
    val updated = validTextEntities(text, entities).flatMap { entity ->
        val stop = entity.offset + entity.length
        if (stop <= start || entity.offset >= end) listOf(entity)
        else buildList {
            if (entity.offset < start) add(entity.copy(length = start - entity.offset))
            if (kind != null && entity.kind != kind && kind !in mono && entity.kind !in mono) {
                val left = maxOf(entity.offset, start)
                add(entity.copy(offset = left, length = minOf(stop, end) - left))
            }
            if (stop > end) add(entity.copy(offset = end, length = stop - end))
        }
    }
    return updated + if (kind != null) listOf(MessageTextEntity(start, end - start, kind)) else emptyList()
}
