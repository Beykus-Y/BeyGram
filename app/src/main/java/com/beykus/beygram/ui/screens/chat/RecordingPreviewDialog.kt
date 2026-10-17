package com.beykus.beygram.ui.screens.chat

import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia

@Composable
internal fun RecordingPreviewDialog(
    mediaList: List<OutgoingMedia>,
    selectedIndex: Int,
    sentPaths: Set<String>,
    sending: Boolean,
    progress: Int,
    onSelect: (Int) -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit
) {
    val safeIndex = selectedIndex.coerceIn(0, mediaList.lastIndex.coerceAtLeast(0))
    val media = mediaList[safeIndex]
    val context = LocalContext.current
    var prepared by remember(media.path) { mutableStateOf(false) }
    var playing by remember(media.path) { mutableStateOf(false) }
    val player = remember(media.path) {
        if (media.kind != MediaKind.Voice) null else runCatching {
            MediaPlayer().apply {
                setDataSource(media.path)
                setOnPreparedListener { prepared = true }
                setOnCompletionListener { playing = false; seekTo(0) }
                prepareAsync()
            }
        }.getOrNull()
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.recorder_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mediaList.size > 1) {
                    Text(stringResource(R.string.recorder_preview_batch, mediaList.size, duration(mediaList.sumOf { it.durationSeconds })))
                    Text(stringResource(R.string.recorder_preview_size, formatBytes(mediaList.sumOf { it.size })))
                }
                Text(stringResource(R.string.recorder_preview_duration, duration(media.durationSeconds)))
                if (media.kind == MediaKind.VideoNote) {
                    key(media.path) {
                        AndroidView(
                            factory = { VideoView(it).apply {
                                setVideoPath(media.path)
                                setMediaController(MediaController(context))
                            } },
                            modifier = Modifier.fillMaxWidth().height(280.dp)
                        )
                    }
                } else {
                    Button(enabled = prepared, onClick = {
                        if (playing) { player?.pause(); playing = false }
                        else { player?.start(); playing = true }
                    }) { Text(stringResource(if (playing) R.string.recorder_preview_pause else R.string.recorder_preview_play)) }
                }
                if (mediaList.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = safeIndex > 0, onClick = { onSelect(safeIndex - 1) }) { Text(stringResource(R.string.recorder_preview_previous)) }
                        Text(stringResource(R.string.recorder_preview_part, safeIndex + 1, mediaList.size))
                        TextButton(enabled = safeIndex < mediaList.lastIndex, onClick = { onSelect(safeIndex + 1) }) { Text(stringResource(R.string.recorder_preview_next)) }
                    }
                    if (sentPaths.isNotEmpty()) Text(stringResource(R.string.recorder_queue_sent, sentPaths.size, mediaList.size))
                    if (sending) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.recorder_queue_progress, progress, mediaList.size))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = !sending, onClick = onSend) { Text(stringResource(if (mediaList.size > 1) R.string.recorder_send_all else R.string.recorder_send)) } },
        dismissButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.recorder_delete)) } }
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}
