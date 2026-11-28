package com.beykus.beygram.ui.screens.chat

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import java.io.File

/**
 * Plays the freshly recorded video note inside the preview dialog (#1228).
 *
 * `VideoView` draws through a `SurfaceView`, which is a separate window layer: inside a dialog it stays black on several
 * devices (the reported Samsung M21 among them) even when the file is fine, and it reports nothing when it fails. A
 * Media3 player drawing into a `TextureView` lives in the dialog's own view hierarchy, so the frame is composited with
 * the rest of the dialog instead of falling behind it.
 *
 * A file that really cannot be decoded is not hidden: the player error replaces the (then pointless) black rectangle
 * with a poster frame and an explicit line, so the user sees that the recording is unreadable instead of a silent black
 * box.
 *
 * The preview starts paused, exactly like the 0.0.6.3 `VideoView`: opening the dialog must not start talking over music.
 * The preview acquires message playback policy on an explicit tap and releases it when playback stops.
 */
@Composable
internal fun VideoNotePreview(
    path: String,
    posterPath: String?,
    modifier: Modifier = Modifier,
    item: PlaybackItem? = null,
    active: Boolean = true,
    onStarted: () -> Unit = {},
) {
    val context = LocalContext.current
    val previewKey = item?.key ?: remember(path) { java.util.UUID.randomUUID().toString() }
    val owner = remember(previewKey) { Any() }
    val speech by PlaybackEngine.speechState.collectAsStateWithLifecycle()
    val preview = speech.takeIf { active && it.item?.key == previewKey }
    var consumed by remember(previewKey) { mutableStateOf(false) }
    val currentOnStarted by rememberUpdatedState(onStarted)
    val playing = preview?.intentPlaying == true
    val ready = preview?.started == true
    val playbackError = preview?.failed == true || !remember(path) { File(path).isFile }
    val ownsSurface = active && preview != null && !playbackError
    DisposableEffect(previewKey, owner, ownsSurface) {
        if (ownsSurface) VideoNoteInlineSurfaceRegistry.register(owner)
        onDispose {
            if (ownsSurface) {
                PlaybackEngine.detachSpeechVideo(owner)
                VideoNoteInlineSurfaceRegistry.unregister(owner)
            }
        }
    }
    DisposableEffect(previewKey, active) {
        onDispose { if (active) PlaybackEngine.stopPrivateSpeech(previewKey) }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (!playbackError && preview != null) {
            // `key(path)` rebuilds the view for another segment, and `update` re-attaches the surface when the player
            // itself is recreated: an AndroidView keeps its view across recompositions, so without both a second
            // segment would play with sound and a black picture (Codex review of #1231).
            key(path) {
                AndroidView(
                    factory = { context ->
                        TextureView(context).also { view -> PlaybackEngine.attachSpeechVideo(previewKey, owner, view) }
                    },
                    update = { view -> PlaybackEngine.attachSpeechVideo(previewKey, owner, view) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Until the first decoded frame (and forever, if the file is unreadable) the poster stands in for the video.
        if (playbackError || !ready) {
            val poster = posterPath?.takeIf { File(it).isFile }
            if (poster != null) {
                LocalMediaImage(poster, Modifier.fillMaxSize(), fit = true, animate = false)
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        if (playbackError) {
            Text(
                text = stringResource(R.string.recorder_preview_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
        if (active && !playbackError && !(consumed && preview == null)) VideoNotePlaybackControl(
            playing = playing,
            onToggle = {
                if (item == null) {
                    PlaybackEngine.toggleSpeechPreview(context, previewKey, MediaKind.VideoNote, path, 0)
                } else {
                    PlaybackEngine.toggle(context, item, onStarted = {
                        if (item.viewOnce) consumed = true
                        currentOnStarted()
                    })
                }
            },
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * The preview's only two states: tap anywhere to toggle, and a corner button that tells TalkBack whether the note is
 * playing. It is a separate composable so the state labels and the toggle can be tested without a decoder.
 */
@Composable
internal fun VideoNotePlaybackControl(
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(if (playing) R.string.common_pause else R.string.common_play)
    Box(modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onToggle)) {
        Box(
            Modifier.align(Alignment.BottomEnd).padding(12.dp).size(44.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}