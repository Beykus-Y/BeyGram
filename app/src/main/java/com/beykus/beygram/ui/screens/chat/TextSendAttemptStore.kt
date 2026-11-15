package com.beykus.beygram.ui.screens.chat

import android.util.AtomicFile
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
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
            val entities = JSONArray()
            a.entities.forEach { e -> entities.put(JSONObject().put("offset", e.offset).put("length", e.length)
                .put("kind", e.kind.name).put("argument", e.argument)) }
            rows.put(JSONObject().put("id", a.attemptId).put("account", a.scope.accountId)
                .put("chat", a.scope.chatId).put("topic", a.scope.forumTopicId).put("text", a.text)
                .put("entities", entities).put("reply", a.replyToMessageId).put("time", a.timeLabel)
                .put("forceReply", a.dismissForceReplyMessageId).put("baseline", a.baselineSequence)
                .put("silent", a.options.disableNotification).put("scheduled", a.options.scheduledAtSeconds)
                .put("viewOnce", a.options.viewOnce).put("consent", a.options.sendNowConsent)
                .put("noPreview", a.options.disableLinkPreview).put("phase", a.phase.name).put("error", a.errorMessage))
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
            val entities = a.getJSONArray("entities")
            TextSendAttempt(
                attemptId = id, scope = TextSendScopeKey(a.getString("account"), a.getLong("chat"), a.getLong("topic")),
                text = a.getString("text"), entities = (0 until entities.length()).map { i ->
                    val e = entities.getJSONObject(i)
                    MessageTextEntity(e.getInt("offset"), e.getInt("length"), TextStyleKind.valueOf(e.getString("kind")), e.getString("argument"))
                }, replyToMessageId = if (a.has("reply")) a.getLong("reply") else null, replyPreview = null,
                options = MessageSendOptions(a.getBoolean("silent"), if (a.has("scheduled")) a.getLong("scheduled") else null,
                    a.getBoolean("viewOnce"), a.getBoolean("consent"), a.getBoolean("noPreview")),
                baselineSequence = a.getLong("baseline"), timeLabel = a.getString("time"),
                dismissForceReplyMessageId = if (a.has("forceReply")) a.getLong("forceReply") else null,
                phase = TextSendAttemptPhase.valueOf(a.optString("phase", "Sending")),
                errorMessage = if (a.has("error")) a.getString("error") else null
            )
        }
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
        val input = try {
            file.openRead()
        } catch (error: java.io.FileNotFoundException) {
            // A missing snapshot is normal. An existing but unreadable one is not.
            val base = file.baseFile
            if (!base.exists() && !File(base.path + ".bak").exists()) return emptyList()
            throw error
        }
        val bytes = input.use {
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
        return TextSendSnapshotCodec.decode(bytes)
    }
}
