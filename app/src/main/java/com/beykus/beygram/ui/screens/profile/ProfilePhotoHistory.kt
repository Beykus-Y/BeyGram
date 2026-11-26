package com.beykus.beygram.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.ProfilePhotoMedia
import com.beykus.beygram.domain.model.ProfilePhotosPage
import com.beykus.beygram.domain.model.ProfilePhotosWindow
import com.beykus.beygram.ui.text.userMessage

private const val PhotosPageSize = 24
private const val PhotosPrefetchDistance = 2

internal class ProfilePhotoHistory(avatar: AvatarPhoto, selection: MutableLongState) {
    private var requestedId by selection
    var window by mutableStateOf(
        ProfilePhotosWindow(
            photos = listOf(ProfilePhotoMedia(avatar.galleryId(), avatar.smallFileId, avatar.bigFileId)),
            totalCount = 1
        )
    )
        private set
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var retry by mutableIntStateOf(0)
        private set

    val currentId: Long get() = window.photos.first().photoId
    val selectedIndex: Int get() = window.photos.indexOfFirst { it.photoId == requestedId }.coerceAtLeast(0)
    val selected: ProfilePhotoMedia get() = window.photos[selectedIndex]
    val totalCount: Int get() = window.totalCount.coerceAtLeast(window.photos.size)
    val requestId: Long get() = requestedId

    fun select(id: Long) {
        requestedId = id
    }

    fun move(offset: Int): Boolean {
        val target = window.photos.getOrNull(selectedIndex + offset) ?: return false
        select(target.photoId)
        return true
    }

    fun append(page: ProfilePhotosPage) {
        val wasCurrent = requestedId == currentId
        window = window.append(page)
        if (wasCurrent || !window.hasMore && window.photos.none { it.photoId == requestedId }) {
            select(currentId)
        }
    }

    fun retryLoad() {
        error = null
        retry++
    }
}

/** The same sequential history window drives the inline scene and the existing fullscreen viewer. */
@Composable
internal fun rememberProfilePhotoHistory(
    profileId: Long,
    avatar: AvatarPhoto,
    active: Boolean,
    loadPhotos: suspend (Long, Int, Int) -> Result<ProfilePhotosPage>
): ProfilePhotoHistory {
    val context = LocalContext.current
    val currentId = avatar.galleryId()
    val selection = rememberSaveable(profileId, currentId) { mutableLongStateOf(currentId) }
    val history = remember(profileId, avatar, selection) { ProfilePhotoHistory(avatar, selection) }
    val latestLoader by rememberUpdatedState(loadPhotos)
    LaunchedEffect(history, active, history.requestId, history.retry) {
        if (!active) return@LaunchedEffect
        while (
            history.window.hasMore &&
            history.error == null &&
            (history.requestId != history.selected.photoId ||
                history.selectedIndex >= history.window.photos.lastIndex - PhotosPrefetchDistance)
        ) {
            history.loading = true
            try {
                latestLoader(profileId, history.window.offset, PhotosPageSize).fold(
                    onSuccess = history::append,
                    onFailure = { history.error = it.userMessage(context, R.string.feature_action_failed) }
                )
            } finally {
                history.loading = false
            }
        }
    }
    return history
}

private fun AvatarPhoto.galleryId(): Long = photoId.takeIf { it > 0L } ?: -bigFileId.toLong()
