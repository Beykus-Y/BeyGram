package com.beykus.beygram.ui.screens.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.ProfilePhotoMedia
import com.beykus.beygram.domain.model.ProfilePhotosPage
import com.beykus.beygram.domain.model.ProfilePhotosWindow
import com.beykus.beygram.ui.screens.chat.MediaViewer
import com.beykus.beygram.ui.screens.chat.MediaViewerActions
import com.beykus.beygram.ui.screens.chat.MediaViewerItem
import com.beykus.beygram.ui.text.userMessage
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
    setAsMain: suspend (Long) -> Result<Unit>,
    onChanged: () -> Unit,
    onClose: () -> Unit,
    /** Repository-backed automatic download; keeps gallery preview/full requests cancellable by policy. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null
) {
    val context = LocalContext.current
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
    var settingMain by remember(profileId, currentId) { mutableStateOf(false) }
    val latestLoader by rememberUpdatedState(loadPhotos)
    val scope = rememberCoroutineScope()

    // One sequential request loop per selected avatar. Incrementing the raw TDLib offset does not
    // launch a competing coroutine; switching profile or photo cancels the previous request.
    LaunchedEffect(profileId, currentId, selectedId, retry) {
        while (window.hasMore && error == null) {
            val selectedIndex = window.photos.indexOfFirst { it.photoId == selectedId }.coerceAtLeast(0)
            if (selectedIndex < window.photos.size - 1 - PHOTOS_PREFETCH_DISTANCE) break
            loading = true
            try {
                latestLoader(profileId, window.offset, PHOTOS_PAGE_SIZE).fold(
                    onSuccess = { window = window.append(it) },
                    onFailure = { error = it.userMessage(context, R.string.feature_action_failed) }
                )
            } finally {
                loading = false
            }
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
        autoDownloadFile = autoDownloadFile,
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
            if (own && selectedId > 0L && selectedId != currentId) {
                TextButton(enabled = !settingMain, onClick = {
                    scope.launch {
                        settingMain = true
                        try {
                            setAsMain(selectedId).fold(
                                onSuccess = { onChanged(); onClose() },
                                onFailure = { error = it.userMessage(context, R.string.feature_action_failed) }
                            )
                        } finally { settingMain = false }
                    }
                }) {
                    if (settingMain) CircularProgressIndicator()
                    else Text(stringResource(R.string.profile_gallery_set_main))
                }
            }
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
                            onSuccess = { deleteTarget = null; onChanged(); onClose() },
                            onFailure = { error = it.userMessage(context, R.string.feature_action_failed); deleteTarget = null }
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
