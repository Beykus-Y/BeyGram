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
