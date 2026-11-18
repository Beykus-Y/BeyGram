package com.beykus.beygram.ui.screens.playback

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.TextStyle
import com.beykus.beygram.ui.motion.MotionSpec
import com.beykus.beygram.ui.motion.MotionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the full player is in its move between the mini bar and full size. */
internal enum class PlayerExpansionPhase { Collapsed, Expanding, Expanded, Collapsing, Dragging }

/** What started a drag: the mini bar, the full player itself, or the system back gesture. */
internal enum class PlayerDragOrigin { Mini, Full, Back }

/** The screen edge the mini bar sits on: a bottom bar grows upwards, a top bar grows downwards. */
internal enum class MiniAnchorEdge { Bottom, Top }

/** An element that flies between the mini bar and the full player while the player moves. */
internal enum class FlightRole { Artwork, Title, Artist, Play, Progress }

/** How far the player shrinks at the end of a full predictive-back swipe (progress falls to 1 - this). */
internal const val BACK_PEEK = 0.3f

private const val MIN_TRAVEL_DP = 96f
private const val FLING_DP_PER_SECOND = 1000f
private const val SHEET_MAX_WIDTH_DP = 640f
private const val SHEET_TOP_FRACTION = 0.06f
private const val HANDLE_DP = 48f

/** Clamped linear ramp of [progress] from [start] to [end]. */
internal fun ramp(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

/** Smoothstep of [x] clamped to 0..1. */
internal fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

internal fun lerpRect(start: Rect, stop: Rect, fraction: Float): Rect = Rect(
    left = lerpFloat(start.left, stop.left, fraction),
    top = lerpFloat(start.top, stop.top, fraction),
    right = lerpFloat(start.right, stop.right, fraction),
    bottom = lerpFloat(start.bottom, stop.bottom, fraction)
)

/** The full player's rect in overlay pixels: a sheet at most 640 dp wide, below the status bar and the drag handle. */
internal fun fullPlayerRect(width: Float, height: Float, statusTop: Float, density: Float): Rect {
    val sheetWidth = min(width, SHEET_MAX_WIDTH_DP * density)
    val left = (width - sheetWidth) / 2f
    val top = statusTop + SHEET_TOP_FRACTION * (height - statusTop - HANDLE_DP * density)
    return Rect(left, top, left + sheetWidth, height)
}

/** The visible rect of the player container: it grows out of [start] (the mini bar), or rises from below without one. */
internal fun containerRectAt(progress: Float, start: Rect?, full: Rect): Rect =
    if (start != null) lerpRect(start, full, progress) else full.translate(0f, (1f - progress) * full.height)

/** Finger travel in pixels that takes the player from the mini bar to full size, or back. */
internal fun dragTravelPx(origin: PlayerDragOrigin, edge: MiniAnchorEdge, source: Rect?, full: Rect, density: Float): Float {
    val minTravel = MIN_TRAVEL_DP * density
    if (origin == PlayerDragOrigin.Mini && source != null) {
        return when (edge) {
            MiniAnchorEdge.Bottom -> max(source.top - full.top, minTravel)
            MiniAnchorEdge.Top -> max(full.bottom - source.bottom, minTravel)
        }
    }
    if (source == null) return full.height
    return max(abs(source.top - full.top), full.height * 0.5f)
}

/** +1 when moving the finger down grows the progress (a top bar), -1 otherwise. */
private fun progressSign(origin: PlayerDragOrigin, edge: MiniAnchorEdge): Float =
    if (origin == PlayerDragOrigin.Mini && edge == MiniAnchorEdge.Top) 1f else -1f

/** Progress change for a finger movement of [deltaYPx] (positive = down). */
internal fun progressDelta(origin: PlayerDragOrigin, edge: MiniAnchorEdge, deltaYPx: Float, travelPx: Float): Float {
    if (travelPx <= 0f) return 0f
    return progressSign(origin, edge) * deltaYPx / travelPx
}

/** Where a released drag settles, 1 (full) or 0 (mini). A fast fling decides by direction, a slow release by place. */
internal fun endDragTarget(
    origin: PlayerDragOrigin,
    edge: MiniAnchorEdge,
    progress: Float,
    velocityYPx: Float,
    density: Float
): Float {
    val sign = progressSign(origin, edge)
    if (abs(velocityYPx) > FLING_DP_PER_SECOND * density) return if (sign * velocityYPx > 0f) 1f else 0f
    return if (origin == PlayerDragOrigin.Mini) {
        if (progress > 0.3f) 1f else 0f
    } else {
        if (progress < 0.7f) 0f else 1f
    }
}

/** The mini bar's chrome fades while it is the bar the transition started from. */
internal fun miniChromeAlphaOf(id: Long, activeId: Long?, phase: PlayerExpansionPhase, progress: Float): Float =
    if (activeId == id && phase != PlayerExpansionPhase.Collapsed) 1f - ramp(progress, 0f, 0.3f) else 1f

/** A mini element is hidden only while its own flight draws it, and only after the flight has started moving. */
internal fun miniSharedHiddenOf(
    id: Long,
    role: FlightRole,
    activeId: Long?,
    flights: Boolean,
    phase: PlayerExpansionPhase,
    progress: Float,
    roles: Set<FlightRole>
): Boolean = activeId == id && flights && phase != PlayerExpansionPhase.Collapsed && progress > 0f && role in roles

/** A full-player element is hidden while its flight is in the air, from the start until the flight ends. */
internal fun fullSharedHiddenOf(role: FlightRole, flights: Boolean, phase: PlayerExpansionPhase, roles: Set<FlightRole>): Boolean =
    flights && phase != PlayerExpansionPhase.Expanded && phase != PlayerExpansionPhase.Collapsed && role in roles

/** The rect of this layout in the coordinates of [root], or null while either layout is detached. */
internal fun LayoutCoordinates.rectIn(root: LayoutCoordinates): Rect? {
    if (!isAttached || !root.isAttached) return null
    val origin = root.boundsInWindow().topLeft
    return boundsInWindow().translate(-origin.x, -origin.y)
}

/** The mini bar's geometry frozen for the frames after it has left the composition. */
internal class FlightSource(
    val container: Rect,
    val containerRadius: Float,
    val containerColor: Color,
    val artwork: Rect?,
    val artworkRadius: Float,
    val artworkBorder: Color?,
    val artworkBackground: Color,
    val artworkTint: Color,
    val title: Rect?,
    val titleText: String,
    val titleStyle: TextStyle,
    val artist: Rect?,
    val artistText: String,
    val artistStyle: TextStyle,
    val play: Rect?,
    val playIconPx: Float,
    val playTint: Color,
    val progress: Rect?,
    val progressTrack: Color,
    val progressAccent: Color
)

/** Layout and style of the mini bar's shared elements. Written by the bar, read by the overlay in draw phase only. */
internal class MiniPlayerAnchor(val id: Long) {
    var seq: Long = 0L
    var container: LayoutCoordinates? = null
    var containerRadiusPx: Float = 0f
    var containerColor: Color = Color.Transparent
    var artwork: LayoutCoordinates? = null
    var artworkRadiusPx: Float = 0f
    var artworkBorder: Color? = null
    var artworkBackground: Color = Color.Transparent
    var artworkTint: Color = Color.Transparent
    var title: LayoutCoordinates? = null
    var titleText: String = ""
    var titleStyle: TextStyle = TextStyle.Default
    var subtitle: LayoutCoordinates? = null
    var subtitleText: String = ""
    var subtitleStyle: TextStyle = TextStyle.Default
    var play: LayoutCoordinates? = null
    var playIconPx: Float = 0f
    var playTint: Color = Color.Transparent
    var progress: LayoutCoordinates? = null
    var progressTrack: Color = Color.Transparent
    var progressAccent: Color = Color.Transparent
}

/** Layout and style of the full player's shared elements, registered by the player content for the flights to land on. */
internal class PlayerFlightTargets {
    var artwork: LayoutCoordinates? = null
    var artworkRadiusPx: Float = 0f
    var artworkBackground: Color = Color.Transparent
    var artworkTint: Color = Color.Transparent
    var artworkBorder: Color? = null
    var title: LayoutCoordinates? = null
    var titleText: String = ""
    var titleStyle: TextStyle = TextStyle.Default
    var titleMaxLines: Int = 3
    var artist: LayoutCoordinates? = null
    var artistText: String = ""
    var artistStyle: TextStyle = TextStyle.Default
    var artistMaxLines: Int = 2
    var play: LayoutCoordinates? = null
    var playBackground: Color = Color.Transparent
    var playTint: Color = Color.Transparent
    var playIconPx: Float = 0f
    var seek: LayoutCoordinates? = null
    var seekTrack: Color = Color.Transparent
    var seekAccent: Color = Color.Transparent
    var seekSurface: Color = Color.Transparent
}

/**
 * The one controller of the full player's move. Snapshot state is read in composition only for the few changes a
 * transition makes; [progress] is read only in draw, layout and pointer lambdas. Everything runs on the main thread.
 */
internal object PlayerExpansion {
    private val progressState = mutableFloatStateOf(0f)
    private val phaseState = mutableStateOf(PlayerExpansionPhase.Collapsed)
    private val activeState = mutableStateOf<Long?>(null)
    private val flightsState = mutableStateOf(false)
    private val rolesState = mutableStateOf<Set<FlightRole>>(emptySet())
    private val artworkPxState = mutableFloatStateOf(0f)

    /** 0 = mini player, 1 = full player. */
    val progress: Float get() = progressState.floatValue
    val phase: PlayerExpansionPhase get() = phaseState.value
    /** The mini bar this transition belongs to; null when the transition has no bar (the player rises from below). */
    val activeAnchorId: Long? get() = activeState.value
    /** True while shared elements may fly; it is off once the transition has ended. */
    val flights: Boolean get() = flightsState.value
    /** The shared elements that fly in this transition. */
    val flyingRoles: Set<FlightRole> get() = rolesState.value
    /** Layout width of the flying artwork: the larger of the two covers. */
    val flightArtworkPx: Float get() = artworkPxState.floatValue
    var dragOrigin: PlayerDragOrigin = PlayerDragOrigin.Full
        private set
    /** Set by the host on every composition; a motion that is instant snaps every change. */
    var motion: MotionSpec = MotionSpec(durationMillis = 0, intensity = 0f, easing = MotionToken.PlayerExpand.easing)
    /** The host's scope, which runs the transitions. Null while no host is composed. */
    var scope: CoroutineScope? = null
    var density: Float = 1f
    var statusTopPx: Float = 0f
    /** The overlay root, the full window of the app. Null while no host is composed. */
    var rootCoords: LayoutCoordinates? = null
        internal set
    var anchorLost: Boolean = false
        private set
    var lastSource: FlightSource? = null
        private set
    val anchors = mutableMapOf<Long, MiniPlayerAnchor>()
    val targets = PlayerFlightTargets()
    private val idSource = AtomicLong(0L)
    private var placeCounter = 0L
    private var animJob: Job? = null
    private var dragEdge = MiniAnchorEdge.Bottom
    private var travelPx = 1f

    fun newAnchorId(): Long = idSource.incrementAndGet()

    fun register(anchor: MiniPlayerAnchor) {
        anchors[anchor.id] = anchor
    }

    fun unregister(id: Long) {
        anchors.remove(id)
    }

    fun nextSeq(): Long {
        placeCounter += 1L
        return placeCounter
    }

    /** The mini bar placed last among those still on screen, or null when none is. */
    fun bestAnchor(): MiniPlayerAnchor? = anchors.values.filter { it.isLive() }.maxByOrNull { it.seq }

    /** Whether a drag of [deltaY] on the bar [anchorId] may start the expansion (up from a bottom bar, down from a top one). */
    fun canExpandBy(anchorId: Long, deltaY: Float): Boolean {
        val anchor = liveAnchor(anchorId) ?: return false
        return if (edgeOf(anchor) == MiniAnchorEdge.Bottom) deltaY < 0f else deltaY > 0f
    }

    /** A rect of [coords] in overlay pixels, or null while it is not on screen. */
    fun rectOf(coords: LayoutCoordinates?): Rect? {
        val root = rootCoords ?: return null
        return coords?.rectIn(root)
    }

    /** The full player's rect as the overlay lays it out, from the root's size. */
    fun fullRect(): Rect? {
        val root = rootCoords?.takeIf { it.isAttached } ?: return null
        return fullPlayerRect(root.size.width.toFloat(), root.size.height.toFloat(), statusTopPx, density)
    }

    /** The mini geometry of the running transition: live while its bar is composed, else the last frozen copy. */
    fun currentSource(): FlightSource? {
        val id = activeAnchorId ?: return null
        val root = rootCoords
        val live = if (root != null) liveAnchor(id)?.let { snapshotOf(it, root) } else null
        if (live != null) {
            lastSource = live
            anchorLost = false
            return live
        }
        anchorLost = true
        return lastSource
    }

    /** Opacity of the whole player layer; it fades out when the bar that started it has gone. */
    fun overlayFade(): Float = if (anchorLost) ramp(progress, 0f, 0.35f) else 1f

    fun miniChromeAlpha(id: Long): Float = miniChromeAlphaOf(id, activeAnchorId, phase, progress)

    fun miniSharedHidden(id: Long, role: FlightRole): Boolean =
        miniSharedHiddenOf(id, role, activeAnchorId, flights, phase, progress, flyingRoles)

    fun fullSharedHidden(role: FlightRole): Boolean = fullSharedHiddenOf(role, flights, phase, flyingRoles)

    /** True while [role] is in the air: the same condition as [fullSharedHidden], named for the flights. */
    fun flies(role: FlightRole): Boolean = fullSharedHiddenOf(role, flights, phase, flyingRoles)

    /** Opens the full player from [anchorId], or from the best visible bar when it is null. */
    fun open(anchorId: Long?) {
        when (phase) {
            PlayerExpansionPhase.Expanded, PlayerExpansionPhase.Expanding -> return
            PlayerExpansionPhase.Collapsing, PlayerExpansionPhase.Dragging -> {
                phaseState.value = PlayerExpansionPhase.Expanding
                runAnimation(1f)
                return
            }
            PlayerExpansionPhase.Collapsed -> Unit
        }
        val anchor = liveAnchor(anchorId) ?: bestAnchor()
        activeState.value = anchor?.id
        flightsState.value = anchor != null && !motion.isInstant
        resetFlightState()
        phaseState.value = PlayerExpansionPhase.Expanding
        val launcher = scope
        if (launcher == null || motion.isInstant) {
            finish(1f)
            return
        }
        animJob?.cancel()
        animJob = launcher.launch {
            // The overlay is composed on the next frame and its targets are laid out after the second one.
            withFrameNanos { }
            withFrameNanos { }
            if (phaseState.value != PlayerExpansionPhase.Expanding) return@launch
            refreshRoles()
            animateProgress(1f)
        }
    }

    /** Collapses the full player to a bar. The bar is looked up now: it may have moved, or another bar may show. */
    fun collapse() {
        when (phase) {
            PlayerExpansionPhase.Collapsed, PlayerExpansionPhase.Collapsing -> return
            PlayerExpansionPhase.Expanding, PlayerExpansionPhase.Expanded, PlayerExpansionPhase.Dragging -> Unit
        }
        val live = liveAnchor(activeAnchorId) ?: bestAnchor()
        val keepLost = live == null && activeAnchorId != null && lastSource != null && !motion.isInstant
        if (live != null) activeState.value = live.id else if (!keepLost) activeState.value = null
        flightsState.value = (live != null && !motion.isInstant) || keepLost
        anchorLost = keepLost
        phaseState.value = PlayerExpansionPhase.Collapsing
        runAnimation(0f)
    }

    /** Removes the player at once, without a transition. Used before navigating away and when the track is gone. */
    fun dismissImmediately() {
        animJob?.cancel()
        animJob = null
        progressState.floatValue = 0f
        phaseState.value = PlayerExpansionPhase.Collapsed
        activeState.value = null
        flightsState.value = false
        resetFlightState()
    }

    /**
     * Starts a drag from a mini bar, from the full player (its header or a list at the top) or from the system back
     * gesture. Returns false when a drag from a bar has no bar on screen to expand from.
     */
    fun beginDrag(origin: PlayerDragOrigin, anchorId: Long?): Boolean {
        val allowed = if (origin == PlayerDragOrigin.Mini) {
            phase == PlayerExpansionPhase.Collapsed
        } else {
            phase != PlayerExpansionPhase.Collapsed
        }
        if (!allowed) return false
        val anchor = if (origin == PlayerDragOrigin.Mini) {
            liveAnchor(anchorId) ?: bestAnchor()
        } else {
            liveAnchor(activeAnchorId) ?: bestAnchor()
        }
        if (origin == PlayerDragOrigin.Mini && anchor == null) return false
        animJob?.cancel()
        animJob = null
        val root = rootCoords
        val source = if (anchor != null && root != null) snapshotOf(anchor, root) else null
        dragEdge = if (anchor != null && root != null) edgeOf(anchor) else MiniAnchorEdge.Bottom
        val full = fullRect() ?: fallbackFull()
        dragOrigin = origin
        travelPx = dragTravelPx(origin, dragEdge, source?.container, full, density)
        activeState.value = anchor?.id
        flightsState.value = anchor != null && !motion.isInstant
        resetFlightState()
        phaseState.value = PlayerExpansionPhase.Dragging
        if (flights) refreshRoles()
        return true
    }

    /** Moves a drag by [deltaYPx] (positive = down). Ignored outside a drag. */
    fun dragBy(deltaYPx: Float) {
        if (phase != PlayerExpansionPhase.Dragging) return
        val next = progress + progressDelta(dragOrigin, dragEdge, deltaYPx, travelPx)
        progressState.floatValue = next.coerceIn(0f, 1f)
        refreshRoles()
    }

    /** Predictive back: the player shrinks towards its bar by up to [BACK_PEEK] while the system gesture runs. */
    fun backProgress(fraction: Float) {
        if (phase != PlayerExpansionPhase.Dragging || dragOrigin != PlayerDragOrigin.Back) return
        progressState.floatValue = 1f - BACK_PEEK * fraction.coerceIn(0f, 1f)
        refreshRoles()
    }

    /** Ends a drag: it settles where the release velocity points, or at the nearer end when the velocity is small. */
    fun endDrag(velocityYPx: Float) {
        if (phase != PlayerExpansionPhase.Dragging) return
        val target = endDragTarget(dragOrigin, dragEdge, progress, velocityYPx, density)
        phaseState.value = if (target >= 1f) PlayerExpansionPhase.Expanding else PlayerExpansionPhase.Collapsing
        runAnimation(target)
    }

    private fun liveAnchor(id: Long?): MiniPlayerAnchor? = id?.let { anchors[it] }?.takeIf { it.isLive() }

    private fun MiniPlayerAnchor.isLive(): Boolean {
        val coords = container ?: return false
        return coords.isAttached && coords.size.width > 0 && coords.size.height > 0
    }

    private fun edgeOf(anchor: MiniPlayerAnchor): MiniAnchorEdge {
        val root = rootCoords ?: return MiniAnchorEdge.Bottom
        val rect = anchor.container?.rectIn(root) ?: return MiniAnchorEdge.Bottom
        return if (rect.center.y < root.size.height / 2f) MiniAnchorEdge.Top else MiniAnchorEdge.Bottom
    }

    private fun snapshotOf(anchor: MiniPlayerAnchor, root: LayoutCoordinates): FlightSource? {
        val container = anchor.container?.rectIn(root) ?: return null
        return FlightSource(
            container = container,
            containerRadius = anchor.containerRadiusPx,
            containerColor = anchor.containerColor,
            artwork = anchor.artwork?.rectIn(root),
            artworkRadius = anchor.artworkRadiusPx,
            artworkBorder = anchor.artworkBorder,
            artworkBackground = anchor.artworkBackground,
            artworkTint = anchor.artworkTint,
            title = anchor.title?.rectIn(root),
            titleText = anchor.titleText,
            titleStyle = anchor.titleStyle,
            artist = anchor.subtitle?.rectIn(root),
            artistText = anchor.subtitleText,
            artistStyle = anchor.subtitleStyle,
            play = anchor.play?.rectIn(root),
            playIconPx = anchor.playIconPx,
            playTint = anchor.playTint,
            progress = anchor.progress?.rectIn(root),
            progressTrack = anchor.progressTrack,
            progressAccent = anchor.progressAccent
        )
    }

    /** Adds the roles whose source and target are both on screen. A role stays flying until the transition ends. */
    private fun refreshRoles() {
        if (!flights) return
        val root = rootCoords ?: return
        val source = currentSource() ?: return
        val now = mutableSetOf<FlightRole>()
        val artTo = targets.artwork?.rectIn(root)
        val artFrom = source.artwork
        if (artFrom != null && artTo != null) {
            now += FlightRole.Artwork
            // Locked for the transition: the target's scale animation would otherwise resize the flight every frame.
            if (artworkPxState.floatValue <= 0f) artworkPxState.floatValue = max(artFrom.width, artTo.width)
        }
        if (source.title != null && targets.title?.rectIn(root) != null) now += FlightRole.Title
        if (source.artist != null && targets.artist?.rectIn(root) != null) now += FlightRole.Artist
        if (source.play != null && targets.play?.rectIn(root) != null) now += FlightRole.Play
        if (source.progress != null && targets.seek?.rectIn(root) != null) now += FlightRole.Progress
        val merged = flyingRoles + now
        if (merged != flyingRoles) rolesState.value = merged
    }

    private fun resetFlightState() {
        rolesState.value = emptySet()
        artworkPxState.floatValue = 0f
        lastSource = null
        anchorLost = false
    }

    private fun fallbackFull(): Rect = fullPlayerRect(400f * density, 1000f * density, 0f, density)

    /** Moves to [target] from where the transition is now. The caller has already set the phase. */
    private fun runAnimation(target: Float) {
        animJob?.cancel()
        val launcher = scope
        if (launcher == null || motion.isInstant) {
            animJob = null
            finish(target)
            return
        }
        animJob = launcher.launch {
            refreshRoles()
            animateProgress(target)
        }
    }

    private suspend fun animateProgress(target: Float) {
        val start = progress
        val distance = abs(target - start)
        if (distance < 0.0005f) {
            finish(target)
            return
        }
        val duration = max(1, (motion.durationMillis * distance).roundToInt())
        animate(
            initialValue = start,
            targetValue = target,
            animationSpec = tween<Float>(durationMillis = duration, easing = motion.easing)
        ) { value, _ ->
            progressState.floatValue = value
            refreshRoles()
        }
        finish(target)
    }

    /** The transition has reached [target]: the phase and the flights change in the same frame. */
    private fun finish(target: Float) {
        progressState.floatValue = target
        flightsState.value = false
        resetFlightState()
        if (target >= 1f) {
            phaseState.value = PlayerExpansionPhase.Expanded
        } else {
            phaseState.value = PlayerExpansionPhase.Collapsed
            activeState.value = null
        }
    }
}
