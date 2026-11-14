package com.beykus.beygram.ui.screens.chat

import android.util.AtomicFile
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal interface TextSendAttemptStore {
    fun load(): List<TextSendAttempt>
    fun save(attempts: List<TextSendAttempt>)
}

/** Private app storage: commit the snapshot before the composer may clear its draft. */
internal class FileTextSendAttemptStore(file: File) : TextSendAttemptStore {
    private val file = AtomicFile(file)
    @Synchronized override fun save(attempts: List<TextSendAttempt>) {
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
        val output = file.startWrite()
        try {
            output.write(rows.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }
    @Synchronized override fun load(): List<TextSendAttempt> {
        val bytes = try { file.openRead().use { it.readBytes() } } catch (_: java.io.FileNotFoundException) { return emptyList() }
        val rows = JSONArray(String(bytes, Charsets.UTF_8))
        return (0 until rows.length()).map { index ->
            val a = rows.getJSONObject(index)
            val entities = a.getJSONArray("entities")
            TextSendAttempt(
                attemptId = a.getLong("id"), scope = TextSendScopeKey(a.getString("account"), a.getLong("chat"), a.getLong("topic")),
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
