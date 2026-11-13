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
    val entities = content.text?.entities ?: return null
    for (entity in entities) {
        val url = (entity?.type as? TdApi.TextEntityTypeTextUrl)?.url?.trim() ?: continue
        if (BgWaveLink.isWaveLink(url)) return url
    }
    return null
}
