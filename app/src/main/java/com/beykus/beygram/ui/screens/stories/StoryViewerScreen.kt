package com.beykus.beygram.ui.screens.stories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryMediaKind
import com.beykus.beygram.domain.model.StoryPreview
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen story viewer. It loads the current story and neighbor previews only, pauses while the
 * user holds the screen, and reports OpenStory/CloseStory to TDLib so the unread marker is real.
 */
@Composable
fun StoryViewerScreen(
    repository: TelegramRepository,
    posterChatId: Long,
    initialStoryId: Int,
    onClose: () -> Unit
) {
    SecureStoryWindow()
    BackHandler { onClose() }

    val bars by repository.activeStories.collectAsStateWithLifecycle()
    var snapshot by remember(posterChatId) { mutableStateOf<ChatStories?>(null) }
    var missing by remember(posterChatId) { mutableStateOf(false) }
    LaunchedEffect(posterChatId, bars) {
        val entry = bars.firstOrNull { it.chatId == posterChatId }
        if (entry != null) {
            snapshot = entry
        } else if (snapshot != null) {
            // The sender left the active story list (all stories expired/deleted or blocked).
            onClose()
        }
    }
    LaunchedEffect(posterChatId) {
        repository.refreshActiveStories()
        delay(8_000)
        if (snapshot == null) missing = true
    }

    val stories = snapshot?.stories.orEmpty()
    val storyIds = stories.map { it.storyId }
    var index by remember(posterChatId) { mutableIntStateOf(-1) }
    var visibleStoryId by remember(posterChatId) { mutableIntStateOf(0) }
    LaunchedEffect(storyIds, initialStoryId) {
        if (stories.isEmpty()) return@LaunchedEffect
        if (visibleStoryId > 0) {
            val resolved = storyIndexAfterUpdate(stories, index, visibleStoryId)
            if (resolved < 0) {
                onClose()
                return@LaunchedEffect
            }
            index = resolved
            visibleStoryId = stories[resolved].storyId
        } else {
            val target = if (index !in stories.indices) {
                storyInitialIndex(stories, snapshot?.maxReadStoryId ?: 0, initialStoryId)
            } else {
                index.coerceIn(0, stories.lastIndex)
            }
            index = target
            visibleStoryId = stories[target].storyId
        }
    }
    LaunchedEffect(index) {
        if (index in stories.indices) visibleStoryId = stories[index].storyId
    }
    val current = stories.getOrNull(index)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember(posterChatId, current?.storyId) { mutableStateOf<StoryDetail?>(null) }
    var loading by remember(posterChatId, current?.storyId) { mutableStateOf(false) }
    var loadError by remember(posterChatId, current?.storyId) { mutableStateOf<String?>(null) }
    var retry by remember(posterChatId, current?.storyId) { mutableIntStateOf(0) }
    LaunchedEffect(posterChatId, current?.storyId, retry) {
        val storyId = current?.storyId ?: return@LaunchedEffect
        loading = true
        loadError = null
        val result = repository.loadStory(posterChatId, storyId)
        loading = false
        result.onSuccess { detail = it }
            .onFailure { loadError = it.userMessage(context, R.string.stories_unavailable) }
    }

    // The current story is open only after its content loads; leaving it sends CloseStory.
    LaunchedEffect(posterChatId, current?.storyId, detail != null) {
        val storyId = current?.storyId ?: return@LaunchedEffect
        if (detail == null) return@LaunchedEffect
        try {
            repository.openStory(posterChatId, storyId)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { repository.closeStory(posterChatId, storyId) }
        }
    }

    val downloaded = remember(posterChatId) { mutableSetOf<Int>() }
    DisposableEffect(posterChatId) {
        onDispose { downloaded.toList().forEach(repository::cancelFileDownload) }
    }
    // Current full file first, then neighbor thumbnails; never a neighbor's full video.
    LaunchedEffect(posterChatId, current?.storyId, detail?.media?.fileId) {
        val media = detail?.media ?: return@LaunchedEffect
        fun request(targets: List<StoryPrefetchTarget>) {
            val keep = targets.map { it.fileId }.toSet()
            (downloaded - keep).forEach(repository::cancelFileDownload)
            downloaded.clear()
            targets.forEach {
                downloaded += it.fileId
                repository.downloadFile(it.fileId)
            }
        }
        request(storyPrefetchTargets(media, null, null))
        val previousId = stories.getOrNull(index - 1)?.storyId
        val nextId = stories.getOrNull(index + 1)?.storyId
        val previous = previousId?.let { repository.loadStory(posterChatId, it).getOrNull() }
        val next = nextId?.let { repository.loadStory(posterChatId, it).getOrNull() }
        request(storyPrefetchTargets(media, previous?.media, next?.media))
    }

    val media = detail?.media
    val emptyFile = remember { MutableStateFlow(MediaFileState(0)) }
    val fullFile by remember(media?.fileId) { media?.fileId?.let(repository::observeFile) ?: emptyFile }
        .collectAsStateWithLifecycle()
    val thumbnailFile by remember(media?.thumbnailFileId, media?.fileId) {
        (media?.thumbnailFileId ?: media?.fileId)?.let(repository::observeFile) ?: emptyFile
    }.collectAsStateWithLifecycle()

    var paused by remember(current?.storyId) { mutableStateOf(false) }
    var progress by remember(current?.storyId) { mutableFloatStateOf(0f) }
    var progressRestart by remember(current?.storyId) { mutableIntStateOf(0) }
    var videoReady by remember(current?.storyId) { mutableStateOf(false) }
    var videoPlaying by remember(current?.storyId) { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var reactionStoryId by remember { mutableIntStateOf(0) }
    var showReply by remember { mutableStateOf(false) }
    var replyStoryId by remember { mutableIntStateOf(0) }
    var showDelete by remember { mutableStateOf(false) }
    var deleteStoryId by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var editStoryId by remember { mutableIntStateOf(0) }
    var editSaving by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var editCaptionLimit by remember { mutableIntStateOf(1_024) }
    var notice by remember { mutableStateOf<String?>(null) }
    val reducedMotion = rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val dialogOpen = showReactions || showReply || showDelete || showEdit
    val mediaReady = when (media?.kind) {
        StoryMediaKind.Photo -> fullFile.localPath != null
        StoryMediaKind.Video -> fullFile.localPath != null && videoReady
        null -> false
    }
    val storyActive = detail != null && loadError == null && mediaReady && resumed && !paused && !dialogOpen
    val canPlay = storyActive && (media?.kind != StoryMediaKind.Video || videoPlaying)
    val duration = storyDurationMillis(detail)
    val closeLatest by rememberUpdatedState(onClose)
    val storyCount = stories.size
    val latestStoryCount by rememberUpdatedState(storyCount)
    LaunchedEffect(current?.storyId, detail?.media?.fileId, canPlay, duration, progressRestart) {
        if (!canPlay) return@LaunchedEffect
        var elapsed = (progress * duration).coerceAtLeast(0f)
        var last = withFrameNanos { it }
        var lastReported = progress
        while (true) {
            val now = withFrameNanos { it }
            elapsed += (now - last) / 1_000_000f
            last = now
            val next = (elapsed / duration).coerceIn(0f, 1f)
            // With reduced motion the bar steps instead of animating every frame.
            if (!reducedMotion || next - lastReported >= 0.1f || next >= 1f) {
                progress = next
                lastReported = next
            }
            if (next >= 1f) break
        }
        if (index < latestStoryCount - 1) index++ else closeLatest()
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_000)
            notice = null
        }
    }
    LaunchedEffect(showEdit) {
        if (showEdit) {
            editError = null
            editCaptionLimit = repository.storyCaptionLimit().getOrDefault(1_024).coerceAtLeast(1)
        }
    }
    LaunchedEffect(showEdit, editStoryId, current?.storyId) {
        if (showEdit && editStoryId != 0 && current?.storyId != null && current.storyId != editStoryId) {
            showEdit = false
            editError = null
        }
    }

    val goPrevious: () -> Unit = {
        if (index > 0) index-- else {
            progress = 0f
            progressRestart++
        }
    }
    val goNext: () -> Unit = {
        if (index < latestStoryCount - 1) index++ else closeLatest()
    }

    var reactions by remember { mutableStateOf<List<ReactionOption>?>(null) }
    var reactionsLoading by remember { mutableStateOf(false) }
    var reactionsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showReactions) {
        if (!showReactions || reactions != null || reactionsLoading) return@LaunchedEffect
        reactionsLoading = true
        reactionsError = null
        try {
            reactions = repository.storyReactions()
                .onFailure { reactionsError = it.userMessage(context, R.string.stories_reaction_failed) }
                .getOrNull()
        } finally {
            reactionsLoading = false
        }
    }
    var replySending by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val entry = snapshot
    val title = when {
        entry == null -> ""
        entry.isSelf -> stringResource(R.string.stories_you)
        else -> entry.title
    }
    val preview: StoryPreview? = current
    val timeText = remember(preview?.storyId, detail?.dateSeconds) {
        val seconds = detail?.dateSeconds ?: preview?.dateSeconds ?: 0L
        if (seconds <= 0L) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(seconds * 1000L))
    }
    val canEditPrivacy = entry?.isSelf == true && detail?.canSetPrivacySettings == true
    val canEditStory = detail?.canBeEdited == true || canEditPrivacy
    val editingStory = detail?.takeIf { editStoryId != 0 && it.storyId == editStoryId }
    val editingCanEditPrivacy = entry?.isSelf == true && editingStory?.canSetPrivacySettings == true

    if (stories.isEmpty()) {
        StoryEmptyView(
            missing = missing || entry != null,
            title = title,
            onRetry = {
                missing = false
                repository.refreshActiveStories()
            },
            onClose = onClose
        )
        return
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().pointerInput(current?.storyId) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onLongPress = {},
                    onTap = { offset ->
                        if (storyTapTarget(offset.x, size.width) == StoryTapTarget.Previous) goPrevious() else goNext()
                    }
                )
            }
        ) {
            StoryContent(
                detail = detail,
                loading = loading,
                loadError = loadError,
                fullFile = fullFile,
                thumbnailFile = thumbnailFile,
                playing = storyActive,
                onReady = { videoReady = true },
                onPlayingChanged = { videoPlaying = it },
                onEnded = {
                    val ended = current?.storyId
                    if (ended != null && stories.getOrNull(index)?.storyId == ended) {
                        if (index < latestStoryCount - 1) index++ else closeLatest()
                    }
                },
                restartToken = progressRestart,
                onRetry = { retry++ },
                onRetryDownload = { detail?.media?.fileId?.let(repository::downloadFile) },
                onFailure = {
                    videoReady = false
                    notice = context.getString(R.string.stories_media_unsupported)
                }
            )
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            StoryProgressRow(stories.size, index) { progress }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry != null) {
                    TelegramAvatar(
                        initials = entry.initials,
                        color = Color(entry.avatarColorArgb),
                        size = 38.dp,
                        photo = entry.photo,
                        observeFile = repository::observeFile,
                        downloadFile = repository::downloadFile
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (timeText.isNotBlank()) {
                        Text(timeText, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                    }
                    if (entry?.isSelf == true) {
                        detail?.privacy?.let {
                            Text(it.label(), color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.stories_close), tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            if (detail?.caption.orEmpty().isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(detail?.caption.orEmpty(), color = Color.White, fontSize = 16.sp, lineHeight = 21.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = goPrevious, enabled = index > 0, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.stories_previous), tint = Color.White)
                }
                IconButton(onClick = { paused = !paused }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        stringResource(if (paused) R.string.stories_resume else R.string.stories_pause),
                        tint = Color.White
                    )
                }
                IconButton(onClick = goNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.stories_next), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (detail?.canBeReplied == true) {
                    IconButton(onClick = {
                        replyStoryId = current?.storyId ?: 0
                        showReply = true
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Reply, stringResource(R.string.stories_reply), tint = Color.White)
                    }
                }
                IconButton(onClick = {
                    reactionStoryId = current?.storyId ?: 0
                    showReactions = true
                }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Rounded.EmojiEmotions,
                        stringResource(R.string.stories_reaction),
                        tint = if (detail?.chosenReaction != null) Color(0xFF69C9FF) else Color.White
                    )
                }
                if (canEditStory) {
                    IconButton(onClick = {
                        editStoryId = detail?.storyId ?: current?.storyId ?: 0
                        if (editStoryId != 0) showEdit = true
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Edit, stringResource(R.string.stories_edit), tint = Color.White)
                    }
                }
                if (detail?.canBeDeleted == true) {
                    IconButton(onClick = {
                        deleteStoryId = current?.storyId ?: 0
                        showDelete = true
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.stories_delete), tint = Color.White)
                    }
                }
            }
        }

        notice?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE62A2D34))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .errorAnnouncement()
            )
        }
    }

    if (showReactions) {
        StoryReactionDialog(
            reactions = reactions.orEmpty(),
            loading = reactionsLoading,
            error = reactionsError,
            chosen = detail?.chosenReaction,
            repository = repository,
            onDismiss = { showReactions = false },
            onPick = { option ->
                val target = if (detail?.chosenReaction == option) null else option
                scope.launch {
                    repository.setStoryReaction(posterChatId, reactionStoryId, target)
                        .onSuccess {
                            detail = detail?.copy(chosenReaction = target)
                            showReactions = false
                        }
                        .onFailure { notice = it.userMessage(context, R.string.stories_reaction_failed) }
                }
            }
        )
    }

    if (showReply) {
        StoryReplyDialog(
            sending = replySending,
            onDismiss = { if (!replySending) showReply = false },
            onSend = { text ->
                replySending = true
                scope.launch {
                    repository.replyToStory(posterChatId, replyStoryId, text)
                        .onSuccess {
                            replySending = false
                            showReply = false
                            notice = context.getString(R.string.stories_reply_sent)
                        }
                        .onFailure {
                            replySending = false
                            notice = it.userMessage(context, R.string.stories_reply_failed)
                        }
                }
            }
        )
    }

    if (showEdit && editingStory != null) {
        StoryEditDialog(
            initialCaption = editingStory.caption,
            captionLimit = editCaptionLimit,
            initialPrivacy = editingStory.privacy,
            initialSelectedUserIds = editingStory.privacyUserIds,
            canEditCaption = editingStory.canBeEdited,
            canEditPrivacy = editingCanEditPrivacy,
            saving = editSaving,
            error = editError,
            repository = repository,
            onDismiss = {
                if (!editSaving) {
                    showEdit = false
                    editStoryId = 0
                }
            },
            onSave = { caption, privacy, ids ->
                detail?.takeIf { it.storyId == editStoryId }?.let { story ->
                    scope.launch {
                        editSaving = true
                        editError = null
                        var failure: Throwable? = null
                        if (story.canBeEdited && caption != story.caption) {
                            repository.editStoryCaption(posterChatId, story.storyId, caption)
                                .onFailure { failure = it }
                        }
                        if (failure == null && privacy != null &&
                            entry?.isSelf == true && story.canSetPrivacySettings &&
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
                            detail = story.copy(
                                caption = if (story.canBeEdited) caption else story.caption,
                                privacy = privacy ?: story.privacy,
                                privacyUserIds = if (privacy != null) ids else story.privacyUserIds
                            )
                            showEdit = false
                            editStoryId = 0
                            notice = context.getString(R.string.stories_edit_saved)
                            retry++
                        }
                    }
                }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDelete = false },
            title = { Text(stringResource(R.string.stories_delete)) },
            text = { Text(stringResource(R.string.stories_delete_confirm)) },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            repository.deleteStory(posterChatId, deleteStoryId)
                                .onSuccess {
                                    notice = context.getString(R.string.stories_deleted)
                                    onClose()
                                }
                                .onFailure {
                                    deleting = false
                                    showDelete = false
                                    notice = it.userMessage(context, R.string.stories_delete_failed)
                                }
                        }
                    }
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { showDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun StoryContent(
    detail: StoryDetail?,
    loading: Boolean,
    loadError: String?,
    fullFile: MediaFileState,
    thumbnailFile: MediaFileState,
    playing: Boolean,
    onReady: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onEnded: () -> Unit,
    restartToken: Int,
    onRetry: () -> Unit,
    onRetryDownload: () -> Unit,
    onFailure: () -> Unit
) {
    val media = detail?.media
    when {
        loadError != null -> Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(loadError, color = Color.White, fontSize = 16.sp)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.stories_retry), color = Color.White) }
        }
        detail != null && media == null -> Text(
            stringResource(R.string.stories_media_unsupported),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            modifier = Modifier.padding(32.dp)
        )
        media == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            if (loading) Text(
                stringResource(R.string.stories_loading),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        media.kind == StoryMediaKind.Photo -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StoryPhoto(fullFile, thumbnailFile, media.minithumbnail)
            if (fullFile.error != null && fullFile.localPath == null) {
                StoryDownloadError(fullFile, onRetryDownload)
            }
        }
        else -> {
            val path = fullFile.localPath
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (path == null) StoryPhoto(fullFile, thumbnailFile, media.minithumbnail)
                else StoryVideo(path, playing, onFailure, onReady, onPlayingChanged, onEnded, restartToken)
                if (fullFile.error != null && fullFile.localPath == null) {
                    StoryDownloadError(fullFile, onRetryDownload)
                }
            }
        }
    }
}

@Composable
internal fun StoryDownloadError(file: MediaFileState, onRetry: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xE62A2D34))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            file.error?.resolve().orEmpty(),
            color = Color.White,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.stories_retry), color = Color.White)
        }
    }
}

@Composable
private fun StoryProgressRow(count: Int, index: Int, progress: () -> Float) {
    if (count <= 0) return
    val currentIndex = index.coerceIn(0, count - 1)
    val currentProgress = progress().coerceIn(0f, 1f)
    val description = stringResource(R.string.stories_progress, currentIndex + 1, count)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                    (currentIndex + currentProgress) / count,
                    0f..1f
                )
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(count) { segment ->
            val fraction = when {
                segment < currentIndex -> 1f
                segment > currentIndex -> 0f
                else -> currentProgress
            }
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.34f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
            }
        }
    }
}

@Composable
private fun StoryEmptyView(missing: Boolean, title: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(
                if (missing) stringResource(R.string.stories_unavailable) else stringResource(R.string.stories_loading),
                color = Color.White,
                fontSize = 17.sp
            )
            if (!title.isBlank()) {
                Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (missing) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.stories_retry), color = Color.White) }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_close), color = Color.White) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryReactionDialog(
    reactions: List<ReactionOption>,
    loading: Boolean,
    error: String?,
    chosen: ReactionOption?,
    repository: TelegramRepository,
    onDismiss: () -> Unit,
    onPick: (ReactionOption) -> Unit
) {
    val palette = beyGramPalette()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 28.dp).clip(RoundedCornerShape(24.dp)).background(palette.surface)
                .padding(20.dp)
        ) {
            Text(
                stringResource(R.string.stories_reaction_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            when {
                loading -> CircularProgressIndicator(color = palette.accent, modifier = Modifier.padding(24.dp).size(28.dp))
                error != null -> Text(error, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                reactions.isEmpty() -> Text(
                    stringResource(R.string.stories_reactions_empty),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                else -> FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reactions.forEach { option ->
                        val selected = option == chosen
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(if (selected) palette.accentSoft else palette.surface2)
                                .then(if (selected) Modifier.border(1.dp, palette.accent, CircleShape) else Modifier)
                                .clickable { onPick(option) },
                            contentAlignment = Alignment.Center
                        ) {
                            StoryReactionGlyph(option, repository)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.stories_paid_reactions_note),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 6.dp)) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

@Composable
private fun StoryReactionGlyph(option: ReactionOption, repository: TelegramRepository) {
    if (option.customEmojiId == 0L) {
        Text(option.emoji, fontSize = 24.sp)
        return
    }
    val custom by remember(option.customEmojiId) { repository.observeCustomEmoji(option.customEmojiId) }
        .collectAsStateWithLifecycle()
    val emoji = custom
    if (emoji == null) {
        Text("…", fontSize = 20.sp, color = Color.White)
        return
    }
    val file by remember(emoji.fileId) { repository.observeFile(emoji.fileId) }.collectAsStateWithLifecycle()
    LaunchedEffect(emoji.fileId) { repository.downloadFile(emoji.fileId) }
    val path = file.localPath
    if (path != null) LocalMediaImage(path, Modifier.size(30.dp))
    else Text(emoji.emoji.ifBlank { "…" }, fontSize = 20.sp, color = Color.White)
}

@Composable
private fun StoryReplyDialog(sending: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.stories_reply_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.stories_reply_hint)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !sending,
                onClick = { onSend(text) }
            ) { Text(stringResource(R.string.common_send)) }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
