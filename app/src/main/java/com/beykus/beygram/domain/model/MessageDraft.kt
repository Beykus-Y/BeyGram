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

/** [synced] is true only while TDLib is known to hold this exact draft; an unsent local edit keeps it false. */
data class StoredMessageDraft(val value: MessageDraft, val updatedAtMillis: Long, val synced: Boolean = false)

/** Decides which copy of a chat draft wins when TDLib reports a draft that differs from the local backup. */
object MessageDraftSync {
    /**
     * A synced backup only mirrors TDLib, so TDLib's state always wins over it, even for a draft cleared elsewhere
     * (which has no timestamp) or when a device clock is skewed. Unsent local edits win over a cleared draft and
     * over a remote draft that is not newer.
     */
    fun keepsUnsentLocal(local: StoredMessageDraft?, remote: MessageDraft, remoteUpdatedAtMillis: Long): Boolean =
        local != null && !local.synced && (remote.isEmpty || local.updatedAtMillis >= remoteUpdatedAtMillis)
}

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
        val synced = if (value.synced) "1" else "0"
        return listOf("v2", value.updatedAtMillis.toString(), (draft.replyToMessageId ?: 0L).toString(), synced, encodeText(draft.text), entities).joinToString("|")
    }

    fun decode(raw: String): StoredMessageDraft? = runCatching {
        val pieces = raw.split('|', limit = 6)
        if (pieces.size != 6 || pieces[0] != "v2") return@runCatching null
        val timestamp = pieces[1].toLong().takeIf { it >= 0L } ?: return@runCatching null
        val reply = pieces[2].toLong().takeIf { it > 0L }
        val synced = when (pieces[3]) {
            "1" -> true
            "0" -> false
            else -> return@runCatching null
        }
        val text = decodeText(pieces[4])
        val entities = pieces[5].takeIf { it.isNotEmpty() }?.split(';')?.mapNotNull { rawEntity ->
            runCatching {
                val fields = rawEntity.split(',', limit = 4)
                if (fields.size != 4) return@runCatching null
                MessageTextEntity(fields[0].toInt(), fields[1].toInt(), TextStyleKind.valueOf(fields[2]), decodeText(fields[3]))
            }.getOrNull()
        }.orEmpty()
        StoredMessageDraft(MessageDraft(text, validTextEntities(text, entities), reply), timestamp, synced)
    }.getOrNull()
}
