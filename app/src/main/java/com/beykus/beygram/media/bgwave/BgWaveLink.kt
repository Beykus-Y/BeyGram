package com.beykus.beygram.media.bgwave

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import com.beykus.beygram.domain.model.validTextEntities

/**
 * The two wrappers of one BG Wave payload (issue #799). Only the wrapper changes here; the bytes inside are produced and
 * read by the BG-Media codec, never by this file.
 */
object BgWaveLink {
    const val URI_PREFIX = "bg://wave/v1/"
    const val HTTPS_PREFIX = "https://beygram.beykus.fun/#bgwave-v1."

    /** A link longer than this is not a valid invitation; it is dropped before any decoding. */
    const val MAX_LINK_CHARS = 8192

    /** The Telegram-safe https wrapper of [uri]; an https link is returned as is, anything else is refused. */
    fun toHttps(uri: String): String? = when {
        uri.length > MAX_LINK_CHARS -> null
        uri.startsWith(HTTPS_PREFIX) -> uri
        uri.startsWith(URI_PREFIX) -> HTTPS_PREFIX + uri.removePrefix(URI_PREFIX)
        else -> null
    }

    fun isWaveLink(candidate: String): Boolean =
        candidate.length <= MAX_LINK_CHARS && (candidate.startsWith(HTTPS_PREFIX) || candidate.startsWith(URI_PREFIX))

    /**
     * The first wave link hidden behind a text link of a message, or null for an ordinary message. A link that is only
     * written as visible text is ignored: invitations are always sent as hidden service links.
     */
    fun find(text: String, entities: List<MessageTextEntity>): String? =
        validTextEntities(text, entities)
            .asSequence()
            .filter { it.kind == TextStyleKind.Url }
            .map { it.argument.trim() }
            .firstOrNull { isWaveLink(it) }
}
