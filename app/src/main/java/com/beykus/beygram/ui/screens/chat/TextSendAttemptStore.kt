package com.beykus.beygram.ui.screens.chat

import android.util.AtomicFile
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import com.beykus.beygram.domain.model.replyQuotePayload
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

internal interface TextSendAttemptStore {
    suspend fun prepare() {}
    fun load(): List<TextSendAttempt>
    fun save(attempts: List<TextSendAttempt>)
    fun quarantineCorrupt() {}
}

/** The format is bounded and versioned; old unversioned snapshots remain readable. */
internal object TextSendSnapshotCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val VERSION = 1

    fun encode(attempts: List<TextSendAttempt>): ByteArray {
        val rows = JSONArray()
        attempts.forEach { a ->
            val quote = a.options.replyQuotePayload()
            rows.put(JSONObject().put("id", a.attemptId).put("account", a.scope.accountId)
                .put("chat", a.scope.chatId).put("topic", a.scope.forumTopicId).put("dest", a.destinationTopicId).put("text", a.text)
                .put("entities", encodeEntities(a.entities)).put("reply", a.replyToMessageId).put("time", a.timeLabel)
                .put("forceReply", a.dismissForceReplyMessageId).put("baseline", a.baselineSequence)
                .put("silent", a.options.disableNotification).put("scheduled", a.options.scheduledAtSeconds)
                .put("viewOnce", a.options.viewOnce).put("consent", a.options.sendNowConsent)
                .put("noPreview", a.options.disableLinkPreview).put("phase", a.phase.name).put("error", a.errorMessage).apply {
                    if (quote != null) put("quote", JSONObject().put("text", quote.text)
                        .put("position", quote.position).put("entities", encodeEntities(quote.entities)))
                })
        }
        val bytes = JSONObject().put("version", VERSION).put("attempts", rows)
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Pending text snapshot exceeds its size limit" }
        return bytes
    }

    fun decode(bytes: ByteArray): List<TextSendAttempt> {
        require(bytes.size <= MAX_BYTES) { "Pending text snapshot exceeds its size limit" }
        val tokener = JSONTokener(String(bytes, Charsets.UTF_8))
        val rows = when (val root = tokener.nextValue()) {
            is JSONArray -> root
            is JSONObject -> {
                require(root.getInt("version") == VERSION) { "Unsupported pending text snapshot version" }
                root.getJSONArray("attempts")
            }
            else -> throw IOException("Invalid pending text snapshot")
        }
        require(tokener.nextClean() == '\u0000') { "Unexpected data after pending text snapshot" }
        val seen = HashSet<Long>()
        return (0 until rows.length()).map { index ->
            val a = rows.getJSONObject(index)
            val id = a.getLong("id")
            require(id > 0 && id < Long.MAX_VALUE && seen.add(id)) { "Invalid pending text attempt id" }
            val quote = a.optJSONObject("quote")
            val scope = TextSendScopeKey(a.getString("account"), a.getLong("chat"), a.getLong("topic"))
            TextSendAttempt(
                attemptId = id, scope = scope,
                text = a.getString("text"), entities = decodeEntities(a.getJSONArray("entities")),
                replyToMessageId = if (a.has("reply")) a.getLong("reply") else null, replyPreview = null,
                options = MessageSendOptions(
                    disableNotification = a.getBoolean("silent"),
                    scheduledAtSeconds = if (a.has("scheduled")) a.getLong("scheduled") else null,
                    viewOnce = a.getBoolean("viewOnce"),
                    sendNowConsent = a.getBoolean("consent"),
                    disableLinkPreview = a.getBoolean("noPreview"),
                    replyQuote = quote?.getString("text"),
                    replyQuotePosition = quote?.getInt("position") ?: 0,
                    replyQuoteEntities = quote?.getJSONArray("entities")?.let(::decodeEntities).orEmpty()
                ),
                baselineSequence = a.getLong("baseline"), timeLabel = a.getString("time"),
                dismissForceReplyMessageId = if (a.has("forceReply")) a.getLong("forceReply") else null,
                phase = TextSendAttemptPhase.valueOf(a.optString("phase", "Sending")),
                errorMessage = if (a.has("error")) a.getString("error") else null,
                // Rows written before the feed had its own scope carry no destination: their scope's topic is it.
                destinationTopicId = if (a.has("dest")) a.getLong("dest") else scope.forumTopicId
            )
        }
    }
    private fun encodeEntities(entities: List<MessageTextEntity>): JSONArray = JSONArray().apply {
        entities.forEach { e -> put(JSONObject().put("offset", e.offset).put("length", e.length)
            .put("kind", e.kind.name).put("argument", e.argument)) }
    }

    private fun decodeEntities(entities: JSONArray): List<MessageTextEntity> = List(entities.length()) { index ->
        val e = entities.getJSONObject(index)
        MessageTextEntity(e.getInt("offset"), e.getInt("length"), TextStyleKind.valueOf(e.getString("kind")), e.getString("argument"))
    }

}

/** Private app storage: commit the snapshot before the composer may clear its draft. */
internal class FileTextSendAttemptStore(
    path: File,
    private val quarantineDir: File? = null
) : TextSendAttemptStore {
    private val file = AtomicFile(path)

    @Synchronized override fun quarantineCorrupt() {
        val base = file.baseFile
        val targetDir = quarantineDir ?: base.parentFile?.let { File(it, PendingTextSendFiles.CORRUPT_QUARANTINE) }
            ?: throw IOException("Could not resolve pending text quarantine directory")
        PendingTextSendFiles.quarantineCorrupt(base, targetDir)
    }

    @Synchronized override fun save(attempts: List<TextSendAttempt>) {
        val bytes = TextSendSnapshotCodec.encode(attempts)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    @Synchronized override fun load(): List<TextSendAttempt> {
        // AtomicFile.openRead() restores a legacy .bak over the base and may delete
        // .new. Validate the committed candidate first so a corrupt snapshot does
        // not destroy other recoverable evidence before the user chooses quarantine.
        val base = file.baseFile
        val backup = File(base.path + ".bak")
        val committed = if (backup.exists()) backup else base
        if (!committed.exists()) {
            if (File(base.path + ".new").exists()) {
                throw IOException("Uncommitted pending text snapshot requires recovery")
            }
            return emptyList()
        }
        val bytes = committed.inputStream().use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                if (output.size() + read > TextSendSnapshotCodec.MAX_BYTES) {
                    throw IOException("Pending text snapshot exceeds its size limit")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        val decoded = TextSendSnapshotCodec.decode(bytes)
        // Complete normal AtomicFile recovery only after validating the candidate.
        file.openRead().close()
        return decoded
    }
}
