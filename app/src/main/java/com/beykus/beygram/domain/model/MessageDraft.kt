package com.beykus.beygram.domain.model

import java.util.Base64

/** A composer draft, independent of transient edit/attachment UI state. */
data class MessageDraft(
    val text: String = "",
    val entities: List<MessageTextEntity> = emptyList(),
    val replyToMessageId: Long? = null
) {
    val isEmpty: Boolean get() = text.isEmpty() && replyToMessageId == null
}

data class StoredMessageDraft(val value: MessageDraft, val updatedAtMillis: Long)

/**
 * Pure-JVM codec for local backup. Base64 keeps newlines, Unicode, separators and entity arguments intact.
 * The on-disk format is versioned so malformed/old values never crash the composer.
 */
object MessageDraftCodec {
    private fun encodeText(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private fun decodeText(encoded: String): String =
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)

    fun encode(value: StoredMessageDraft): String {
        val draft = value.value
        val entities = validTextEntities(draft.text, draft.entities).joinToString(";") {
            "${it.offset},${it.length},${it.kind.name},${encodeText(it.argument)}"
        }
        return listOf("v1", value.updatedAtMillis.toString(), (draft.replyToMessageId ?: 0L).toString(), encodeText(draft.text), entities).joinToString("|")
    }

    fun decode(raw: String): StoredMessageDraft? = runCatching {
        val pieces = raw.split('|', limit = 5)
        if (pieces.size != 5 || pieces[0] != "v1") return@runCatching null
        val timestamp = pieces[1].toLong().takeIf { it >= 0L } ?: return@runCatching null
        val reply = pieces[2].toLong().takeIf { it > 0L }
        val text = decodeText(pieces[3])
        val entities = pieces[4].takeIf { it.isNotEmpty() }?.split(';')?.mapNotNull { rawEntity ->
            runCatching {
                val fields = rawEntity.split(',', limit = 4)
                if (fields.size != 4) return@runCatching null
                MessageTextEntity(fields[0].toInt(), fields[1].toInt(), TextStyleKind.valueOf(fields[2]), decodeText(fields[3]))
            }.getOrNull()
        }.orEmpty()
        StoredMessageDraft(MessageDraft(text, validTextEntities(text, entities), reply), timestamp)
    }.getOrNull()
}
