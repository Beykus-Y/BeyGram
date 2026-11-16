package com.beykus.beygram.data.tdlib

import com.beykus.beygram.media.bgwave.BgWaveLink
import org.drinkless.tdlib.TdApi

/**
 * The BG Wave link hidden in a received text message, or null for any other message. Only a plain text message with a
 * text-link entity counts, and a forwarded message never does: an invitation must come from the person who sent it.
 */
internal fun waveLinkOf(message: TdApi.Message): String? {
    if (message.forwardInfo != null) return null
    val content = message.content as? TdApi.MessageText ?: return null
    val formatted = content.text ?: return null
    val entity = formatted.entities?.singleOrNull() ?: return null
    val text = formatted.text ?: return null
    if (text.isEmpty() || entity.offset != 0 || entity.length != text.length) return null
    val url = (entity.type as? TdApi.TextEntityTypeTextUrl)?.url ?: return null
    return url.takeIf(BgWaveLink::isWaveLink)
}
