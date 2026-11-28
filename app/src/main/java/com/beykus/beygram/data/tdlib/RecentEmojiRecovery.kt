package com.beykus.beygram.data.tdlib

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import android.os.SystemClock
import com.beykus.beygram.domain.model.RecentEmojiHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/** The few TDLib reads the recovery needs; a fake implements them in tests without a native client. */
internal interface RecentEmojiSource {
    suspend fun meId(): Long

    suspend fun chatIds(limit: Int): List<Long>

    suspend fun chatIsSecret(chatId: Long): Boolean

    suspend fun ownMessages(chatId: Long, senderId: Long, limit: Int): List<TdApi.Message>
}

/** TDLib implementation: only cached/served own messages, never the full account history. */
internal class TdRecentEmojiSource(private val request: TdRequests) : RecentEmojiSource {
    override suspend fun meId(): Long = request(TdApi.GetMe(), REQUEST_TIMEOUT_MS).id

    override suspend fun chatIds(limit: Int): List<Long> {
        // A missing/partially loaded list is not an error here: GetChats still returns what TDLib knows.
        try {
            request(TdApi.LoadChats(TdApi.ChatListMain(), limit), REQUEST_TIMEOUT_MS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep going with the chats already known.
        }
        return request(TdApi.GetChats(TdApi.ChatListMain(), limit), REQUEST_TIMEOUT_MS).chatIds?.toList().orEmpty()
    }

    override suspend fun chatIsSecret(chatId: Long): Boolean =
        request(TdApi.GetChat(chatId), REQUEST_TIMEOUT_MS).type is TdApi.ChatTypeSecret

    override suspend fun ownMessages(chatId: Long, senderId: Long, limit: Int): List<TdApi.Message> =
        request(
            TdApi.SearchChatMessages(
                chatId,
                null,
                "",
                TdApi.MessageSenderUser(senderId),
                0,
                0,
                limit,
                TdApi.SearchMessagesFilterEmpty()
            ),
            REQUEST_TIMEOUT_MS
        ).messages.orEmpty().filterNotNull()

    private companion object {
        /** One slow chat must not hold the whole bounded recovery; the wall-clock budget stops it anyway. */
        const val REQUEST_TIMEOUT_MS = 4_000L
    }
}

/**
 * Rebuilds the local recent-emoji list from the account's own sent messages. Telegram provides no server-side recent
 * emoji list, so this is the only honest way to recover it after a reinstall. The scan is deliberately small: the most
 * recent few chats, one page of own messages per chat, a wall-clock budget, no secret chats, and only emoji/custom-emoji
 * ids ever leave this class. It never logs or uploads message text.
 */
internal class RecentEmojiRecovery(
    private val source: RecentEmojiSource,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    suspend fun recover(
        unicodeLimit: Int = DEFAULT_UNICODE_LIMIT,
        customLimit: Int = DEFAULT_CUSTOM_LIMIT,
    ): RecentEmojiHistory = withContext(Dispatchers.IO) {
        if (unicodeLimit <= 0 && customLimit <= 0) return@withContext RecentEmojiHistory()
        val deadline = clock() + TOTAL_BUDGET_MS
        val me = source.meId()
        val chatIds = source.chatIds(MAX_CHATS)
        var succeededChats = 0
        var lastFailure: Exception? = null
        val collected = ArrayList<TdApi.Message>()
        for (chatId in chatIds.take(MAX_CHATS)) {
            currentCoroutineContext().ensureActive()
            if (clock() >= deadline) break
            val secret = try {
                source.chatIsSecret(chatId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                lastFailure = failure
                continue
            }
            // A secret chat must never be read for this, even though only emoji are extracted.
            if (secret) {
                succeededChats++
                continue
            }
            val messages = try {
                source.ownMessages(chatId, me, MESSAGES_PER_CHAT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                lastFailure = failure
                continue
            }
            succeededChats++
            for (message in messages) {
                if (message.isOutgoing) collected += message
            }
        }
        if (chatIds.isNotEmpty() && succeededChats == 0) {
            throw lastFailure ?: IllegalStateException("Recent emoji recovery did not complete for any chat")
        }
        collected.sortWith(compareByDescending<TdApi.Message> { it.date }.thenByDescending { it.id })
        val unicode = LinkedHashSet<String>()
        val custom = LinkedHashSet<Long>()
        for (message in collected) {
            val extracted = extractRecentEmoji(message.content)
            extracted.unicode.forEach { if (unicode.size < unicodeLimit) unicode.add(it) }
            extracted.customEmojiIds.forEach { if (custom.size < customLimit) custom.add(it) }
            if (unicode.size >= unicodeLimit && custom.size >= customLimit) break
        }
        RecentEmojiHistory(unicode.toList(), custom.toList())
    }

    private companion object {
        /** Recent chats are the best proxy for the user's recent emoji; older chats rarely add something new. */
        const val MAX_CHATS = 10
        const val MESSAGES_PER_CHAT = 100
        const val TOTAL_BUDGET_MS = 8_000L
        const val DEFAULT_UNICODE_LIMIT = 48
        const val DEFAULT_CUSTOM_LIMIT = 36
    }
}

/** Emoji found in one message. */
internal data class ExtractedEmoji(
    val unicode: List<String> = emptyList(),
    val customEmojiIds: List<Long> = emptyList()
) {
    val isEmpty: Boolean get() = unicode.isEmpty() && customEmojiIds.isEmpty()
}

/** The longest list one message may contribute, so a single "emoji wall" cannot fill the recents by itself. */
private const val MAX_EMOJI_PER_MESSAGE = 32

/** Emoji of a message or media caption. Stickers, dice and service messages are not emoji usage. */
internal fun extractRecentEmoji(content: TdApi.MessageContent?): ExtractedEmoji = when (content) {
    is TdApi.MessageText -> extractRecentEmoji(content.text?.text.orEmpty(), content.text?.entities?.toList().orEmpty())
    is TdApi.MessagePhoto -> extractRecentEmoji(content.caption?.text.orEmpty(), content.caption?.entities?.toList().orEmpty())
    is TdApi.MessageVideo -> extractRecentEmoji(content.caption?.text.orEmpty(), content.caption?.entities?.toList().orEmpty())
    is TdApi.MessageAnimation -> extractRecentEmoji(content.caption?.text.orEmpty(), content.caption?.entities?.toList().orEmpty())
    is TdApi.MessageDocument -> extractRecentEmoji(content.caption?.text.orEmpty(), content.caption?.entities?.toList().orEmpty())
    is TdApi.MessageAudio -> extractRecentEmoji(content.caption?.text.orEmpty(), content.caption?.entities?.toList().orEmpty())
    is TdApi.MessageVoiceNote -> extractRecentEmoji(content.caption?.text.orEmpty(), content.caption?.entities?.toList().orEmpty())
    is TdApi.MessageAnimatedEmoji -> {
        val id = (content.animatedEmoji?.sticker?.fullType as? TdApi.StickerFullTypeCustomEmoji)?.customEmojiId ?: 0L
        ExtractedEmoji(customEmojiIds = listOfNotNull(id.takeIf { it > 0L }))
    }
    else -> ExtractedEmoji()
}

/**
 * Unicode emoji grapheme clusters of [text] and the custom emoji entities. A custom emoji's fallback glyph is not
 * counted as a Unicode emoji: the entity is the real usage. Offsets are UTF-16, exactly as TDLib sends them.
 */
internal fun extractRecentEmoji(text: String, entities: List<TdApi.TextEntity>): ExtractedEmoji {
    if (text.isEmpty()) return ExtractedEmoji()
    val custom = LinkedHashSet<Long>()
    val customRanges = ArrayList<IntRange>()
    entities.forEach { entity ->
        val type = entity.type
        if (type !is TdApi.TextEntityTypeCustomEmoji) return@forEach
        val id = type.customEmojiId
        if (id > 0L) custom.add(id)
        val start = entity.offset
        val end = (entity.offset + entity.length).coerceAtMost(text.length)
        if (start in 0 until end) customRanges += start until end
    }
    val unicode = LinkedHashSet<String>()
    for (range in emojiClusters(text)) {
        if (customRanges.any { it.first <= range.first && range.last <= it.last }) continue
        unicode.add(text.substring(range.first, range.last + 1))
        if (unicode.size >= MAX_EMOJI_PER_MESSAGE) break
    }
    return ExtractedEmoji(
        unicode = unicode.take(MAX_EMOJI_PER_MESSAGE),
        customEmojiIds = custom.take(MAX_EMOJI_PER_MESSAGE)
    )
}

/**
 * UTF-16 ranges of emoji grapheme clusters. ZWJ sequences, skin tones, flags and keycaps stay one cluster; ASCII
 * digits, # and * count only as the base of a keycap.
 */
internal fun emojiClusters(text: String): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val ranges = ArrayList<IntRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        if (isEmojiCluster(text.substring(start, end))) ranges += start until end
        start = end
        end = iterator.next()
    }
    return ranges
}

private fun isEmojiCluster(cluster: String): Boolean {
    var index = 0
    while (index < cluster.length) {
        val codePoint = Character.codePointAt(cluster, index)
        if ((codePoint > 0x7F && UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)) ||
            codePoint == 0xFE0F || codePoint == 0x20E3
        ) return true
        index += Character.charCount(codePoint)
    }
    return false
}