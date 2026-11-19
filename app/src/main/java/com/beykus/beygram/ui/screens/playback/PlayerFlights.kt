package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import kotlin.math.roundToInt

/** The artwork's rect, corner and border at one moment of the flight. */
private class ArtworkFrame(val rect: Rect, val radiusPx: Float, val border: Color)

private fun artworkFrame(p: Float): ArtworkFrame? {
    if (!PlayerExpansion.flies(FlightRole.Artwork)) return null
    val src = PlayerExpansion.currentSource() ?: return null
    val from = src.artwork ?: return null
    val targets = PlayerExpansion.targets
    val to = PlayerExpansion.rectOf(targets.artwork) ?: return null
    return ArtworkFrame(
        rect = lerpRect(from, to, p),
        radiusPx = lerpFloat(src.artworkRadius, targets.artworkRadiusPx, p),
        border = lerp(src.artworkBorder ?: Color.Transparent, targets.artworkBorder ?: Color.Transparent, p)
    )
}

/**
 * The flying artwork: laid out once at the larger end's size and moved with a layer transform. Only the layer is
 * updated per frame, so the cover is never decoded or laid out again during the flight.
 */
private fun GraphicsLayerScope.flightArtworkLayer(bigPx: Float) {
    val frame = artworkFrame(PlayerExpansion.progress)
    if (frame == null) {
        alpha = 0f
        return
    }
    val sx = (frame.rect.width / bigPx).coerceAtLeast(0.001f)
    val sy = (frame.rect.height / bigPx).coerceAtLeast(0.001f)
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = frame.rect.left
    translationY = frame.rect.top
    scaleX = sx
    scaleY = sy
    // The layer is scaled, so the corner radius is expressed in the layer's own units.
    shape = RoundedCornerShape((frame.radiusPx / sx).toDp())
    clip = true
    shadowElevation = 6.dp.toPx() * PlayerExpansion.progress
    alpha = 1f
}

private fun DrawScope.drawFlightArtworkBorder(p: Float) {
    val frame = artworkFrame(p) ?: return
    drawRoundRect(
        color = frame.border,
        topLeft = frame.rect.topLeft,
        size = frame.rect.size,
        cornerRadius = CornerRadius(frame.radiusPx),
        style = Stroke(width = 1.dp.toPx())
    )
}

/**
 * One text that moves from the mini bar into the player. The first lines of both renderings share one anchor at every
 * progress value, so the crossfade reads as one text that grows; extra lines of a long title fade in with the target.
 */
private fun DrawScope.drawFlightText(
    measurer: TextMeasurer,
    fromMaxLines: Int = 1,
    p: Float,
    from: Rect?,
    fromText: String,
    fromStyle: TextStyle,
    to: Rect?,
    toText: String,
    toStyle: TextStyle,
    toMaxLines: Int
) {
    if (from == null || to == null || fromText.isEmpty() || toText.isEmpty()) return
    val m = fromStyle.fontSize.value
    val f = toStyle.fontSize.value
    if (!(m > 0f) || !(f > 0f)) return
    val a = measurer.measure(
        text = fromText,
        style = fromStyle,
        overflow = TextOverflow.Ellipsis,
        maxLines = fromMaxLines,
        constraints = Constraints(maxWidth = from.width.roundToInt().coerceAtLeast(1))
    )
    val b = measurer.measure(
        text = toText,
        style = toStyle,
        overflow = TextOverflow.Ellipsis,
        maxLines = toMaxLines,
        constraints = Constraints(maxWidth = to.width.roundToInt().coerceAtLeast(1))
    )
    val visual = lerpFloat(m, f, p)
    val sa = visual / m
    val sb = visual / f
    val aLeft = a.getLineLeft(0)
    val aBase = a.firstBaseline
    val bLeft = b.getLineLeft(0)
    val bBase = b.firstBaseline
    val anchorX = lerpFloat(from.left + aLeft, to.left + bLeft, p)
    val anchorY = lerpFloat(from.top + aBase, to.top + bBase, p)
    val c = smooth(ramp(p, 0.2f, 0.6f))
    withTransform({
        translate(anchorX - aLeft * sa, anchorY - aBase * sa)
        scale(scaleX = sa, scaleY = sa, pivot = Offset.Zero)
    }) {
        drawText(a, alpha = 1f - c)
    }
    withTransform({
        translate(anchorX - bLeft * sb, anchorY - bBase * sb)
        scale(scaleX = sb, scaleY = sb, pivot = Offset.Zero)
    }) {
        drawText(b, alpha = c)
    }
}

private fun DrawScope.drawFlightPlay(p: Float, painter: Painter) {
    val src = PlayerExpansion.currentSource() ?: return
    val from = src.play ?: return
    val targets = PlayerExpansion.targets
    val to = PlayerExpansion.rectOf(targets.play) ?: return
    val cx = lerpFloat(from.center.x, to.center.x, p)
    val cy = lerpFloat(from.center.y, to.center.y, p)
    drawCircle(
        color = targets.playBackground.copy(alpha = ramp(p, 0.1f, 0.7f)),
        radius = lerpFloat(src.playIconPx / 2f, to.width / 2f, p),
        center = Offset(cx, cy)
    )
    val iconPx = lerpFloat(src.playIconPx, targets.playIconPx, p)
    val tint = lerp(src.playTint, targets.playTint, p)
    withTransform({ translate(cx - iconPx / 2f, cy - iconPx / 2f) }) {
        with(painter) {
            draw(size = Size(iconPx, iconPx), colorFilter = ColorFilter.tint(tint))
        }
    }
}

/** The progress line flies from the mini bar's line to the seek bar; the thumb grows in over the last half. */
private fun DrawScope.drawFlightProgress(p: Float, position: PlaybackPositionUi) {
    val src = PlayerExpansion.currentSource() ?: return
    val from = src.progress ?: return
    val targets = PlayerExpansion.targets
    val seek = PlayerExpansion.rectOf(targets.seek) ?: return
    val inset = 10.dp.toPx()
    val y = lerpFloat(from.center.y, seek.center.y, p)
    val x0 = lerpFloat(from.left, seek.left + inset, p)
    val x1 = lerpFloat(from.right, seek.right - inset, p)
    val span = x1 - x0
    val stroke = lerpFloat(2.dp.toPx(), 4.dp.toPx(), p)
    val cap = if (p < 0.5f) StrokeCap.Butt else StrokeCap.Round
    val accent = lerp(src.progressAccent, targets.seekAccent, p)
    drawLine(
        color = lerp(src.progressTrack, targets.seekTrack, p),
        start = Offset(x0, y),
        end = Offset(x1, y),
        strokeWidth = stroke,
        cap = cap
    )
    drawLine(
        color = accent.copy(alpha = lerpFloat(0.22f, 0.32f, p)),
        start = Offset(x0, y),
        end = Offset(x0 + span * position.bufferedFraction, y),
        strokeWidth = stroke,
        cap = cap
    )
    drawLine(
        color = accent,
        start = Offset(x0, y),
        end = Offset(x0 + span * position.fraction, y),
        strokeWidth = stroke,
        cap = cap
    )
    if (position.durationMs > 0L) {
        val k = ramp(p, 0.55f, 1f)
        val thumb = Offset(x0 + span * position.fraction, y)
        drawCircle(color = accent, radius = 7.dp.toPx() * k, center = thumb)
        drawCircle(color = targets.seekSurface, radius = 3.dp.toPx() * k, center = thumb)
    }
}

/**
 * The shared elements that move between the mini bar and the full player. They are drawn above the player container
 * and exist only while a transition runs. The composable recomposes only when the transition starts or ends; every
 * per-frame value is read in a draw or layer lambda.
 */
@Composable
internal fun PlayerFlights(player: MusicPlayerUi, cover: CoverTokens) {
    if (!PlayerExpansion.flights) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val position = rememberPlaybackPositionState()
    // Read in composition on purpose: it changes once when a transition starts, never per frame.
    val bigPx = PlayerExpansion.flightArtworkPx.coerceAtLeast(1f)
    val icon = when {
        player.failed || player.ended -> Icons.Rounded.Replay
        player.playing || player.intentPlaying -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    val painter = rememberVectorPainter(icon)
    CompositionLocalProvider(LocalBeyGramTokens provides cover.tokens) {
        val palette = beyGramPalette()
        val item = player.item
        val bigDp = with(density) { bigPx.toDp() }
        TrackArtwork(
            artworkPath = item.artworkPath,
            audioPath = item.path.takeIf { it.isNotBlank() },
            modifier = Modifier
                .size(bigDp)
                .graphicsLayer { flightArtworkLayer(bigPx) },
            background = palette.accentSoft,
            tint = palette.accentInk,
            symbolSize = bigDp * 0.22f,
            cornerRadius = 0.dp,
            border = null
        )
        Canvas(Modifier.fillMaxSize()) {
            val p = PlayerExpansion.progress
            val src = PlayerExpansion.currentSource()
            val targets = PlayerExpansion.targets
            drawFlightArtworkBorder(p)
            if (src != null && PlayerExpansion.flies(FlightRole.Title)) {
                drawFlightText(
                    measurer = measurer,
                    p = p,
                    from = src.title,
                    fromText = src.titleText,
                    fromStyle = src.titleStyle,
                    to = PlayerExpansion.rectOf(targets.title),
                    toText = targets.titleText,
                    toStyle = targets.titleStyle,
                    toMaxLines = targets.titleMaxLines
                )
            }
            if (src != null && PlayerExpansion.flies(FlightRole.Artist)) {
                drawFlightText(
                    measurer = measurer,
                    p = p,
                    from = src.artist,
                    fromText = src.artistText,
                    fromStyle = src.artistStyle,
                    to = PlayerExpansion.rectOf(targets.artist),
                    toText = targets.artistText,
                    toStyle = targets.artistStyle,
                    toMaxLines = targets.artistMaxLines
                )
            }
            if (src != null && PlayerExpansion.flies(FlightRole.Play)) drawFlightPlay(p, painter)
            if (src != null && PlayerExpansion.flies(FlightRole.Progress)) drawFlightProgress(p, position.value)
        }
    }
}

/** Where the player's artwork is between the player ([q] = 0) and the queue card ([q] = 1), in [root] pixels. */
private class QueueArtworkFrame(val rect: Rect, val radiusPx: Float, val border: Color)

private fun queueArtworkFrame(root: LayoutCoordinates?, q: Float): QueueArtworkFrame? {
    if (root == null || FlightRole.Artwork !in PlayerExpansion.queueRoles) return null
    val player = PlayerExpansion.targets
    val queue = PlayerExpansion.queueTargets
    val from = player.artwork?.rectIn(root) ?: return null
    val to = queue.artwork?.rectIn(root) ?: return null
    return QueueArtworkFrame(
        rect = lerpRect(from, to, q),
        radiusPx = lerpFloat(player.artworkRadiusPx, queue.artworkRadiusPx, q),
        border = lerp(player.artworkBorder ?: Color.Transparent, queue.artworkBorder ?: Color.Transparent, q)
    )
}

/**
 * The artwork, title and artist that move between the full player and the queue's current-track card while the queue
 * panel moves. Drawn inside the player content, in the coordinates of [rootCoords]; composed only while a queue move
 * has flying roles. Every per-frame value is read in a draw or layer lambda.
 */
@Composable
internal fun QueueFlights(player: MusicPlayerUi, queueProgress: () -> Float, rootCoords: () -> LayoutCoordinates?, artworkPx: Float) {
    val roles = PlayerExpansion.queueRoles
    if (roles.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val palette = beyGramPalette()
    if (FlightRole.Artwork in roles && artworkPx > 0f) {
        val bigDp = with(density) { artworkPx.toDp() }
        TrackArtwork(
            artworkPath = player.item.artworkPath,
            audioPath = player.item.path.takeIf { it.isNotBlank() },
            modifier = Modifier
                .size(bigDp)
                .graphicsLayer {
                    val frame = queueArtworkFrame(rootCoords(), queueProgress())
                    if (frame == null) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val sx = (frame.rect.width / artworkPx).coerceAtLeast(0.001f)
                    val sy = (frame.rect.height / artworkPx).coerceAtLeast(0.001f)
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = frame.rect.left
                    translationY = frame.rect.top
                    scaleX = sx
                    scaleY = sy
                    shape = RoundedCornerShape((frame.radiusPx / sx).toDp())
                    clip = true
                    alpha = 1f
                },
            background = palette.accentSoft,
            tint = palette.accentInk,
            symbolSize = bigDp * 0.22f,
            cornerRadius = 0.dp,
            border = null
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        val root = rootCoords() ?: return@Canvas
        val q = queueProgress()
        queueArtworkFrame(root, q)?.let { frame ->
            drawRoundRect(
                color = frame.border,
                topLeft = frame.rect.topLeft,
                size = frame.rect.size,
                cornerRadius = CornerRadius(frame.radiusPx),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        val from = PlayerExpansion.targets
        val to = PlayerExpansion.queueTargets
        if (FlightRole.Title in roles) {
            drawFlightText(
                measurer = measurer,
                fromMaxLines = from.titleMaxLines,
                p = q,
                from = from.title?.rectIn(root),
                fromText = from.titleText,
                fromStyle = from.titleStyle,
                to = to.title?.rectIn(root),
                toText = to.titleText,
                toStyle = to.titleStyle,
                toMaxLines = 2
            )
        }
        if (FlightRole.Artist in roles) {
            drawFlightText(
                measurer = measurer,
                fromMaxLines = from.artistMaxLines,
                p = q,
                from = from.artist?.rectIn(root),
                fromText = from.artistText,
                fromStyle = from.artistStyle,
                to = to.artist?.rectIn(root),
                toText = to.artistText,
                toStyle = to.artistStyle,
                toMaxLines = 1
            )
        }
    }
}

/** The roles that can fly in a queue move: both ends are laid out now. Measured relative to [root]. */
internal fun queueRolesNow(root: LayoutCoordinates): Set<FlightRole> {
    val player = PlayerExpansion.targets
    val queue = PlayerExpansion.queueTargets
    val roles = mutableSetOf<FlightRole>()
    if (player.artwork?.rectIn(root) != null && queue.artwork?.rectIn(root) != null) roles += FlightRole.Artwork
    if (player.titleText.isNotEmpty() && queue.titleText.isNotEmpty() &&
        player.title?.rectIn(root) != null && queue.title?.rectIn(root) != null
    ) roles += FlightRole.Title
    if (player.artistText.isNotBlank() && queue.artistText.isNotBlank() &&
        player.artist?.rectIn(root) != null && queue.artist?.rectIn(root) != null
    ) roles += FlightRole.Artist
    return roles
}
