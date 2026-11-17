package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.flow.StateFlow

/**
 * The state without byte counters. Status flags, error, paths and size stay, so readers of this value
 * recompose only when something other than download/upload progress changes.
 */
internal fun MediaFileState.withoutProgress(): MediaFileState =
    copy(downloadedBytes = 0, uploadedBytes = 0, downloadedPrefixBytes = 0)

/**
 * A chat-bubble view of one TDLib file. [raw] is live and changes on every progress tick, so read it only
 * inside deferred lambdas or in a child composable. [content] changes only when something other than progress does.
 */
internal class MediaFileHandle(val raw: State<MediaFileState>, val content: State<MediaFileState>)

@Composable
internal fun rememberMediaFile(fileId: Int, observeFile: (Int) -> StateFlow<MediaFileState>): MediaFileHandle {
    val raw = remember(fileId) { observeFile(fileId) }.collectAsStateWithLifecycle()
    // derivedStateOf compares with equals, so a progress-only tick does not notify the readers of [content].
    val content = remember(raw) { derivedStateOf { raw.value.withoutProgress() } }
    return remember(raw, content) { MediaFileHandle(raw, content) }
}

/** Upload or download fraction, whichever is running. Call it only from a deferred progress lambda. */
internal fun MediaFileState.transferProgress(): Float = if (isUploading) uploadProgress else downloadProgress
