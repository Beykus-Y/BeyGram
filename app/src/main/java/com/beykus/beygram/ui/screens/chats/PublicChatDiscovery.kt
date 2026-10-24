package com.beykus.beygram.ui.screens.chats

import com.beykus.beygram.deeplink.TelegramDeepLinkParser
import com.beykus.beygram.deeplink.TelegramDeepLinkRoute
import com.beykus.beygram.deeplink.TelegramDeepLinkRouter

/** A public search or invite target accepted by the discovery field. */
internal sealed interface PublicDiscoveryTarget {
    data class Username(val username: String) : PublicDiscoveryTarget
    data class Invite(val url: String) : PublicDiscoveryTarget
}

private val PLAIN_USERNAME = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
private val HTTPS_HOST = Regex("(?i)^(?:t\\.me|telegram\\.me)/")

/**
 * Normalizes what a user pastes into the discovery field: a plain @username, a t.me URL with or without
 * the scheme, or a tg:// link. Only username/bot and invite targets are returned; message/phone/proxy
 * links belong to the external deep-link router and are not silently joined from this screen.
 */
internal fun publicDiscoveryTarget(raw: String): PublicDiscoveryTarget? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = when {
        trimmed.startsWith("@") -> "https://t.me/${trimmed.removePrefix("@")}"
        PLAIN_USERNAME.matches(trimmed) -> "https://t.me/$trimmed"
        HTTPS_HOST.containsMatchIn(trimmed) -> "https://$trimmed"
        else -> trimmed
    }
    return when (val route = TelegramDeepLinkRouter.route(TelegramDeepLinkParser.parse(normalized))) {
        is TelegramDeepLinkRoute.Chat -> PublicDiscoveryTarget.Username(route.username)
        is TelegramDeepLinkRoute.Invite -> PublicDiscoveryTarget.Invite(route.url)
        else -> null
    }
}
