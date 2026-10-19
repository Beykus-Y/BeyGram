package com.beykus.beygram.ui.screens.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.ProfilePhotoMedia
import com.beykus.beygram.domain.model.ProfilePhotosPage
import com.beykus.beygram.domain.model.ProfilePhotosWindow
import com.beykus.beygram.ui.screens.chat.MediaViewer
import com.beykus.beygram.ui.screens.chat.MediaViewerActions
import com.beykus.beygram.ui.screens.chat.MediaViewerItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PHOTOS_PAGE_SIZE = 24
private const val PHOTOS_PREFETCH_DISTANCE = 2

/** Photos use TDLib photo IDs; they are never treated as chat messages. */
private fun ProfilePhotoMedia.viewerItem(title: String): MediaViewerItem {
    val animated = animationFileId != null
    return MediaViewerItem(
        messageId = photoId,
        media = MessageMedia(
            kind = if (animated) MediaKind.Animation else MediaKind.Photo,
            fileId = fileId,
            thumbnailFileId = if (animated) bigFileId else smallFileId,
            fileName = title,
            mimeType = if (animated) "video/mp4" else "image/jpeg"
        )
    )
}

/** Window is created only while the fullscreen gallery is open; no history is loaded for a normal profile visit. */
@Composable
internal fun ProfilePhotoGallery(
    profileId: Long,
    avatar: AvatarPhoto,
    own: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    loadPhotos: suspend (Long, Int, Int) -> Result<ProfilePhotosPage>,
    deletePhoto: suspend (Long) -> Result<Unit>,
    onDeleted: () -> Unit,
    onClose: () -> Unit
) {
    val title = stringResource(R.string.profile_photo_title)
    // A negative fallback key is stable for older/group avatars without a known photo ID.
    val currentId = avatar.photoId.takeIf { it > 0L } ?: -avatar.bigFileId.toLong()
    val initial = remember(currentId, avatar.smallFileId, avatar.bigFileId) {
        ProfilePhotosWindow(
            photos = listOf(ProfilePhotoMedia(currentId, avatar.smallFileId, avatar.bigFileId)),
            totalCount = 1
        )
    }
    var window by remember(profileId, currentId) { mutableStateOf(initial) }
    var selectedId by rememberSaveable(profileId, currentId) { mutableLongStateOf(currentId) }
    var loading by remember(profileId, currentId) { mutableStateOf(false) }
    var error by remember(profileId, currentId) { mutableStateOf<String?>(null) }
    var retry by remember(profileId, currentId) { mutableIntStateOf(0) }
    var deleteTarget by remember(profileId, currentId) { mutableStateOf<Long?>(null) }
    var deleting by remember(profileId, currentId) { mutableStateOf(false) }
    val latestLoader by rememberUpdatedState(loadPhotos)
    val scope = rememberCoroutineScope()

    // One request at a time. Offset is based on raw TDLib items, not number of visible pages.
    // When the active profile changes this effect is cancelled, so old responses cannot enter the new gallery.
    LaunchedEffect(profileId, currentId, window.offset, selectedId, retry) {
        val selectedIndex = window.photos.indexOfFirst { it.photoId == selectedId }.coerceAtLeast(0)
        if (loading || error != null || !window.hasMore || selectedIndex < window.photos.size - 1 - PHOTOS_PREFETCH_DISTANCE) return@LaunchedEffect
        loading = true
        try {
            latestLoader(profileId, window.offset, PHOTOS_PAGE_SIZE).fold(
                onSuccess = { window = window.append(it) },
                onFailure = { error = it.localizedMessage ?: it.message ?: "" }
            )
        } finally {
            loading = false
        }
    }

    val pages = window.photos.map { it.viewerItem(title) }
    val index = window.photos.indexOfFirst { it.photoId == selectedId }.coerceAtLeast(0)
    val subtitle = stringResource(R.string.profile_gallery_position, index + 1, window.totalCount.coerceAtLeast(window.photos.size))
    MediaViewer(
        pages = pages,
        selectedId = selectedId,
        onSelect = { selectedId = it },
        observeFile = observeFile,
        downloadFile = downloadFile,
        onViewed = {},
        onClose = onClose,
        title = title,
        subtitle = subtitle,
        fullFileOnlyWhenActive = true,
        actions = if (own && selectedId > 0L) MediaViewerActions(
            canForward = false, canEdit = false, canDelete = true,
            onForward = {}, onEdit = {}, onDelete = { deleteTarget = selectedId }
        ) else null,
        extraFooter = {
            if (loading) LinearProgressIndicator()
            error?.let {
                TextButton(onClick = { error = null; retry++ }) {
                    Text(stringResource(R.string.profile_gallery_retry))
                }
            }
        }
    )
    if (deleteTarget != null) AlertDialog(
        onDismissRequest = { if (!deleting) deleteTarget = null },
        title = { Text(stringResource(R.string.profile_gallery_delete_title)) },
        text = { Text(stringResource(R.string.profile_gallery_delete_warning)) },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = {
                val id = deleteTarget ?: return@TextButton
                scope.launch {
                    deleting = true
                    try {
                        deletePhoto(id).fold(
                            onSuccess = { deleteTarget = null; onDeleted(); onClose() },
                            onFailure = { error = it.localizedMessage ?: it.message ?: ""; deleteTarget = null }
                        )
                    } finally { deleting = false }
                }
            }) {
                if (deleting) CircularProgressIndicator()
                else Text(stringResource(R.string.common_delete))
            }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } }
    )
}
