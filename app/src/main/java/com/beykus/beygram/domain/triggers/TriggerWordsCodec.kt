package com.beykus.beygram.domain.triggers

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One message that matched at least one trigger. The feed keeps these; alerts are derived from them. */
data class TriggerMention(
    /** Unique per account, chat and message, so one message is recorded and alerted once. */
    val id: String,
    val accountId: String,
    val chatId: Long,
    val messageId: Long,
    val dateSeconds: Long,
    val chatTitle: String,
    val senderName: String,
    /** Null when the text must not be kept: previews are off, or the chat protects its content. */
    val snippet: String?,
    /** The patterns that fired, in the order of the rules. */
    val triggers: List<String>,
    val muted: Boolean
)

/** Stored form of rules and mentions. Unreadable entries are skipped instead of failing the whole list. */
object TriggerWordsCodec {
    fun encodeRules(rules: List<TriggerRule>): String = JSONArray().also { array ->
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("pattern", rule.pattern)
                    .put("mode", rule.mode.name)
                    .put("ignoreCase", rule.ignoreCase)
                    .put("ignoreDiacritics", rule.ignoreDiacritics)
                    .put("enabled", rule.enabled)
            )
        }
    }.toString()

    fun decodeRules(json: String?): List<TriggerRule> = objects(json).mapNotNull { item ->
        TriggerRule(
            id = item.optString("id"),
            pattern = item.optString("pattern"),
            mode = TriggerMatchMode.entries.firstOrNull { it.name == item.optString("mode") } ?: TriggerMatchMode.WHOLE_WORD,
            ignoreCase = item.optBoolean("ignoreCase", true),
            ignoreDiacritics = item.optBoolean("ignoreDiacritics", false),
            enabled = item.optBoolean("enabled", true)
        ).takeIf { it.id.isNotEmpty() && validateTriggerRule(it) == null }
    }.take(TriggerWordLimits.MAX_RULES)

    fun encodeMentions(mentions: List<TriggerMention>): String = JSONArray().also { array ->
        mentions.forEach { mention ->
            val item = JSONObject()
                .put("id", mention.id)
                .put("accountId", mention.accountId)
                .put("chatId", mention.chatId)
                .put("messageId", mention.messageId)
                .put("dateSeconds", mention.dateSeconds)
                .put("chatTitle", mention.chatTitle)
                .put("senderName", mention.senderName)
                .put("triggers", JSONArray(mention.triggers))
                .put("muted", mention.muted)
            mention.snippet?.let { item.put("snippet", it) }
            array.put(item)
        }
    }.toString()

    fun decodeMentions(json: String?): List<TriggerMention> = objects(json).mapNotNull { item ->
        val id = item.optString("id")
        if (id.isEmpty()) return@mapNotNull null
        val names = item.optJSONArray("triggers")
        val triggers = if (names == null) emptyList() else (0 until names.length()).map { names.optString(it) }.filter { it.isNotEmpty() }
        TriggerMention(
            id = id,
            accountId = item.optString("accountId", "legacy"),
            chatId = item.optLong("chatId"),
            messageId = item.optLong("messageId"),
            dateSeconds = item.optLong("dateSeconds"),
            chatTitle = item.optString("chatTitle"),
            senderName = item.optString("senderName"),
            snippet = item.optString("snippet", "").takeIf { it.isNotEmpty() },
            triggers = triggers,
            muted = item.optBoolean("muted", false)
        )
    }.take(TriggerWordLimits.MAX_MENTIONS)

    private fun objects(json: String?): List<JSONObject> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
