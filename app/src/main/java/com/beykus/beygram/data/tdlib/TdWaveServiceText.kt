package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.drinkless.tdlib.TdApi

/**
 * Builds the visible service line of a BG Wave invite: [label] is shown as a hidden text link
 * to [link]. The label comes from string resources at the UI layer; the link is opaque here.
 * The wire payload is produced by the shared BG-Media codec (issue #799), not by this file.
 */
internal fun waveServiceFormattedText(label: String, link: String): TdApi.FormattedText =
    formattedText(label, waveServiceEntities(label, link))

/** The single TextUrl entity (UTF-16 length of the whole [label]) that makes the label a hidden link to [link]. */
internal fun waveServiceEntities(label: String, link: String): List<MessageTextEntity> {
    require(label.isNotEmpty()) { "wave service label must not be empty" }
    require(link.isNotEmpty() && !link.any { it.isWhitespace() }) { "wave link must be a single token" }
    return listOf(MessageTextEntity(0, label.length, TextStyleKind.Url, link))
}
