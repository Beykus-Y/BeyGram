package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForumTopicBrief
import com.beykus.beygram.domain.model.ForumTopicIcon
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

/** A forum topic. Topic ids are unique only inside their forum, so the chat id is part of the key. */
internal data class ForumTopicKey(val chatId: Long, val topicId: Long)

/** Topic briefs kept per account; idle ones beyond this are dropped and asked again when a row shows them again. */
internal const val FORUM_TOPIC_BRIEF_CAPACITY = 256

/** The name and icon of a topic, as TDLib's ForumTopicInfo describes them. */
internal fun forumTopicBrief(info: TdApi.ForumTopicInfo): ForumTopicBrief = ForumTopicBrief(
    id = info.forumTopicId.toLong(),
    title = info.name.orEmpty(),
    icon = info.icon?.let { ForumTopicIcon(colorArgb = it.color, customEmojiId = it.customEmojiId) },
    isGeneral = info.isGeneral,
    isNameImplicit = info.isNameImplicit
)

/** The brief of a topic that a forum listing already carried. */
internal fun forumTopicBrief(topic: ForumTopic): ForumTopicBrief = ForumTopicBrief(
    id = topic.id,
    title = topic.title,
    icon = topic.icon,
    isGeneral = topic.isGeneral,
    isNameImplicit = topic.isNameImplicit
)

/** The brief of a topic that TDLib could not describe: no name, so the row falls back to the generic topic label. */
internal fun unresolvedForumTopicBrief(topicId: Long): ForumTopicBrief = ForumTopicBrief(id = topicId, title = "")

/**
 * Names and icons of forum topics for the chat list; one instance per account.
 *
 * Nothing is loaded for a forum up front. A row asks for the topic of its last message through [observe]: the first reader of
 * a topic makes one question through [fetch], and nobody asks again while that answer is open. A topic that TDLib cannot
 * describe keeps an unresolved brief, so its failure is not asked again while the cache holds it. TDLib's topic updates and forum listings fill the
 * briefs without any question.
 *
 * The briefs live in an [EvictableStateMap], so only about [FORUM_TOPIC_BRIEF_CAPACITY] idle briefs are kept. A topic that a
 * row still watches is never dropped.
 */
internal class TdForumTopicBriefs(
    /**
     * Asks TDLib for one topic. [onAnswer] receives the brief, or null when TDLib has no such topic, and is called once. The
     * answer may arrive on TDLib's thread.
     */
    private val fetch: (ForumTopicKey, (ForumTopicBrief?) -> Unit) -> Unit,
    capacity: Int = FORUM_TOPIC_BRIEF_CAPACITY
) {
    private val lock = Any()
    /** Topics whose question to TDLib has no answer yet. Guarded by [lock]. */
    private val asked = HashSet<ForumTopicKey>()
    private val briefs = EvictableStateMap<ForumTopicKey, ForumTopicBrief?>(
        lock = lock,
        maxEntries = capacity,
        seed = { _ -> null },
        // A settled brief, unresolved or not, is asked again only after the map has dropped it. An open question stays.
        isIdle = { _, brief -> brief != null },
        onCreatedByRead = { key -> ask(key) }
    )

    /** The name and icon of a topic, null until it is known. The first reader asks TDLib once. */
    fun observe(chatId: Long, forumTopicId: Long): StateFlow<ForumTopicBrief?> =
        briefs.observe(ForumTopicKey(chatId, forumTopicId))

    /** A topic changed on the server, as UpdateForumTopicInfo reports it. */
    fun receive(info: TdApi.ForumTopicInfo) {
        store(ForumTopicKey(info.chatId, info.forumTopicId.toLong()), forumTopicBrief(info))
    }

    /** The topics of a forum listing that TDLib has just returned. */
    fun remember(chatId: Long, topics: List<ForumTopic>) {
        topics.forEach { topic -> store(ForumTopicKey(chatId, topic.id), forumTopicBrief(topic)) }
    }

    private fun store(key: ForumTopicKey, brief: ForumTopicBrief) {
        synchronized(lock) { briefs.stateLocked(key).value = brief }
    }

    private fun ask(key: ForumTopicKey) {
        synchronized(lock) {
            if (!asked.add(key)) return
        }
        val failure = runCatching { fetch(key) { brief -> answer(key, brief) } }.exceptionOrNull()
        if (failure != null) answer(key, null)
    }

    private fun answer(key: ForumTopicKey, brief: ForumTopicBrief?) {
        synchronized(lock) { asked.remove(key) }
        store(key, brief ?: unresolvedForumTopicBrief(key.topicId))
    }
}
