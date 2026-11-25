package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.*
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
import com.beykus.beygram.ui.screens.chat.MediaViewer
import com.beykus.beygram.ui.screens.chat.MediaViewerActions
import com.beykus.beygram.ui.screens.chat.MediaViewerItem
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    sharedHistory: ProfilePhotoHistory? = null
) {
    val context = LocalContext.current
    val title = stringResource(R.string.profile_photo_title)
    val history = sharedHistory ?: rememberProfilePhotoHistory(profileId, avatar, active = true, loadPhotos)
    val selectedId = history.selected.photoId
    var deleteTarget by remember(history) { mutableStateOf<Long?>(null) }
    var deleting by remember(history) { mutableStateOf(false) }
    var settingMain by remember(history) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pages = history.window.photos.map { it.viewerItem(title) }
    val subtitle = if (sharedHistory == null || history.totalCount > pages.size) {
        stringResource(R.string.profile_gallery_position, history.selectedIndex + 1, history.totalCount)
    } else null
    MediaViewer(
        pages = pages,
        selectedId = selectedId,
        onSelect = history::select,
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
            if (own && selectedId > 0L && selectedId != history.currentId) {
                TextButton(enabled = !settingMain, onClick = {
                    scope.launch {
                        settingMain = true
                        try {
                            setAsMain(selectedId).fold(
                                onSuccess = { onChanged(); onClose() },
                                onFailure = { history.error = it.userMessage(context, R.string.feature_action_failed) }
                            )
                        } finally { settingMain = false }
                    }
                }) {
                    if (settingMain) CircularProgressIndicator()
                    else Text(stringResource(R.string.profile_gallery_set_main))
                }
            }
            if (history.loading) LinearProgressIndicator()
            if (history.error != null) {
                TextButton(onClick = history::retryLoad) {
                    Text(stringResource(R.string.profile_gallery_retry))
                }
            }
        }
    )
    if (deleteTarget != null) {
        val deletingBar: @Composable () -> Unit = { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        BeyGramDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            title = stringResource(R.string.profile_gallery_delete_title),
            message = stringResource(R.string.profile_gallery_delete_warning),
            content = if (deleting) deletingBar else null,
            confirmText = stringResource(R.string.common_delete),
            confirmEnabled = !deleting,
            onConfirm = {
                val id = deleteTarget ?: return@BeyGramDialog
                scope.launch {
                    deleting = true
                    try {
                        deletePhoto(id).fold(
                            onSuccess = { deleteTarget = null; onChanged(); onClose() },
                            onFailure = { history.error = it.userMessage(context, R.string.feature_action_failed); deleteTarget = null }
                        )
                    } finally { deleting = false }
                }
            },
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !deleting,
            onDismiss = { deleteTarget = null }
        )
    }
}
