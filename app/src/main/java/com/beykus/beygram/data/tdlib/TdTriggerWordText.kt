package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi

/** The text trigger words are matched against: the message text, or the caption of a media message. */
internal fun triggerWordText(content: TdApi.MessageContent): String? {
    val text = when (content) {
        is TdApi.MessageText -> content.text?.text
        is TdApi.MessagePhoto -> content.caption?.text
        is TdApi.MessageVideo -> content.caption?.text
        is TdApi.MessageAnimation -> content.caption?.text
        is TdApi.MessageAudio -> content.caption?.text
        is TdApi.MessageDocument -> content.caption?.text
        is TdApi.MessageVoiceNote -> content.caption?.text
        else -> null
    }
    return text?.takeIf { it.isNotBlank() }
}
