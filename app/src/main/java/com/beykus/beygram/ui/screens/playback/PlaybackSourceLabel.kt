package com.beykus.beygram.ui.screens.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.repository.TelegramRepository
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

/** Only the selected Telegram account's repository may resolve the music origin. */
internal data class PlaybackRepositorySource(val accountId: String, val repository: TelegramRepository)

internal val LocalPlaybackRepositorySource = compositionLocalOf<PlaybackRepositorySource?> { null }

internal data class PlaybackSourceLabel(
    val text: String,
    val kind: PlaybackSourceKind,
    val canOpen: Boolean
)

/** Resolves music source names from the selected account's repository, never a different Telegram login. */
@Composable
internal fun rememberPlaybackSourceLabel(origin: PlaybackOrigin?): PlaybackSourceLabel {
    val selected = LocalPlaybackRepositorySource.current
    var preview by remember(origin, selected) { mutableStateOf<ChatPreview?>(null) }
    LaunchedEffect(origin, selected) {
        preview = null
        val current = origin ?: return@LaunchedEffect
        val scoped = selected?.takeIf { it.accountId == current.accountId } ?: return@LaunchedEffect
        val repository = scoped.repository
        val flow = repository.observeChat(current.chatId)
        preview = flow.value
        if (preview == null) repository.loadChat(current.chatId)
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
