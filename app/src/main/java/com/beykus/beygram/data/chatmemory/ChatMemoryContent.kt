package com.beykus.beygram.data.chatmemory

import org.drinkless.tdlib.TdApi

/**
 * The text Chat Memory keeps for a message's content. A service event has no text worth keeping and returns null.
 * Text messages keep their plain text; media keeps its caption and sets [MemorySnapshot.hasAttachment].
 *
 * [isService] comes from the repository's service-message mapper, which already knows every TDLib service kind.
 */
internal fun memorySnapshotOf(content: TdApi.MessageContent, isService: Boolean): MemorySnapshot? {
    if (isService) return null
    return when (content) {
        is TdApi.MessageText -> MemorySnapshot(text = content.text?.text.orEmpty(), hasAttachment = false)
        else -> MemorySnapshot(text = captionOf(content), hasAttachment = true)
    }
}

private fun captionOf(content: TdApi.MessageContent): String = when (content) {
    is TdApi.MessagePhoto -> content.caption?.text
    is TdApi.MessageVideo -> content.caption?.text
    is TdApi.MessageAnimation -> content.caption?.text
    is TdApi.MessageAudio -> content.caption?.text
    is TdApi.MessageDocument -> content.caption?.text
    is TdApi.MessageVoiceNote -> content.caption?.text
    else -> null
}.orEmpty()
