package com.beykus.beygram.ui.screens.stories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryMediaKind
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen viewer for expired stories. Telegram cannot return an archived story to the active
 * list; the screen offers the supported alternative, keeping it accessible in the profile, plus
 * deletion and editing of the fields TDLib allows.
 */
@Composable
internal fun StoryArchiveViewer(
    initialStories: List<StoryDetail>,
    initialStoryId: Int,
    repository: TelegramRepository,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stories by remember { mutableStateOf(initialStories) }
    var index by remember {
        mutableIntStateOf(initialStories.indexOfFirst { it.storyId == initialStoryId }.takeIf { it >= 0 } ?: 0)
    }
    val story = stories.getOrNull(index)
    var playing by remember(story?.storyId) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var editSaving by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var editCaptionLimit by remember { mutableIntStateOf(1_024) }
    var showDelete by remember { mutableStateOf(false) }

    val media = story?.media
    val emptyFile = remember { MutableStateFlow(MediaFileState(0)) }
    val fullFile by remember(media?.fileId) { media?.fileId?.let(repository::observeFile) ?: emptyFile }
        .collectAsStateWithLifecycle()
    val thumbnailFile by remember(media?.thumbnailFileId, media?.fileId) {
        (media?.thumbnailFileId ?: media?.fileId)?.let(repository::observeFile) ?: emptyFile
    }.collectAsStateWithLifecycle()
    val downloaded = remember { mutableSetOf<Int>() }
    DisposableEffect(Unit) {
        onDispose { downloaded.toList().forEach(repository::cancelFileDownload) }
    }
    LaunchedEffect(media?.fileId) {
        val ids = listOfNotNull(media?.fileId, media?.thumbnailFileId).distinct()
        (downloaded - ids.toSet()).forEach(repository::cancelFileDownload)
        downloaded.clear()
        ids.forEach {
            downloaded += it
            repository.downloadFile(it)
        }
    }
    LaunchedEffect(showEdit) {
        if (showEdit) {
            editError = null
            editCaptionLimit = repository.storyCaptionLimit().getOrDefault(1_024).coerceAtLeast(1)
        }
    }
    val date = remember(story?.dateSeconds) {
        story?.dateSeconds?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000L))
        }.orEmpty()
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            story == null -> Text(stringResource(R.string.stories_unavailable), color = Color.White)
            media == null -> Text(
                stringResource(R.string.stories_media_unsupported),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp
            )
            media.kind == StoryMediaKind.Photo -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StoryPhoto(fullFile, thumbnailFile, media.minithumbnail)
                if (fullFile.error != null && fullFile.localPath == null) {
                    StoryDownloadError(fullFile) { media?.fileId?.let(repository::downloadFile) }
                }
            }
            else -> {
                val path = fullFile.localPath
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (path == null) StoryPhoto(fullFile, thumbnailFile, media.minithumbnail)
                    else StoryVideo(
                        path = path,
                        playing = playing,
                        onFailure = { playing = false; error = context.getString(R.string.stories_media_unsupported) },
                        onEnded = { playing = false }
                    )
                    if (fullFile.error != null && fullFile.localPath == null) {
                        StoryDownloadError(fullFile) { media?.fileId?.let(repository::downloadFile) }
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.stories_archive_viewer),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (date.isNotBlank()) Text(date, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.stories_close), tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            if (story != null) {
                val privacyLabel = story.privacy?.label()
                val meta = listOfNotNull(
                    stringResource(R.string.stories_archive_views, story.viewCount),
                    stringResource(R.string.stories_archive_reactions, story.reactionCount),
                    privacyLabel
                ).joinToString(" · ")
                Text(
                    meta,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
                if (story.caption.isNotBlank()) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(story.caption, color = Color.White, fontSize = 16.sp, lineHeight = 21.sp)
                    }
                }
                if (story.canToggleIsPostedToChatPage) {
                    Text(
                        if (story.isPostedToChatPage) stringResource(R.string.stories_archive_keep_profile)
                        else stringResource(R.string.stories_archive_restore_unavailable),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )
                } else if (!story.isPostedToChatPage) {
                    Text(
                        stringResource(R.string.stories_archive_restore_unsupported),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.stories_previous), tint = Color.White)
                    }
                    IconButton(onClick = { index++ }, enabled = index < stories.lastIndex, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipNext, stringResource(R.string.stories_next), tint = Color.White)
                    }
                    if (media?.kind == StoryMediaKind.Video && fullFile.localPath != null) {
                        IconButton(onClick = { playing = !playing }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                stringResource(if (playing) R.string.stories_pause else R.string.stories_resume),
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (story.canBeEdited || story.canSetPrivacySettings) {
                        IconButton(enabled = !busy, onClick = { showEdit = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Edit, stringResource(R.string.stories_edit), tint = Color.White)
                        }
                    }
                    if (story.canToggleIsPostedToChatPage) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val target = !story.isPostedToChatPage
                                busy = true
                                error = null
                                scope.launch {
                                    repository.setStoryPostedToChatPage(story.posterChatId, story.storyId, target)
                                        .onSuccess {
                                            stories = stories.map {
                                                if (it.storyId == story.storyId) it.copy(isPostedToChatPage = target) else it
                                            }
                                        }
                                        .onFailure { error = it.userMessage(context, R.string.stories_edit_failed) }
                                    busy = false
                                }
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (story.isPostedToChatPage) R.string.stories_archive_remove_profile
                                    else R.string.stories_archive_keep_profile
                                ),
                                color = Color.White
                            )
                        }
                    }
                    if (story.canBeDeleted) {
                        IconButton(enabled = !busy, onClick = { showDelete = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.stories_delete), tint = Color.White)
                        }
                    }
                }
            }
            error?.let {
                Text(
                    it,
                    color = Color(0xFFFFB4AB),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }
        if (busy) CircularProgressIndicator(color = Color.White)
    }

    if (showEdit && story != null) {
        StoryEditDialog(
            initialCaption = story.caption,
            captionLimit = editCaptionLimit,
            initialPrivacy = story.privacy,
            initialSelectedUserIds = story.privacyUserIds,
            canEditCaption = story.canBeEdited,
            canEditPrivacy = story.canSetPrivacySettings,
            saving = editSaving,
            error = editError,
            repository = repository,
            onDismiss = { if (!editSaving) showEdit = false },
            onSave = { caption, privacy, ids ->
                scope.launch {
                    editSaving = true
                    editError = null
                    var failure: Throwable? = null
                    if (story.canBeEdited && caption != story.caption) {
                        repository.editStoryCaption(story.posterChatId, story.storyId, caption)
                            .onFailure { failure = it }
                    }
                    if (failure == null && privacy != null && story.canSetPrivacySettings &&
                        (privacy != story.privacy || ids.toSet() != story.privacyUserIds.toSet())
                    ) {
                        repository.setStoryPrivacy(story.storyId, privacy, ids)
                            .onFailure { failure = it }
                    }
                    editSaving = false
                    val failed = failure
                    if (failed != null) {
                        editError = failed.userMessage(context, R.string.stories_edit_failed)
                    } else {
                        stories = stories.map {
                            if (it.storyId == story.storyId)
                                it.copy(
                                    caption = if (story.canBeEdited) caption else it.caption,
                                    privacy = privacy ?: it.privacy,
                                    privacyUserIds = if (privacy != null) ids else it.privacyUserIds
                                )
                            else it
                        }
                        showEdit = false
                    }
                }
            }
        )
    }

    if (showDelete && story != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDelete = false },
            title = { Text(stringResource(R.string.stories_delete)) },
            text = { Text(stringResource(R.string.stories_delete_confirm)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    showDelete = false
                    busy = true
                    error = null
                    scope.launch {
                        repository.deleteStory(story.posterChatId, story.storyId)
                            .onSuccess {
                                val remaining = stories.filterNot { it.storyId == story.storyId }
                                busy = false
                                if (remaining.isEmpty()) {
                                    onClose()
                                } else {
                                    stories = remaining
                                    index = index.coerceIn(0, remaining.lastIndex)
                                }
                            }
                            .onFailure {
                                busy = false
                                error = it.userMessage(context, R.string.stories_delete_failed)
                            }
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

