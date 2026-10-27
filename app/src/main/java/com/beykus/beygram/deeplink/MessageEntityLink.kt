package com.beykus.beygram.deeplink

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TelegramProxyDraft
import com.beykus.beygram.domain.model.TextStyleKind
import com.beykus.beygram.domain.model.normalizedPublicUsername
import com.beykus.beygram.domain.model.validTextEntities
import java.net.URI

/**
 * The action behind one interactive entity of a formatted message.
 *
 * Telegram links are kept separate from ordinary web URLs so the chat can route them through TDLib instead of
 * handing a t.me address to an external application. Anything that is not explicitly classified stays null:
 * a message must never turn arbitrary text into a profile lookup.
 */
sealed interface MessageEntityLink {
    data class PublicChat(val username: String) : MessageEntityLink
    data class User(val userId: Long) : MessageEntityLink
    data class TelegramMessage(val url: String) : MessageEntityLink
    data class TelegramInvite(val url: String) : MessageEntityLink
    data class ExternalUrl(val url: String) : MessageEntityLink
    data class Email(val address: String) : MessageEntityLink
    data class Phone(val number: String) : MessageEntityLink
    /** t.me/+79991234567 and tg://resolve?phone= lookup a Telegram user, not the system dialer. */
    data class TelegramPhone(val phone: String) : MessageEntityLink
    /** tg://proxy or tg://socks; the raw URL remains a safe external fallback when no in-app prompt is available. */
    data class TelegramProxy(val url: String, val draft: TelegramProxyDraft) : MessageEntityLink
}

private val COPYABLE_KINDS = setOf(
    TextStyleKind.Url,
    TextStyleKind.Mention,
    TextStyleKind.Email,
    TextStyleKind.Phone
)

/** Returns the link carried by [entity], or null when the entity is not interactive. Malformed offsets are ignored. */
fun messageEntityLink(entity: MessageTextEntity, text: String): MessageEntityLink? {
    if (entity.offset < 0 || entity.length <= 0 || entity.offset.toLong() + entity.length > text.length) return null
    val value = text.substring(entity.offset, entity.offset + entity.length)
    return when (entity.kind) {
        TextStyleKind.Mention -> normalizedPublicUsername(value)?.let(MessageEntityLink::PublicChat)
        TextStyleKind.MentionName -> entity.argument.toLongOrNull()?.takeIf { it > 0L }?.let(MessageEntityLink::User)
        TextStyleKind.Url -> urlLink(entity.argument.ifBlank { value })
        TextStyleKind.Email -> value.trim().takeIf { it.isNotEmpty() }?.let(MessageEntityLink::Email)
        TextStyleKind.Phone -> value.trim().takeIf { it.isNotEmpty() }?.let(MessageEntityLink::Phone)
        else -> null
    }
}

/**
 * Links that a tap may follow. A link hidden under an unrevealed spoiler is deliberately absent: the action must not
 * expose content the reader has not chosen to reveal yet.
 */
fun messageEntityLinks(text: String, entities: List<MessageTextEntity>): List<MessageEntityLink> {
    val valid = validTextEntities(text, entities)
    val spoilers = valid.filter { it.kind == TextStyleKind.Spoiler }
    return valid
        .asSequence()
        .filter { it.kind in COPYABLE_KINDS || it.kind == TextStyleKind.MentionName }
        .filterNot { entity -> spoilers.any { it.overlaps(entity) } }
        .mapNotNull { messageEntityLink(it, text) }
        .distinct()
        .toList()
}

/**
 * Values offered by the long-press "copy link or username" action. A text link contributes its destination, a mention
 * its @username, so copying never silently falls back to the label of a hidden URL. Spoiler-hidden links are omitted.
 */
fun copyableMessageLinks(text: String, entities: List<MessageTextEntity>): List<String> {
    val valid = validTextEntities(text, entities)
    val spoilers = valid.filter { it.kind == TextStyleKind.Spoiler }
    return valid
        .asSequence()
        .filter { it.kind in COPYABLE_KINDS }
        .filterNot { entity -> spoilers.any { it.overlaps(entity) } }
        .mapNotNull { entity ->
            when (entity.kind) {
                TextStyleKind.Url -> entity.argument.trim().ifEmpty {
                    text.substring(entity.offset, entity.offset + entity.length).trim()
                }
                else -> text.substring(entity.offset, entity.offset + entity.length).trim()
            }.takeIf { it.isNotEmpty() }
        }
        .distinct()
        .toList()
}

private fun MessageTextEntity.overlaps(other: MessageTextEntity): Boolean =
    offset < other.offset + other.length && other.offset < offset + length

private fun urlLink(raw: String): MessageEntityLink? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    // Some senders mark an @username as a URL entity; it is still a profile, never a web host.
    if (value.startsWith("@")) normalizedPublicUsername(value)?.let { return MessageEntityLink.PublicChat(it) }
    telegramLink(value)?.let { return it }
    val normalized = if (!value.contains(":")) "https://$value" else value
    val scheme = runCatching { URI(normalized).scheme?.lowercase() }.getOrNull() ?: return null
    return when (scheme) {
        "http", "https" -> MessageEntityLink.ExternalUrl(normalized)
        "mailto" -> normalized.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }?.let(MessageEntityLink::Email)
        "tel" -> normalized.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }?.let(MessageEntityLink::Phone)
        else -> null
    }
}

/** t.me and telegram.me are normalized to https, the only form the fail-closed deep-link parser accepts. */
private fun telegramLink(raw: String): MessageEntityLink? {
    val normalized = when {
        raw.startsWith("tg://", ignoreCase = true) -> raw
        raw.startsWith("https://t.me/", ignoreCase = true) ||
            raw.startsWith("https://telegram.me/", ignoreCase = true) -> raw
        raw.startsWith("http://t.me/", ignoreCase = true) ||
            raw.startsWith("http://telegram.me/", ignoreCase = true) -> "https://${raw.substringAfter("://")}"
        raw.startsWith("t.me/", ignoreCase = true) ||
            raw.startsWith("telegram.me/", ignoreCase = true) -> "https://$raw"
        else -> return null
    }
    return when (val route = TelegramDeepLinkRouter.route(TelegramDeepLinkParser.parse(normalized))) {
        is TelegramDeepLinkRoute.Chat -> MessageEntityLink.PublicChat(route.username)
        is TelegramDeepLinkRoute.UserId -> MessageEntityLink.User(route.userId)
        is TelegramDeepLinkRoute.Message -> MessageEntityLink.TelegramMessage(route.url)
        is TelegramDeepLinkRoute.Invite -> MessageEntityLink.TelegramInvite(route.url)
        is TelegramDeepLinkRoute.Phone -> MessageEntityLink.TelegramPhone(route.phone)
        is TelegramDeepLinkRoute.Proxy -> MessageEntityLink.TelegramProxy(normalized, route.draft)
        TelegramDeepLinkRoute.Ignore -> null
    }
}
