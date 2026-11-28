package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.text.resolve

@Composable
internal fun RecordingPreviewDialog(
    mediaList: List<OutgoingMedia>,
    selectedIndex: Int,
    sentPaths: Set<String>,
    sending: Boolean,
    progress: Int,
    onSelect: (Int) -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    /** Why the last delivery attempt did not happen; the draft stays available for another try. */
    failure: VideoNoteFailure? = null
) {
    val safeIndex = selectedIndex.coerceIn(0, mediaList.lastIndex.coerceAtLeast(0))
    val media = mediaList[safeIndex]
    val context = LocalContext.current
    val previewKey = remember(media.path) { java.util.UUID.randomUUID().toString() }
    val speech by PlaybackEngine.speechState.collectAsStateWithLifecycle()
    val preview = speech.takeIf { it.item?.key == previewKey }
    DisposableEffect(previewKey) {
        onDispose { PlaybackEngine.stopPrivateSpeech(previewKey) }
    }
    BeyGramDialog(
        onDismissRequest = {},
        title = stringResource(R.string.recorder_preview_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mediaList.size > 1) {
                    Text(stringResource(R.string.recorder_preview_batch, mediaList.size, duration(mediaList.sumOf { it.durationSeconds })))
                    Text(stringResource(R.string.recorder_preview_size, formatMediaSize(mediaList.sumOf { it.size })))
                }
                Text(stringResource(R.string.recorder_preview_duration, duration(media.durationSeconds)))
                if (media.kind == MediaKind.VideoNote) {
                    VideoNotePreview(
                        path = media.path,
                        posterPath = media.thumbnailPath,
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                            .semantics { contentDescription = context.getString(R.string.a11y_video_note_preview) }
                    )
                } else {
                    val playing = preview?.intentPlaying == true
                    val canPlay = java.io.File(media.path).isFile
                    Button(enabled = canPlay, onClick = {
                        PlaybackEngine.toggleSpeechPreview(context, previewKey, MediaKind.Voice, media.path, media.durationSeconds)
                    }) { Text(stringResource(if (playing) R.string.recorder_preview_pause else R.string.recorder_preview_play)) }
                    if (!canPlay || preview?.failed == true) {
                        // A "Play" that does nothing is the same dead-button problem as a silent "Send".
                        Text(
                            text = stringResource(R.string.recorder_preview_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.errorAnnouncement()
                        )
                    }
                }
                if (failure != null) {
                    // The reason is the point of the dialog after a failed attempt: without it every "Send" looked like
                    // a dead button (P0 #1228).
                    Text(
                        text = failure.message.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.errorAnnouncement()
                    )
                    if (failure.detail.isNotBlank()) {
                        Text(
                            text = failure.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start
                        )
                    }
                }
                if (mediaList.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = safeIndex > 0, onClick = { onSelect(safeIndex - 1) }) { Text(stringResource(R.string.recorder_preview_previous)) }
                        Text(stringResource(R.string.recorder_preview_part, safeIndex + 1, mediaList.size))
                        TextButton(enabled = safeIndex < mediaList.lastIndex, onClick = { onSelect(safeIndex + 1) }) { Text(stringResource(R.string.recorder_preview_next)) }
                    }
                    if (sentPaths.isNotEmpty()) Text(stringResource(R.string.recorder_queue_sent, sentPaths.size, mediaList.size))
                }
                if (sending) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.errorAnnouncement()) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (mediaList.size > 1) stringResource(R.string.recorder_queue_progress, progress, mediaList.size)
                            else stringResource(R.string.recorder_send_in_progress)
                        )
                    }
                }
            }
        },
        confirmText = stringResource(
            when {
                failure != null -> R.string.recorder_retry_send
                mediaList.size > 1 -> R.string.recorder_send_all
                else -> R.string.recorder_send
            }
        ),
        onConfirm = onSend,
        confirmEnabled = !sending,
        dismissText = stringResource(R.string.recorder_delete),
        onDismiss = { if (!sending) onDelete() }
    )
}
