package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.TextStyleKind

/**
 * Single messages eligible for the foreground context scene.
 * Secret, view-once, self-destruct, spoiler-text/media, effect, expired, service, and album rows stay on the fallback path.
 */
internal fun ChatMessage.supportsContextScene(): Boolean =
    sendState == MessageSendState.Sent &&
        serviceText == null &&
        expiredMediaKind == null &&
        mediaAlbumId == 0L &&
        entities.none { it.kind == TextStyleKind.Spoiler } &&
        effectId == 0L &&
        !isViewOnce &&
        selfDestruct == null &&
        (media?.let {
            !it.isSecret && !it.isSecretChat && !it.hasSpoiler
        } ?: (text.isNotBlank() || special != null))
