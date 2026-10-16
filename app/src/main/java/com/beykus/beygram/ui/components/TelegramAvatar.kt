package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TelegramAvatar(
    initials: String, color: Color, size: Dp, photo: AvatarPhoto?,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(size).clip(CircleShape).background(color)) {
        Avatar(initials, color, size)
        photo?.let {
            val file by remember(it.smallFileId) { observeFile(it.smallFileId) }.collectAsStateWithLifecycle()
            LaunchedEffect(it.smallFileId) { downloadFile(it.smallFileId) }
            file.localPath?.let { path -> LocalMediaImage(path, Modifier.size(size)) }
        }
    }
}
