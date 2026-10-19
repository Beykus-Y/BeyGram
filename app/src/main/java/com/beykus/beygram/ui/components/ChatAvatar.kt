package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.flow.StateFlow

/** Значок «Избранное» вместо фото и инициалов. */
@Composable
fun SavedMessagesAvatar(size: Dp, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Box(modifier.size(size).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ChatAvatar(
    chat: ChatPreview, size: Dp,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chat.isSavedMessages) SavedMessagesAvatar(size, modifier)
    else TelegramAvatar(chat.initials, Color(chat.avatarColorArgb), size, chat.photo, observeFile, downloadFile, modifier)
}
