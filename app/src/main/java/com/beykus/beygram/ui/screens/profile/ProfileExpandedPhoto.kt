package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import kotlinx.coroutines.flow.StateFlow

/** Side of the revealed square, in dp. Fits a 320 dp phone content width and stays bounded in landscape. */
private val PhotoSide = 260.dp

/**
 * Profile 2.0 C, step C1: the current profile photo shown at full header width. A tap on the avatar
 * reveals it here; a tap on the revealed photo opens the existing full-screen gallery (#1138).
 * Uses the same big-file download path as the gallery, so no new loader is introduced.
 */
@Composable
internal fun ProfileExpandedPhoto(
    photo: AvatarPhoto,
    color: Color,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val file by remember(photo.bigFileId) { observeFile(photo.bigFileId) }.collectAsStateWithLifecycle()
    LaunchedEffect(photo.bigFileId) { downloadFile(photo.bigFileId) }
    Box(
        // Fixed square so it fits a 320 dp phone and stays bounded in landscape, where the page scrolls.
        modifier
            .size(PhotoSide)
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .clickable(role = Role.Button) { onOpenGallery() }
            .semantics {
                contentDescription = context.getString(R.string.a11y_profile_photo_open)
                stateDescription = context.getString(R.string.a11y_profile_photo_expanded_state)
            }
    ) {
        file.localPath?.let { path -> LocalMediaImage(path, Modifier.fillMaxSize()) }
    }
}
