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
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryMediaKind
import com.beykus.beygram.domain.model.StoryPreview
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.CustomEmojiImage
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
 * A temporary network or active-list change keeps the open story pinned; only a confirmed empty
 * active list may close the viewer automatically.
 */
@Composable
fun StoryViewerScreen(
    repository: TelegramRepository,
    posterChatId: Long,
    initialStoryId: Int,
    onClose: () -> Unit
) {
    SecureStoryWindow()

    val bars by repository.activeStories.collectAsStateWithLifecycle()
    val connection by repository.connectionState.collectAsStateWithLifecycle()
    val connected = connection == TelegramConnectionState.Connected

    // The entry is kept even after the author leaves the visible bar: an empty list update is not
    // enough proof that the stories were deleted, and the viewer must survive a network failure.
    var snapshot by remember(posterChatId) {
        mutableStateOf(repository.cachedActiveStory(posterChatId))
    }
    var initialTimedOut by remember(posterChatId) { mutableStateOf(false) }
    var initialRetry by remember(posterChatId) { mutableIntStateOf(0) }

    val stories = snapshot?.stories.orEmpty()
    val storyIds = stories.map { it.storyId }
    var index by remember(posterChatId) { mutableIntStateOf(-1) }
    var visibleStory by remember(posterChatId) { mutableStateOf<StoryPreview?>(null) }

    var closing by remember(posterChatId) { mutableStateOf(false) }
    val latestStoryCount by rememberUpdatedState(stories.size)
    val latestOnClose by rememberUpdatedState(onClose)
    val closeViewer: (StoryViewerCloseReason) -> Unit = { reason ->
        if (!closing) {
            closing = true
            logStoryViewerClose(reason, latestStoryCount)
            latestOnClose()
        }
    }
    BackHandler { closeViewer(StoryViewerCloseReason.Back) }

    LaunchedEffect(posterChatId, bars) {
        val entry = bars.firstOrNull { it.chatId == posterChatId }
        storyViewerEntryUpdate(snapshot, entry)?.let { next ->
            if (next !== snapshot) snapshot = next
        }
    }
    LaunchedEffect(posterChatId, initialRetry, connection, snapshot == null) {
        if (snapshot != null) return@LaunchedEffect
        initialTimedOut = false
        repository.refreshActiveStories()
        // WaitingForNetwork/Unknown already show the offline state. For Connecting/Updating a
        // stalled reconnect must still leave the spinner after the same bounded wait.
        if (connection != TelegramConnectionState.WaitingForNetwork &&
            connection != TelegramConnectionState.Unknown
        ) {
            delay(8_000)
            if (snapshot == null) initialTimedOut = true
        }
    }
    val authorStillListed = bars.any { it.chatId == posterChatId }
    LaunchedEffect(posterChatId, authorStillListed, connected, snapshot != null) {
        if (!needsStoryRemovalConfirmation(snapshot != null, authorStillListed, connected)) return@LaunchedEffect
        // A page replacement can briefly omit the author. Confirm directly and keep the viewer open
        // unless TDLib answers twice that the active list is actually empty.
        delay(1_000)
        if (bars.any { it.chatId == posterChatId }) return@LaunchedEffect
        if (confirmStoryListState { repository.confirmActiveStoryIds(posterChatId) } == StoryListConfirmation.Gone) {
            closeViewer(StoryViewerCloseReason.Unavailable)
        }
    }

    LaunchedEffect(storyIds, initialStoryId) {
        val next = storyViewerPinnedStory(
            stories,
            visibleStory,
            initialStoryId,
            snapshot?.maxReadStoryId ?: 0
        )
        if (next == null) return@LaunchedEffect
        val found = stories.indexOfFirst { it.storyId == next.storyId }
        if (found >= 0) {
            index = found
            visibleStory = stories[found]
        } else if (visibleStory == null) {
            // The pinned story is not in the current page yet; keep it while the list is refreshed.
            visibleStory = next
        }
    }
    val current = visibleStory
    val currentIndex = stories.indexOfFirst { it.storyId == current?.storyId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember(posterChatId, current?.storyId) {
        mutableStateOf(current?.storyId?.let { repository.cachedStory(posterChatId, it) })
    }
    var loading by remember(posterChatId, current?.storyId) { mutableStateOf(false) }
    var loadError by remember(posterChatId, current?.storyId) { mutableStateOf<String?>(null) }
    var cachedNotice by remember(posterChatId, current?.storyId) { mutableStateOf(false) }
    var retry by remember(posterChatId, current?.storyId) { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun confirmUnavailable(storyId: Int) {
        if (repository.connectionState.value != TelegramConnectionState.Connected) return
        when (val confirmation = confirmStoryListState { repository.confirmActiveStoryIds(posterChatId) }) {
            StoryListConfirmation.Gone -> closeViewer(StoryViewerCloseReason.Unavailable)
            is StoryListConfirmation.Present -> {
                if (storyId in confirmation.storyIds) return
                val nextIndex = storySuccessorIndex(stories, storyId, index)
                val next = nextIndex?.let(stories::getOrNull)
                if (nextIndex != null && next != null && next.storyId != storyId) {
                    index = nextIndex
                    visibleStory = next
                } else {
                    repository.refreshActiveStories()
                    if (detail == null) {
                        loadError = context.getString(R.string.stories_unavailable)
                    } else {
                        notice = context.getString(R.string.stories_unavailable)
                    }
                }
            }
            StoryListConfirmation.Unknown -> Unit
        }
    }

    // A partial list can omit the open story. Verify it directly before leaving the cached frame
    // on screen forever; only an explicitly confirmed absence moves or closes the viewer. The
    // effect is keyed by the list so an unknown result is retried after reconnecting or refreshing.
    LaunchedEffect(current?.storyId, currentIndex, connected, storyIds) {
        val storyId = current?.storyId ?: return@LaunchedEffect
        if (!connected || currentIndex >= 0 || stories.isEmpty()) return@LaunchedEffect
        confirmUnavailable(storyId)
    }

    LaunchedEffect(posterChatId, current?.storyId, retry, connected) {
        val storyId = current?.storyId ?: return@LaunchedEffect
        if (detail == null) {
            repository.cachedStory(posterChatId, storyId)?.let {
                detail = it
                cachedNotice = true
            }
        }
        if (!connected) {
            loading = false
            if (detail == null) {
                loadError = context.getString(R.string.stories_offline_body)
            } else {
                cachedNotice = true
            }
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        val result = repository.loadStory(posterChatId, storyId)
        loading = false
        result.onSuccess {
            detail = it
            cachedNotice = false
        }.onFailure { error ->
            if (detail != null) {
                cachedNotice = true
            } else {
                loadError = error.userMessage(context, R.string.stories_unavailable)
            }
            confirmUnavailable(storyId)
        }
    }

    val downloaded = remember(posterChatId) { mutableSetOf<Int>() }
    DisposableEffect(posterChatId) {
        onDispose { downloaded.toList().forEach(repository::cancelFileDownload) }
    }
    // Current full file first, then neighbor thumbnails; never a neighbor's full video.
    LaunchedEffect(posterChatId, current?.storyId, detail?.media?.fileId, connected) {
        val media = detail?.media ?: return@LaunchedEffect
        if (!connected) return@LaunchedEffect
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
        val previous = previousId?.let {
            repository.cachedStory(posterChatId, it) ?: repository.loadStory(posterChatId, it).getOrNull()
        }
        val next = nextId?.let {
            repository.cachedStory(posterChatId, it) ?: repository.loadStory(posterChatId, it).getOrNull()
        }
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
    var advancePendingStoryId by remember(posterChatId) { mutableIntStateOf(0) }
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

    // TDLib marks a story as read only after OpenStory succeeds. Wait until the media is actually on
    // screen; otherwise a failed or offline load must not consume the unread marker.
    var openedStoryId by remember(posterChatId) { mutableIntStateOf(0) }
    var dispatchedStoryId by remember(posterChatId) { mutableIntStateOf(0) }
    LaunchedEffect(posterChatId, current?.storyId, detail != null, mediaReady, connected, resumed, retry, openedStoryId) {
        val storyId = current?.storyId ?: return@LaunchedEffect
        if (!connected || detail == null || !mediaReady || !resumed || openedStoryId == storyId) {
            return@LaunchedEffect
        }
        // Mark the request before awaiting it: TDLib may process OpenStory after the coroutine is
        // cancelled, so leaving must still send the matching CloseStory.
        dispatchedStoryId = storyId
        // A timeout can hide a request that TDLib already processed, so keep the dispatched marker
        // until leaving the story even when the local result is a failure.
        if (repository.openStory(posterChatId, storyId).isSuccess) {
            openedStoryId = storyId
        }
    }
    // CloseStory is sent only for a story whose open was dispatched or completed. A connection blip
    // does not restart this effect, so the open story and its playback position survive it.
    LaunchedEffect(posterChatId, current?.storyId, detail != null) {
        val storyId = current?.storyId ?: return@LaunchedEffect
        if (detail == null) return@LaunchedEffect
        try {
            awaitCancellation()
        } finally {
            val dispatched = dispatchedStoryId == storyId
            val opened = openedStoryId == storyId
            if (dispatched || opened) {
                withContext(NonCancellable) { repository.closeStory(posterChatId, storyId) }
                // Allow OpenStory for this story again if the user navigates back to it.
                if (dispatchedStoryId == storyId) dispatchedStoryId = 0
                if (openedStoryId == storyId) openedStoryId = 0
            }
        }
    }

    val storyActive = detail != null && loadError == null && mediaReady && resumed && !paused && !dialogOpen
    val canPlay = storyActive && (media?.kind != StoryMediaKind.Video || videoPlaying)
    val duration = storyDurationMillis(detail)
    val latestStories by rememberUpdatedState(stories)
    val latestVisibleStoryId by rememberUpdatedState(current?.storyId)
    val closeCompleted by rememberUpdatedState { closeViewer(StoryViewerCloseReason.Completed) }
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
        val at = latestStories.indexOfFirst { it.storyId == latestVisibleStoryId }
        when {
            at in 0 until latestStories.lastIndex -> {
                advancePendingStoryId = 0
                index = at + 1
                visibleStory = latestStories[at + 1]
            }
            at >= 0 && at == latestStories.lastIndex -> closeCompleted()
            // The open story is not in the current list page. Keep it instead of closing on a
            // temporary network/empty-list response; advance when the list includes it again.
            else -> latestVisibleStoryId?.let { advancePendingStoryId = it }
        }
    }
    LaunchedEffect(current?.storyId) {
        advancePendingStoryId = 0
    }
    LaunchedEffect(current?.storyId, currentIndex, advancePendingStoryId, stories, storyActive) {
        val storyId = current?.storyId ?: return@LaunchedEffect
        // A queued completion waits for the same eligibility as the normal timer: no pause, hold,
        // dialog or background state may be bypassed when the list recovers.
        if (advancePendingStoryId != storyId || currentIndex < 0 || !storyActive) return@LaunchedEffect
        advancePendingStoryId = 0
        if (currentIndex < stories.lastIndex) {
            index = currentIndex + 1
            visibleStory = stories[currentIndex + 1]
        } else {
            closeViewer(StoryViewerCloseReason.Completed)
        }
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
        when {
            currentIndex > 0 -> {
                index = currentIndex - 1
                visibleStory = stories[currentIndex - 1]
            }
            currentIndex == 0 -> {
                advancePendingStoryId = 0
                progress = 0f
                progressRestart++
            }
            stories.isNotEmpty() -> {
                val target = (index - 1).coerceIn(0, stories.lastIndex)
                if (target == index) {
                    advancePendingStoryId = 0
                    progress = 0f
                    progressRestart++
                } else {
                    index = target
                    visibleStory = stories[target]
                }
            }
            else -> {
                advancePendingStoryId = 0
                progress = 0f
                progressRestart++
            }
        }
    }
    val goNext: () -> Unit = {
        when {
            currentIndex in 0 until stories.lastIndex -> {
                index = currentIndex + 1
                visibleStory = stories[currentIndex + 1]
            }
            currentIndex >= 0 && currentIndex == stories.lastIndex ->
                closeViewer(StoryViewerCloseReason.Completed)
            stories.isNotEmpty() -> {
                val target = (index + 1).coerceIn(0, stories.lastIndex)
                index = target
                visibleStory = stories[target]
            }
            // An empty list is not a confirmed end of the stories; keep the viewer open.
            else -> Unit
        }
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
    val progressIndex = if (currentIndex >= 0) {
        currentIndex
    } else {
        index.coerceIn(0, (stories.size - 1).coerceAtLeast(0))
    }
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

    if (current == null) {
        val phase = if (entry != null) {
            StoryViewerEmptyPhase.Unavailable
        } else {
            storyViewerEmptyPhase(connection, initialTimedOut)
        }
        StoryEmptyView(
            phase = phase,
            title = title,
            onRetry = {
                initialTimedOut = false
                initialRetry++
            },
            onClose = { closeViewer(StoryViewerCloseReason.CloseButton) }
        )
        return
    }

    val latestGoPrevious by rememberUpdatedState(goPrevious)
    val latestGoNext by rememberUpdatedState(goNext)
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
                        if (storyTapTarget(offset.x, size.width) == StoryTapTarget.Previous) {
                            latestGoPrevious()
                        } else {
                            latestGoNext()
                        }
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
                offline = !connected,
                onReady = { videoReady = true },
                onPlayingChanged = { videoPlaying = it },
                onEnded = {
                    val ended = current?.storyId
                    val at = stories.indexOfFirst { it.storyId == ended }
                    when {
                        at in 0 until stories.lastIndex -> {
                            advancePendingStoryId = 0
                            index = at + 1
                            visibleStory = stories[at + 1]
                        }
                        at >= 0 && at == stories.lastIndex ->
                            closeViewer(StoryViewerCloseReason.Completed)
                        else -> if (ended != null) advancePendingStoryId = ended
                        // The story is not in the latest page; advance when the list includes it again.
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
            StoryProgressRow(stories.size, progressIndex) { progress }
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
                IconButton(
                    onClick = { closeViewer(StoryViewerCloseReason.CloseButton) },
                    modifier = Modifier.size(48.dp)
                ) {
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
                IconButton(
                    onClick = goPrevious,
                    enabled = currentIndex > 0 || (currentIndex < 0 && stories.isNotEmpty()),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.stories_previous), tint = Color.White)
                }
                IconButton(onClick = { paused = !paused }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        stringResource(if (paused) R.string.stories_resume else R.string.stories_pause),
                        tint = Color.White
                    )
                }
                IconButton(onClick = goNext, enabled = stories.isNotEmpty(), modifier = Modifier.size(48.dp)) {
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

        if (cachedNotice) {
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE62A2D34))
                    .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                    .errorAnnouncement(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.stories_offline_cached),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TextButton(onClick = { retry++ }) {
                    Text(stringResource(R.string.stories_retry), color = Color.White)
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
                                    closeViewer(StoryViewerCloseReason.Deleted)
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
    offline: Boolean,
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
            Modifier.fillMaxWidth().padding(32.dp).errorAnnouncement(),
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
            storyDownloadMessage(fullFile, offline)?.let { StoryDownloadError(it, onRetryDownload) }
        }
        else -> {
            val path = fullFile.localPath
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (path == null) StoryPhoto(fullFile, thumbnailFile, media.minithumbnail)
                else StoryVideo(path, playing, onFailure, onReady, onPlayingChanged, onEnded, restartToken)
                storyDownloadMessage(fullFile, offline)?.let { StoryDownloadError(it, onRetryDownload) }
            }
        }
    }
}

@Composable
private fun storyDownloadMessage(file: MediaFileState, offline: Boolean): String? = when {
    file.localPath != null -> null
    offline -> stringResource(R.string.stories_download_offline)
    file.error != null -> file.error?.resolve().orEmpty()
    else -> null
}

@Composable
internal fun StoryDownloadError(file: MediaFileState, onRetry: () -> Unit) {
    StoryDownloadError(file.error?.resolve().orEmpty(), onRetry)
}

@Composable
internal fun StoryDownloadError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xE62A2D34))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .errorAnnouncement(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
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
private fun StoryEmptyView(
    phase: StoryViewerEmptyPhase,
    title: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).errorAnnouncement()
        ) {
            when (phase) {
                StoryViewerEmptyPhase.Loading -> {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        stringResource(R.string.stories_loading),
                        color = Color.White,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                StoryViewerEmptyPhase.Offline -> {
                    Text(
                        stringResource(R.string.stories_offline_title),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        stringResource(R.string.stories_offline_body),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                StoryViewerEmptyPhase.Unavailable -> {
                    Text(
                        stringResource(R.string.stories_unavailable),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        stringResource(R.string.stories_unavailable_body),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            if (!title.isBlank()) {
                Text(
                    title,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (phase != StoryViewerEmptyPhase.Loading) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.stories_retry), color = Color.White)
                }
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
    CustomEmojiImage(
        emoji = emoji,
        size = 30.dp,
        observeFile = repository::observeFile,
        downloadFile = repository::downloadFile
    ) {
        Text(emoji.emoji.ifBlank { "…" }, fontSize = 20.sp, color = Color.White)
    }
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
