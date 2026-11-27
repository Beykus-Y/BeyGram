package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState

/** Only self-contained text bubbles can be composed in the foreground without duplicating media effects. */
internal fun ChatMessage.supportsContextScene(): Boolean =
    sendState == MessageSendState.Sent && text.isNotBlank() && media == null && special == null &&
        serviceText == null && expiredMediaKind == null && reply == null && linkPreview == null &&
        entities.isEmpty() && reactions.isEmpty() && effectId == 0L && memory == null &&
        inlineKeyboard.isEmpty() && !isForwarded && !isViewOnce && selfDestruct == null
