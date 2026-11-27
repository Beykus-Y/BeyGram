package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.TextStyleKind
import com.beykus.beygram.ui.screens.bgmusic.isBgMusicDocument

/** Keep media and independently stateful rich text in the timeline until they can be presented without duplicate effects. */
internal fun ChatMessage.supportsContextScene(): Boolean =
    sendState == MessageSendState.Sent && special == null && serviceText == null &&
        expiredMediaKind == null && linkPreview == null && mediaAlbumId == 0L &&
        entities.none { it.kind in ContextSceneStatefulStyles } && reactions.isEmpty() && effectId == 0L && memory == null &&
        inlineKeyboard.isEmpty() && !isViewOnce && selfDestruct == null &&
        (media?.let {
            val passiveKind = when (it.kind) {
                MediaKind.Photo, MediaKind.Video -> it.mimeType != "image/gif"
                MediaKind.Document -> !isBgMusicDocument(it)
                else -> false
            }
            passiveKind && !it.isSecret && !it.isSecretChat && !it.hasSpoiler
        } ?: text.isNotBlank())

private val ContextSceneStatefulStyles = setOf(
    TextStyleKind.Spoiler, TextStyleKind.CustomEmoji, TextStyleKind.Pre, TextStyleKind.Quote
)
