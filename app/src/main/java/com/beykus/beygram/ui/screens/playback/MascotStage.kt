package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.media.bgmotion.BgMascotManager
import com.beykus.beygram.media.bgmotion.BgMascotSettingsStore
import com.beykus.beygram.media.bgmotion.BgMascotUiState
import com.beykus.beygram.media.bgmotion.MascotFrameBuffer
import com.beykus.beygram.media.bgmotion.MascotFrameLayout
import com.beykus.beygram.media.bgmotion.MascotMove
import com.beykus.beygram.media.bgmotion.MascotSectionKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.ui.accessibility.rememberSystemReducedMotion
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.LocalMotionSettings
import com.beykus.beygram.ui.motion.MotionCategory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Temporary Phase 0/1 overlay. It only proves that BG Motion 2.0 follows the
 * player clock; the visual is a debug mannequin, not the future VRM mascot.
 */
@Composable
internal fun MascotOverlay(
    item: PlaybackItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { BgMascotSettingsStore.getInstance(context) }
    remember(context) { BgMascotSettingsStore.load(context) }
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    val showInPlayer by settings.showInPlayer.collectAsStateWithLifecycle()
    if (!enabled || !showInPlayer) return

    val manager = remember { BgMascotManager.getInstance(context) }
    val uiState by manager.uiState.collectAsStateWithLifecycle()
    val debugOverlay by settings.debugOverlay.collectAsStateWithLifecycle()
    val reducedMotion = rememberSystemReducedMotion()
    val motion = LocalMotionSettings.current
    val canAnimate = motion.enabled &&
        MotionCategory.Media !in motion.disabledCategories &&
        !reducedMotion

    // A tiny selector: the stage only needs play/pause, not the 250 ms position stream.
    val playingFlow = remember { PlaybackEngine.state.map { it.playing }.distinctUntilChanged() }
    val playing by playingFlow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.playing)
    // While paused, a seek still has to move the static pose. While playing, the
    // derived key stays 0, so the 250 ms position stream does not recompose the stage.
    val positionFlow = remember { PlaybackEngine.state.map { it.positionMs }.distinctUntilChanged() }
    val positionMs by positionFlow.collectAsStateWithLifecycle(initialValue = PlaybackEngine.state.value.positionMs)
    val staticPositionKey by remember {
        derivedStateOf { if (playing) 0L else positionMs }
    }

    val buffer = remember { MascotFrameBuffer() }
    val debugLines = remember { mutableStateOf(emptyList<String>()) }
    var fps by remember { mutableIntStateOf(0) }
    var frameMs by remember { mutableStateOf(0.0f) }

    DisposableEffect(item.key, enabled) {
        manager.setVisible(true)
        onDispose { manager.setVisible(false) }
    }

    val ready = uiState as? BgMascotUiState.Ready
    LaunchedEffect(item.key, ready?.handle, playing, canAnimate, staticPositionKey) {
        if (ready == null) {
            debugLines.value = emptyList()
            return@LaunchedEffect
        }
        if (!canAnimate || !playing) {
            // Static pose while paused or when motion is disabled. One sample is
            // enough; there is no reason to wake the CPU/GPU every frame.
            sampleOnce(manager, buffer, System.nanoTime())
            debugLines.value = debugLinesFor(buffer, ready, 0, 0f)
            fps = 0
            frameMs = 0f
            return@LaunchedEffect
        }

        var lastFrame = 0L
        var lastFrameMs = 0f
        var framesInWindow = 0
        var windowStart = 0L
        var lastDebugUpdate = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (lastFrame != 0L) {
                    lastFrameMs = (now - lastFrame) / 1_000_000.0f
                }
                lastFrame = now
                sampleOnce(manager, buffer, now)
                framesInWindow++
                if (windowStart == 0L) windowStart = now
                val elapsed = now - windowStart
                if (elapsed >= 500_000_000L) {
                    fps = ((framesInWindow * 1_000_000_000L) / elapsed).toInt()
                    framesInWindow = 0
                    windowStart = now
                }
                if (now - lastDebugUpdate >= 250_000_000L) {
                    lastDebugUpdate = now
                    frameMs = lastFrameMs
                    debugLines.value = debugLinesFor(buffer, ready, fps, lastFrameMs)
                }
            }
        }
    }

    Box(modifier) {
        MascotTestRig(
            buffer = buffer,
            modifier = Modifier.matchParentSize(),
            debugLines = { debugLines.value },
            showDebug = debugOverlay
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MascotToggleChip(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { BgMascotSettingsStore.getInstance(context) }
    remember(context) { BgMascotSettingsStore.load(context) }
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    val showInPlayer by settings.showInPlayer.collectAsStateWithLifecycle()
    val debugOverlay by settings.debugOverlay.collectAsStateWithLifecycle()
    if (!enabled) return

    val palette = beyGramPalette()
    val description = stringResource(if (showInPlayer) R.string.player_mascot_hide else R.string.player_mascot_show)
    Surface(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .combinedClickable(
                onClick = { settings.setShowInPlayer(context, !showInPlayer) },
                onLongClick = { settings.setDebugOverlay(context, !debugOverlay) }
            ),
        shape = CircleShape,
        color = palette.surface.copy(alpha = 0.78f),
        contentColor = if (debugOverlay) palette.accent else palette.textPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

private fun sampleOnce(
    manager: BgMascotManager,
    buffer: MascotFrameBuffer,
    nowNanos: Long
) {
    manager.sampleInto(buffer.values, manager.mediaTimeSeconds(nowNanos))
    buffer.publish()
}

private fun debugLinesFor(
    buffer: MascotFrameBuffer,
    ready: BgMascotUiState.Ready,
    fps: Int,
    frameMs: Float
): List<String> {
    val v = buffer.values
    val info = ready.info
    val move = MascotMove.fromOrdinal(v[MascotFrameLayout.MOVE_ORDINAL])
    val section = MascotSectionKind.fromOrdinal(v[MascotFrameLayout.SECTION_KIND])
    val phrase = v[MascotFrameLayout.PHRASE_LOUDNESS]
    val lines = mutableListOf<String>()
    lines += if (fps > 0) {
        String.format(Locale.US, "%d fps · %.1f ms", fps, frameMs)
    } else {
        "paused/static"
    }
    lines += String.format(
        Locale.US,
        "BPM %.1f · bar %d · beat %d",
        v[MascotFrameLayout.BPM],
        v[MascotFrameLayout.BAR].toInt(),
        v[MascotFrameLayout.BEAT_IN_BAR].toInt()
    )
    lines += String.format(
        Locale.US,
        "phrase %s · accent %.2f · bass %.2f",
        if (phrase < 0f) "—" else String.format(Locale.US, "%.2f", phrase),
        v[MascotFrameLayout.ACCENT],
        v[MascotFrameLayout.BASS]
    )
    lines += String.format(
        Locale.US,
        "section %s %.2f · move %s",
        section.name,
        v[MascotFrameLayout.SECTION_ENERGY],
        move.name
    )
    lines += "cache ${if (info.cacheHit) "HIT" else "MISS"} · beats ${info.beatCount} · sections ${info.sectionCount}"
    lines += "analysis handles ${info.analysisHandles} · sessions ${info.sessions}"
    lines += String.format(
        Locale.US,
        "intensity %.0f%% · analyzed %s",
        v[MascotFrameLayout.INTENSITY] * 100f,
        if (v[MascotFrameLayout.ANALYZED] > 0.5f) "yes" else "no"
    )
    return lines
}
