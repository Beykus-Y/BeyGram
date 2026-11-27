package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.TextStyleKind

/** Keep media and independently stateful rich text in the timeline until they can be presented without duplicate effects. */
internal fun ChatMessage.supportsContextScene(): Boolean =
    sendState == MessageSendState.Sent && text.isNotBlank() && media == null && special == null &&
        serviceText == null && expiredMediaKind == null && reply == null && linkPreview == null &&
        entities.none { it.kind in ContextSceneStatefulStyles } && reactions.isEmpty() && effectId == 0L && memory == null &&
        inlineKeyboard.isEmpty() && !isForwarded && !isViewOnce && selfDestruct == null

private val ContextSceneStatefulStyles = setOf(
    TextStyleKind.Spoiler, TextStyleKind.CustomEmoji, TextStyleKind.Pre, TextStyleKind.Quote
)
