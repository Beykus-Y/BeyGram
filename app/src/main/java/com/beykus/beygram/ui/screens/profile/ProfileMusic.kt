package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileAudio
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Добавление трека из общего плеера в профиль. Значение задаёт корень приложения через репозиторий текущего
 * аккаунта; null прячет действие, например в режиме без аккаунта.
 */
val LocalAddProfileAudio = compositionLocalOf<(suspend (PlaybackItem) -> Result<Unit>)?> { null }

/**
 * Полоса музыки под аватаром. Играет через общий плеер, как и музыка из чатов, поэтому мини-плеер и экран
 * плеера работают одинаково. У своего профиля список открывается с перестановкой и удалением, у чужого только
 * прослушивание.
 */
@Composable
internal fun ProfileMusicBar(
    ownerId: Long,
    tracks: List<ProfileAudio>,
    own: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    removeTrack: suspend (Int) -> Result<Unit>,
    moveTrackToFront: suspend (Int) -> Result<Unit>
) {
    if (tracks.isEmpty()) return
    val palette = beyGramPalette()
    val context = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val playback by PlaybackEngine.state.collectAsState()
    var listOpen by rememberSaveable(ownerId) { mutableStateOf(false) }
    // Bar shows the track that is playing from this profile; otherwise the first one.
    val playingIndex = tracks.indexOfFirst { profileAudioPlaybackKey(accountId, ownerId, it.fileId) == playback.item?.key }
    val index = if (playingIndex >= 0) playingIndex else 0
    val shown = tracks[index]
    val isPlaying = playingIndex >= 0 && playback.playing
    val playLabel = stringResource(R.string.profile_music_play)
    val pauseLabel = stringResource(R.string.profile_music_pause)
    val subtitle = if (tracks.size > 1) stringResource(R.string.profile_music_track_count, index + 1, tracks.size) else shown.performer
    // The list also opens for a single track on the own profile, because a track is removed from there.
    val canOpenList = own || tracks.size > 1

    fun play(start: Int) {
        val queue = tracks.map { track ->
            track.playbackItem(accountId, ownerId, observeFile(track.fileId).value.localPath.orEmpty())
        }
        PlaybackEngine.playQueue(context, queue, start, PlaybackQueueSource(accountId, observeFile, downloadFile, autoDownloadFile))
    }
    fun togglePlayback() {
        if (playingIndex >= 0) PlaybackEngine.togglePlayPause() else play(index)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.accentSoft.copy(alpha = 0.6f))
            .clickable(role = Role.Button) { if (canOpenList) listOpen = true else togglePlayback() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.accent)
                .clickable(role = Role.Button) { togglePlayback() }
                .semantics { contentDescription = if (isPlaying) pauseLabel else playLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = palette.onAccent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                shown.displayTitle,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) Text(
                subtitle,
                color = palette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canOpenList) Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(22.dp)
        )
    }

    if (listOpen) ProfileMusicSheet(
        ownerId = ownerId,
        tracks = tracks,
        own = own,
        accountId = accountId,
        playingKey = playback.item?.key,
        onDismiss = { listOpen = false },
        onPlay = { position ->
            listOpen = false
            if (position == playingIndex) PlaybackEngine.togglePlayPause() else play(position)
        },
        removeTrack = removeTrack,
        moveTrackToFront = moveTrackToFront
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileMusicSheet(
    ownerId: Long,
    tracks: List<ProfileAudio>,
    own: Boolean,
    accountId: String,
    playingKey: String?,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    removeTrack: suspend (Int) -> Result<Unit>,
    moveTrackToFront: suspend (Int) -> Result<Unit>
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var failure by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetSurfaceColor()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.profile_music_title),
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
            )
            failure?.let { Text(it, color = palette.danger, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
            tracks.forEachIndexed { position, track ->
                ProfileMusicRow(
                    track = track,
                    position = position,
                    active = profileAudioPlaybackKey(accountId, ownerId, track.fileId) == playingKey,
                    own = own,
                    onPlay = { onPlay(position) },
                    onMakeFirst = {
                        scope.launch {
                            moveTrackToFront(track.fileId)
                                .onSuccess { failure = null }
                                .onFailure { failure = it.userMessage(context, R.string.profile_music_failed) }
                        }
                    },
                    onRemove = {
                        scope.launch {
                            removeTrack(track.fileId)
                                .onSuccess { failure = null }
                                .onFailure { failure = it.userMessage(context, R.string.profile_music_failed) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileMusicRow(
    track: ProfileAudio,
    position: Int,
    active: Boolean,
    own: Boolean,
    onPlay: () -> Unit,
    onMakeFirst: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.player_menu)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(start = 24.dp, end = if (own) 8.dp else 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = if (active) palette.accent else palette.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            track.displayTitle,
            color = if (active) palette.accentInk else palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (own) Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, moreLabel, tint = palette.textSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (position > 0) DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_music_make_first), color = palette.textPrimary) },
                    onClick = {
                        menuOpen = false
                        onMakeFirst()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_music_remove), color = palette.danger) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    }
                )
            }
        }
    }
}
