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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.media.bgbeat.BgMediaEligibility
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs

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
    val buffer = remember { MascotFrameBuffer() }
    val debugLines = remember { mutableStateOf(emptyList<String>()) }
    var fps by remember { mutableIntStateOf(0) }
    var frameMs by remember { mutableStateOf(0.0f) }
    var seekAtNanos by remember { mutableLongStateOf(0L) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var transitionAtNanos by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(item.key, enabled, resumed) {
        manager.setVisible(resumed)
        onDispose { manager.setVisible(false) }
    }

    val ready = (uiState as? BgMascotUiState.Ready)?.takeIf { it.itemKey == item.key }
    val eligible = remember(item.key, item.path) { BgMediaEligibility.reasonFor(item) == null }
    val showStage = when (uiState) {
        is BgMascotUiState.Ready -> ready != null
        is BgMascotUiState.Preparing,
        is BgMascotUiState.Idle,
        is BgMascotUiState.Error -> eligible
        else -> false
    }

    LaunchedEffect(item.key) {
        seekAtNanos = 0L
        seekDirection = 0
        transitionAtNanos = System.nanoTime()
    }
    LaunchedEffect(ready?.handle) {
        // The dance joins after the analysis delay; ring it in so the hand-off
        // from the idle loop is visible instead of a pose pop.
        if (ready != null) transitionAtNanos = System.nanoTime()
    }
    LaunchedEffect(playing) {
        // Pause/resume is also a stage transition, not a hard cut.
        transitionAtNanos = System.nanoTime()
    }
    LaunchedEffect(item.key, playing) {
        // Collect positions away from composition: this effect does not make the
        // stage recompose four times a second just to detect a seek.
        var lastPosition = PlaybackEngine.state.value.positionMs
        PlaybackEngine.state
            .map { it.positionMs }
            .distinctUntilChanged()
            .collect { position ->
                val delta = position - lastPosition
                val threshold = if (playing) 800L else 50L
                if (delta != 0L && abs(delta) >= threshold) {
                    seekAtNanos = System.nanoTime()
                    seekDirection = if (delta > 0L) 1 else -1
                }
                lastPosition = position
            }
    }

    LaunchedEffect(
        item.key,
        ready?.handle,
        playing,
        canAnimate,
        lifecycleOwner,
        showStage,
        uiState
    ) {
        if (!showStage) {
            buffer.values.fill(0f)
            buffer.publish()
            debugLines.value = emptyList()
            return@LaunchedEffect
        }
        if (!canAnimate) {
            // Reduced motion: a static pose, no idle breathing and no seek pulse.
            buffer.values.fill(0f)
            if (ready != null) {
                sampleOnce(manager, buffer, System.nanoTime(), 0L, 0, 0L)
                debugLines.value = debugLinesFor(buffer, ready, 0, 0f)
            } else {
                buffer.publish()
                debugLines.value = idleDebugLines(uiState, paused = false)
            }
            return@LaunchedEffect
        }
        if (ready == null || !playing) {
            // Analysis is still running, or playback is paused/stopped. Keep the
            // rig alive with a quiet idle loop instead of freezing on a pose.
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                fps = 0
                frameMs = 0f
                runIdleFrames(
                    buffer = buffer,
                    publishDebug = { debugLines.value = it },
                    label = { idleDebugLines(uiState, paused = ready != null) },
                    seekAt = { seekAtNanos },
                    seekDirection = { seekDirection },
                    transitionAt = { transitionAtNanos }
                )
            }
            return@LaunchedEffect
        }

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            fps = 0
            frameMs = 0f

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
                    sampleOnce(
                        manager,
                        buffer,
                        now,
                        seekAtNanos,
                        seekDirection,
                        transitionAtNanos
                    )
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
    }

    // The cover remains behind the rig; the idle state is a translucent scrim, not
    // a replacement for the album art.
    if (showStage) {
        Box(modifier) {
            MascotTestRig(
                buffer = buffer,
                modifier = Modifier.matchParentSize(),
                debugLines = { debugLines.value },
                showDebug = debugOverlay
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MascotToggleChip(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { BgMascotSettingsStore.getInstance(context) }
    val enabled by settings.enabled.collectAsStateWithLifecycle()
    val showInPlayer by settings.showInPlayer.collectAsStateWithLifecycle()
    val debugOverlay by settings.debugOverlay.collectAsStateWithLifecycle()
    if (!enabled) return

    val palette = beyGramPalette()
    val description = stringResource(if (showInPlayer) R.string.player_mascot_hide else R.string.player_mascot_show)
    val debugLabel = stringResource(R.string.settings_mascot_debug_title)
    Surface(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .combinedClickable(
                onClick = { settings.setShowInPlayer(context, !showInPlayer) },
                onLongClickLabel = debugLabel,
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
    nowNanos: Long,
    seekAtNanos: Long,
    seekDirection: Int,
    transitionAtNanos: Long
) {
    manager.sampleInto(buffer.values, manager.mediaTimeSeconds(nowNanos))
    buffer.values[MascotFrameLayout.SEEK_PULSE] = seekPulseAt(nowNanos, seekAtNanos)
    buffer.values[MascotFrameLayout.SEEK_DIRECTION] = seekDirection.toFloat()
    buffer.values[MascotFrameLayout.TRANSITION_PULSE] = transitionPulseAt(nowNanos, transitionAtNanos)
    buffer.publish()
}

private suspend fun CoroutineScope.runIdleFrames(
    buffer: MascotFrameBuffer,
    publishDebug: (List<String>) -> Unit,
    label: () -> List<String>,
    seekAt: () -> Long,
    seekDirection: () -> Int,
    transitionAt: () -> Long
) {
    val idleStart = System.nanoTime()
    var lastPublish = 0L
    var lastDebug = 0L
    buffer.values.fill(0f)
    while (isActive) {
        withFrameNanos { now ->
            if (now - lastPublish >= 33_000_000L) {
                val phase = (((now - idleStart) / 1_000_000_000.0) * 0.32)
                    .toFloat() % 1f
                buffer.values.fill(0f)
                buffer.values[MascotFrameLayout.PHASE] = phase
                buffer.values[MascotFrameLayout.ANALYZED] = 0f
                buffer.values[MascotFrameLayout.SEEK_PULSE] = seekPulseAt(now, seekAt())
                buffer.values[MascotFrameLayout.SEEK_DIRECTION] = seekDirection().toFloat()
                buffer.values[MascotFrameLayout.TRANSITION_PULSE] =
                    transitionPulseAt(now, transitionAt())
                buffer.publish()
                lastPublish = now
                if (now - lastDebug >= 250_000_000L) {
                    lastDebug = now
                    publishDebug(label())
                }
            }
        }
    }
}

private fun seekPulseAt(nowNanos: Long, seekAtNanos: Long): Float {
    if (seekAtNanos <= 0L) return 0f
    val age = nowNanos - seekAtNanos
    if (age !in 0L..600_000_000L) return 0f
    return (1f - age / 600_000_000f).coerceIn(0f, 1f)
}

private fun transitionPulseAt(nowNanos: Long, transitionAtNanos: Long): Float {
    if (transitionAtNanos <= 0L) return 0f
    val age = nowNanos - transitionAtNanos
    if (age !in 0L..900_000_000L) return 0f
    return (1f - age / 900_000_000f).coerceIn(0f, 1f)
}

private fun idleDebugLines(state: BgMascotUiState, paused: Boolean): List<String> {
    if (paused) {
        return listOf(
            "PAUSED · IDLE",
            "the rig breathes while playback is stopped",
            "seek is shown as a quick stage pulse"
        )
    }
    return when (state) {
        is BgMascotUiState.Preparing -> listOf(
            "ANALYZING TRACK",
            "the rig is listening…",
            "it will join the beat when ready"
        )
        is BgMascotUiState.Error -> listOf(
            "IDLE",
            "analysis unavailable",
            "showing the neutral pose"
        )
        else -> listOf(
            "IDLE",
            "waiting for track analysis"
        )
    }
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
        "BPM %.1f · bar %d · beat %d/%d",
        v[MascotFrameLayout.BPM],
        v[MascotFrameLayout.BAR].toInt(),
        v[MascotFrameLayout.BEAT_IN_BAR].toInt(),
        v[MascotFrameLayout.BEATS_PER_BAR].toInt().coerceIn(1, 8)
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
