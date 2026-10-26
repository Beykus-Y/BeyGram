package com.beykus.beygram.ui.screens.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.flow.collect

/**
 * Requests the full music player from anywhere in the app (for example the music bubble). The currently composed
 * mini-player owns the sheet, so cards do not open a second, independent copy of the player.
 */
internal object MusicPlayerLauncher {
    private val _openRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _openRequests
    fun open() {
        _openRequests.tryEmit(Unit)
    }
}

internal data class PlaybackSourceLabel(
    val text: String,
    val kind: PlaybackSourceKind,
    val canOpen: Boolean
)

/**
 * Resolves the chat/channel name from TDLib's local chat cache. Only the first account's repository is globally
 * reachable; for a secondary account the UI honestly falls back to the generic source name instead of showing a
 * title from the wrong account. The source is read from the same account-scoped origin as the playing item.
 */
@Composable
internal fun rememberPlaybackSourceLabel(origin: PlaybackOrigin?): PlaybackSourceLabel {
    val context = LocalContext.current
    var preview by remember(origin) { mutableStateOf<ChatPreview?>(null) }
    LaunchedEffect(origin) {
        preview = null
        val current = origin ?: return@LaunchedEffect
        val repository = runCatching {
            TdLibTelegramRepository.getInstance(context.applicationContext)
        }.getOrNull() ?: return@LaunchedEffect
        if (repository.accountId != current.accountId) return@LaunchedEffect
        val flow = repository.observeChat(current.chatId)
        preview = flow.value
        if (preview == null) runCatching { repository.loadChat(current.chatId) }
        flow.collect { preview = it }
    }
    val kind = playbackSourceKind(
        hasOrigin = origin != null,
        isSavedMessages = preview?.isSavedMessages == true,
        isChannel = preview?.isChannel == true,
        isGroup = preview?.isGroup == true
    )
    val generic = when (kind) {
        PlaybackSourceKind.Local -> stringResource(R.string.player_source_local)
        PlaybackSourceKind.SavedMessages -> stringResource(R.string.player_source_saved)
        PlaybackSourceKind.Channel -> stringResource(R.string.player_source_channel)
        PlaybackSourceKind.Group -> stringResource(R.string.player_source_group)
        PlaybackSourceKind.Chat -> stringResource(R.string.player_source_chat)
    }
    val title = preview?.title?.trim().orEmpty()
    val text = when (kind) {
        PlaybackSourceKind.Local, PlaybackSourceKind.SavedMessages -> generic
        PlaybackSourceKind.Channel ->
            listOf(generic, title).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { generic }
        PlaybackSourceKind.Group ->
            listOf(generic, title).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { generic }
        PlaybackSourceKind.Chat -> title.ifBlank { generic }
    }
    return PlaybackSourceLabel(text = text, kind = kind, canOpen = origin != null)
}
