package com.beykus.beygram.ui.screens.chat

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** No retained image bytes or paths: the actual outgoing bubble supplies the visual. */
internal data class ComposerMediaCapture(val bounds: Rect, val capturedAt: Long)
internal data class ComposerMediaLaunch(val messageId: Long, val capture: ComposerMediaCapture)

/** One short-lived visual flight per chat. Transport success is independent of this optional effect. */
@Stable
internal class ComposerMediaLaunchController {
    private val previews = mutableMapOf<String, LayoutCoordinates>()
    var active by mutableStateOf<List<ComposerMediaLaunch>>(emptyList())
        private set

    fun record(stagingKey: String, coordinates: LayoutCoordinates) {
        previews[stagingKey] = coordinates
    }

    fun forget(stagingKey: String) {
        previews.remove(stagingKey)
    }

    fun capture(media: List<OutgoingMedia>): List<ComposerMediaCapture?> {
        val now = SystemClock.elapsedRealtime()
        return media.map { item ->
            if (item.kind !in setOf(MediaKind.Photo, MediaKind.Video) || item.stagingKey.isBlank()) null
            else previews[item.stagingKey]?.fullyVisibleBounds()?.let { ComposerMediaCapture(it, now) }
        }
    }

    fun accept(captures: List<ComposerMediaCapture?>, messageIds: List<Long>) {
        active = matchComposerMediaReceipts(captures, messageIds, SystemClock.elapsedRealtime())
    }

    fun finish(launch: ComposerMediaLaunch) {
        active = active.filterNot { it === launch }
    }
}

/** Receipts and captures must have the same cardinality; preserve empty positions for offscreen tiles. */
internal fun matchComposerMediaReceipts(
    captures: List<ComposerMediaCapture?>, messageIds: List<Long>, now: Long
): List<ComposerMediaLaunch> {
    if (captures.size != messageIds.size || messageIds.any { it == 0L } ||
        messageIds.distinct().size != messageIds.size) return emptyList()
    return captures.zip(messageIds).mapNotNull { (capture, id) ->
        capture?.takeIf { now - it.capturedAt in 0L..750L }?.let { ComposerMediaLaunch(id, it) }
    }
}

private fun LayoutCoordinates.fullyVisibleBounds(): Rect? {
    if (!isAttached || size.width <= 0 || size.height <= 0) return null
    val bounds = boundsInWindow()
    return bounds.takeIf {
        it.width.isFinite() && it.height.isFinite() &&
            abs(it.width - size.width) <= 2f && abs(it.height - size.height) <= 2f &&
            it.width > 0f && it.height > 0f
    }
}

internal data class ComposerMediaTransform(val dx: Float, val dy: Float, val scale: Float)

internal fun composerMediaTransform(source: Rect, destination: Rect): ComposerMediaTransform? {
    if (source.width <= 0f || source.height <= 0f || destination.width <= 0f || destination.height <= 0f) return null
    val scale = minOf(source.width / destination.width, source.height / destination.height)
    val dx = source.center.x - destination.center.x
    val dy = source.center.y - destination.center.y
    if (!scale.isFinite() || !dx.isFinite() || !dy.isFinite() || scale <= 0f) return null
    return ComposerMediaTransform(dx, dy, scale.coerceAtMost(1f))
}

/**
 * Move the real measured bubble toward its final position; never create a phantom message or decode a second copy.
 * A clipped/unmeasured row, a slow receipt, or reduced motion keeps the normal message appearance.
 */
@Composable
internal fun composerMediaLaunchModifier(
    launch: ComposerMediaLaunch?,
    onFinished: (ComposerMediaLaunch) -> Unit
): Modifier {
    if (launch == null) return Modifier
    val motion = rememberMotionSpec(MotionToken.MediaExpand)
    val latestFinished by rememberUpdatedState(onFinished)
    var transform by remember(launch) { mutableStateOf<ComposerMediaTransform?>(null) }
    val progress = remember(launch) { Animatable(0f) }
    LaunchedEffect(launch, motion.isInstant) {
        if (!motion.isInstant) {
            val target = withTimeoutOrNull(250L) {
                snapshotFlow { transform }.filterNotNull().first()
            }
            if (target != null) progress.animateTo(1f, motion.animationSpec())
        }
        latestFinished(launch)
    }
    if (motion.isInstant) return Modifier
    return Modifier.onGloballyPositioned { coordinates ->
        if (transform == null) {
            coordinates.fullyVisibleBounds()?.let { target ->
                transform = composerMediaTransform(launch.capture.bounds, target)
            }
        }
    }.graphicsLayer {
        transform?.let { start ->
            val remaining = 1f - progress.value
            scaleX = 1f + (start.scale - 1f) * remaining
            scaleY = scaleX
            translationX = start.dx * remaining
            translationY = start.dy * remaining
        }
    }
}
