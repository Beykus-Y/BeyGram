package com.beykus.beygram.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The continuous chat ↔ profile transition (#999). One flight moves the avatar, the name and the
 * status between the real coordinates of the chat header and the real coordinates of the profile
 * header. Both screens keep composing in the existing route [androidx.compose.animation.AnimatedContent];
 * only the shared elements are hidden while the overlay carries them, so the conversation list is
 * never redrawn for the avatar and no second interactive copy of an element ever exists.
 *
 * Progress is one number for every related element: 0 is the chat state, 1 is the profile state.
 * A push animates 0 → 1, a pop 1 → 0, a predictive back gesture writes the value directly, and a
 * gesture cancel animates back to 1. Reversals interrupt in place from the current value.
 */

/** Window identity a geometry report was captured with; stale reports must never drive a flight. */
@Immutable
data class ChatProfileWindowFingerprint(val windowWidthPx: Int, val windowHeightPx: Int, val density: Float)

internal fun fingerprintMatches(captured: ChatProfileWindowFingerprint, current: ChatProfileWindowFingerprint): Boolean =
    captured.windowWidthPx == current.windowWidthPx &&
        captured.windowHeightPx == current.windowHeightPx &&
        captured.density == current.density

/** Bounds of the shared elements of one side, in window coordinates; null means "not placed". */
@Immutable
data class ChatProfileElementGeometry(
    val avatar: Rect? = null,
    val title: Rect? = null,
    val status: Rect? = null
) {
    val usable: Boolean get() = avatar != null
}

/** Linear sub-progress of [progress] clamped into the [start, end] window. */
internal fun windowProgress(progress: Float, start: Float, end: Float): Float {
    if (end <= start) return if (progress >= end) 1f else 0f
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

/** Real chat-header visibility: hidden once the flight leaves the chat, back for the landing. */
internal fun chatSideVisibility(progress: Float): Float = 1f - windowProgress(progress, 0.02f, 0.14f)

/** Real profile-header visibility: appears as the flight lands in the profile state. */
internal fun profileSideVisibility(progress: Float): Float = windowProgress(progress, 0.86f, 0.98f)

/**
 * Overlay visibility: the flying elements exist only between the two ramps, where both real
 * headers are hidden. At the exact endpoints the real elements carry the visuals, so the swap
 * between the overlay and the real header is pixel-identical and never duplicates an element.
 */
internal fun overlayVisibility(progress: Float): Float =
    windowProgress(progress, 0.02f, 0.14f) * (1f - windowProgress(progress, 0.86f, 0.98f))

/** The flight windows, as fractions of the whole flight. Kept in one place for tests and tuning. */
internal object ChatProfileFlightWindows {
    /** The avatar flies through the first 94% of the flight (issue: 0–300 ms of ~320 ms). */
    const val AvatarEnd = 0.94f
    /** The name and status move between these fractions (issue: 40–280 ms of ~320 ms). */
    const val TextStart = 0.125f
    const val TextEnd = 0.875f
}

/** Eased progress of one element inside its window. */
internal fun easedWindowProgress(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(windowProgress(progress, start, end))

internal fun lerpRect(from: Rect, to: Rect, fraction: Float): Rect = Rect(
    left = from.left + (to.left - from.left) * fraction,
    top = from.top + (to.top - from.top) * fraction,
    right = from.right + (to.right - from.right) * fraction,
    bottom = from.bottom + (to.bottom - from.bottom) * fraction
)

/**
 * Collapsed fraction of the profile header from the live scroll offset: 0 at the very top,
 * 1 once the header block has scrolled under the toolbar. Proportional to the real offset;
 * no timed animation is started per scroll change.
 */
internal fun collapseFractionForScroll(scrollPx: Int, headerHeightPx: Int): Float {
    if (headerHeightPx <= 0) return 0f
    return (scrollPx.toFloat() / headerHeightPx).coerceIn(0f, 1f)
}

/**
 * The visual bounds of a header avatar that a [collapse] scroll has shrunk around its center:
 * the layout bounds do not change when the avatar scales, so reports must convert them back.
 */
internal fun Rect.shrunkAroundCenter(collapse: Float, maxShrink: Float): Rect {
    val clamped = collapse.coerceIn(0f, 1f)
    if (clamped <= 0f || maxShrink <= 0f) return this
    val scale = 1f - maxShrink * clamped
    val horizontal = width / 2f * scale
    val vertical = height / 2f * scale
    val centerX = left + width / 2f
    val centerY = top + height / 2f
    return Rect(centerX - horizontal, centerY - vertical, centerX + horizontal, centerY + vertical)
}

/** Above this collapse fraction the return flight starts from the compact toolbar identity. */
internal const val ProfileCollapsedSwitch = 0.5f

/** How long a push waits for the profile header to place itself before falling back. */
internal const val GeometryWaitMillis = 200L

/** One flying element set: what the overlay draws and where it flies. */
@Immutable
class ChatProfileFlightVisuals(
    /** Renders the avatar exactly as the chat header does, at the given size. */
    val avatar: @Composable (Dp) -> Unit,
    /** The chat header's title and status lines, captured when the flight starts. */
    val chatTitle: String,
    val chatStatus: String,
    /** The layout size of the overlay avatar; the profile avatar size. */
    val avatarSize: Dp
)

/** The live flight. Snapshot state drives the overlay and both headers in the draw phase only. */
@Stable
class ChatProfileFlight(
    val chatId: Long,
    val visuals: ChatProfileFlightVisuals,
    val fingerprint: ChatProfileWindowFingerprint,
    val pushMillis: Int,
    val popMillis: Int,
    initialChatGeometry: ChatProfileElementGeometry,
    initialProfileGeometry: ChatProfileElementGeometry?,
    /** True once the profile identity has scrolled into its compact toolbar form. */
    val collapsed: Boolean
) {
    /** 0 = chat state, 1 = profile state. */
    var progress by mutableStateOf(if (initialProfileGeometry == null) 0f else 1f)
        internal set
    var chatGeometry by mutableStateOf(initialChatGeometry)
        internal set
    var profileGeometry by mutableStateOf(initialProfileGeometry)
        internal set

    /** The profile side was chosen; later reports never move a flight that already has a target. */
    var targetAttached by mutableStateOf(initialProfileGeometry?.usable == true)
        private set

    fun attachProfileTarget(geometry: ChatProfileElementGeometry) {
        if (targetAttached || !geometry.usable) return
        profileGeometry = geometry
        targetAttached = true
    }

    /** Whether the overlay may draw; both sides must be placed. */
    val ready: Boolean get() = chatGeometry.usable && profileGeometry?.usable == true
}

@Immutable
internal data class ChatHeaderReport(
    val chatId: Long,
    val fingerprint: ChatProfileWindowFingerprint,
    val geometry: ChatProfileElementGeometry
)

@Immutable
internal data class ProfileHeaderReport(
    val chatId: Long,
    val fingerprint: ChatProfileWindowFingerprint,
    val geometry: ChatProfileElementGeometry
)

/**
 * Owns the flights and the last known geometry of both headers. One instance per account lives at
 * the app root, so flights survive the route switch (both route contents unmount during the
 * transition) and their jobs outlive any single screen's composition.
 */
@Stable
class ChatProfileTransitionController internal constructor(private val scope: CoroutineScope) {

    internal var chatReport: ChatHeaderReport? = null
    internal var profileReport: ProfileHeaderReport? = null
    internal var profileToolbarReport: ProfileHeaderReport? = null
    /** True while the profile identity is in its compact toolbar form. */
    internal var profileCollapsed: Boolean = false

    var flight: ChatProfileFlight? by mutableStateOf(null)
        internal set

    private var driveJob: Job? = null

    /** The chat-side handle: reports the header geometry and starts pushes. */
    fun chatHeaderSource(chatId: Long, push: ChatProfilePushOrchestration? = null): ChatProfileHeaderSource =
        ChatProfileHeaderSource(this, chatId, push)

    /** The profile-side handle: reports geometry, drives pops and the predictive back gesture. */
    fun profileTarget(chatId: Long, back: ChatProfileBackOrchestration? = null): ChatProfileHeaderTarget =
        ChatProfileHeaderTarget(this, chatId, back)

    internal fun reportChatHeader(chatId: Long, fingerprint: ChatProfileWindowFingerprint, geometry: ChatProfileElementGeometry) {
        val active = flight
        if (active != null && active.chatId != chatId) {
            // The chat switched mid-flight: the flight's landing spot no longer exists.
            abortFlight()
            return
        }
        chatReport = ChatHeaderReport(chatId, fingerprint, geometry)
        if (active != null && active.chatId == chatId && fingerprintMatches(fingerprint, active.fingerprint)) {
            active.chatGeometry = geometry
        }
    }

    internal fun reportProfileHeader(chatId: Long, fingerprint: ChatProfileWindowFingerprint, geometry: ChatProfileElementGeometry) {
        val active = flight
        if (active != null && active.chatId != chatId) {
            abortFlight()
            return
        }
        profileReport = ProfileHeaderReport(chatId, fingerprint, geometry)
        if (active != null) active.attachProfileTarget(geometry)
    }

    internal fun reportProfileToolbar(chatId: Long, fingerprint: ChatProfileWindowFingerprint, geometry: ChatProfileElementGeometry) {
        val active = flight
        if (active != null && active.chatId != chatId) {
            abortFlight()
            return
        }
        profileToolbarReport = ProfileHeaderReport(chatId, fingerprint, geometry)
    }

    internal fun setProfileCollapsed(chatId: Long, collapsed: Boolean) {
        if (flight?.chatId == chatId) return
        profileCollapsed = collapsed
    }

    /** Chat-header visibility of the real elements; read in the draw phase. */
    internal fun chatVisibility(chatId: Long): Float {
        val active = flight ?: return 1f
        return if (active.chatId == chatId) chatSideVisibility(active.progress) else 1f
    }

    /** Profile-header visibility of the real elements; read in the draw phase. */
    internal fun profileVisibility(chatId: Long): Float {
        val active = flight ?: return 1f
        return if (active.chatId == chatId) profileSideVisibility(active.progress) else 1f
    }

    /**
     * Starts (or reverses into) a push. Returns true when a flight runs; navigation happens in
     * either case, and a false return means the caller got the standard transition as fallback.
     */
    internal fun beginPush(
        chatId: Long,
        visuals: ChatProfileFlightVisuals,
        current: ChatProfileWindowFingerprint,
        push: MotionSpec,
        pop: MotionSpec
    ): Boolean {
        if (push.isInstant) return false
        val report = chatReport ?: return false
        if (report.chatId != chatId || !fingerprintMatches(report.fingerprint, current) || !report.geometry.usable) return false
        val active = flight
        if (active != null && active.chatId == chatId) {
            // Interrupt: fly back toward the profile from wherever the flight is now.
            if (!active.ready) {
                abortFlight()
                return false
            }
            drive(active, target = 1f, millis = active.pushMillis)
            return true
        }
        abortFlight()
        val newFlight = ChatProfileFlight(
            chatId = chatId,
            visuals = visuals,
            fingerprint = current,
            pushMillis = push.durationMillis,
            popMillis = pop.durationMillis,
            initialChatGeometry = report.geometry,
            // The profile content composes only after navigation; its geometry arrives by report.
            initialProfileGeometry = null,
            collapsed = false
        )
        flight = newFlight
        driveJob?.cancel()
        driveJob = scope.launch {
            val placed = withTimeoutOrNull(GeometryWaitMillis) {
                snapshotFlow { newFlight.profileGeometry?.avatar != null }.first { it }
            }
            if (placed == null) {
                // Geometry never arrived: fall back to the standard transition underneath.
                if (flight === newFlight) flight = null
                return@launch
            }
            animateTo(newFlight, target = 1f, millis = newFlight.pushMillis)
            if (flight === newFlight) flight = null
        }
        return true
    }

    /**
     * Starts (or reverses into) a pop. Returns true when a flight runs; navigation happens in
     * either case.
     */
    internal fun beginPop(
        chatId: Long,
        visuals: ChatProfileFlightVisuals,
        current: ChatProfileWindowFingerprint,
        pop: MotionSpec
    ): Boolean {
        if (pop.isInstant) return false
        val active = flight
        if (active != null && active.chatId == chatId) {
            if (!active.ready) {
                // A push interrupted before the profile side was even placed: nothing to fly to.
                abortFlight()
                return false
            }
            drive(active, target = 0f, millis = active.popMillis)
            return true
        }
        val chat = chatReport ?: return false
        if (chat.chatId != chatId || !fingerprintMatches(chat.fingerprint, current) || !chat.geometry.usable) return false
        val profile = popSourceGeometry(chatId, current) ?: return false
        abortFlight()
        val newFlight = ChatProfileFlight(
            chatId = chatId,
            visuals = visuals,
            fingerprint = current,
            pushMillis = pop.durationMillis,
            popMillis = pop.durationMillis,
            initialChatGeometry = chat.geometry,
            initialProfileGeometry = profile.geometry,
            collapsed = profileCollapsed
        )
        flight = newFlight
        drive(newFlight, target = 0f, millis = newFlight.popMillis)
        return true
    }

    /** The visible profile identity: the compact toolbar once collapsed, else the big header. */
    private fun popSourceGeometry(chatId: Long, current: ChatProfileWindowFingerprint): ProfileHeaderReport? {
        val collapsed = profileCollapsed
        val candidates = if (collapsed) listOfNotNull(profileToolbarReport, profileReport) else listOfNotNull(profileReport)
        return candidates.firstOrNull {
            it.chatId == chatId && fingerprintMatches(it.fingerprint, current) && it.geometry.usable
        }
    }

    /** Engages the flight for a predictive back gesture at the current profile state. */
    internal fun beginGesturePop(
        chatId: Long,
        visuals: ChatProfileFlightVisuals,
        current: ChatProfileWindowFingerprint,
        pop: MotionSpec
    ): Boolean {
        val active = flight
        if (active != null && active.chatId == chatId) {
            if (!active.ready) {
                abortFlight()
                return false
            }
            // The gesture owns the progress now; stop any running drive animation.
            driveJob?.cancel()
            driveJob = null
            return true
        }
        // Reuse the pop path to build the flight, then stop its drive: the gesture writes progress.
        if (!beginPop(chatId, visuals, current, pop)) return false
        driveJob?.cancel()
        driveJob = null
        return true
    }

    /** Gesture progress 0..1 drives the same flight, from the profile state toward the chat. */
    internal fun updateGesturePop(progress: Float) {
        val active = flight ?: return
        active.progress = (1f - progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    /** The gesture was cancelled: fly back to the profile state without navigating. */
    internal fun cancelGesturePop() {
        val active = flight ?: return
        drive(active, target = 1f, millis = active.popMillis)
    }

    /** The gesture committed: navigate now and finish the flight over the incoming chat. */
    internal fun commitGesturePop(navigate: () -> Unit) {
        val active = flight
        navigate()
        if (active == null) return
        drive(active, target = 0f, millis = active.popMillis)
    }

    private fun drive(active: ChatProfileFlight, target: Float, millis: Int) {
        driveJob?.cancel()
        driveJob = scope.launch {
            animateTo(active, target, millis)
            if (flight === active) flight = null
        }
    }

    private suspend fun animateTo(active: ChatProfileFlight, target: Float, millis: Int) {
        val distance = abs(target - active.progress)
        if (distance < 0.001f) {
            active.progress = target
            return
        }
        animate(
            initialValue = active.progress,
            targetValue = target,
            animationSpec = tween((millis * distance).roundToInt().coerceAtLeast(1), easing = LinearEasing)
        ) { value, _ -> active.progress = value }
    }

    private fun abortFlight() {
        driveJob?.cancel()
        driveJob = null
        flight = null
    }

    internal fun dispose() {
        abortFlight()
    }
}

/**
 * Everything the chat side needs to start a push, provided by the navigation host so the handles
 * stay fresh across recompositions. A null [visuals] means the identity must not fly (a forum
 * topic view, for example) and the caller keeps the standard transition.
 */
@Immutable
class ChatProfilePushOrchestration(
    val visuals: () -> ChatProfileFlightVisuals?,
    val fingerprint: () -> ChatProfileWindowFingerprint,
    val pushSpec: () -> MotionSpec,
    val popSpec: () -> MotionSpec,
    val navigate: () -> Unit
)

/**
 * Everything the profile side needs to run a pop or a predictive back gesture. A null [visuals]
 * falls back to the standard transition.
 */
@Immutable
class ChatProfileBackOrchestration(
    val visuals: () -> ChatProfileFlightVisuals?,
    val fingerprint: () -> ChatProfileWindowFingerprint,
    val popSpec: () -> MotionSpec,
    val navigate: () -> Unit
)

/** The chat-header side of the transition; one instance per composed chat. */
@Stable
class ChatProfileHeaderSource internal constructor(
    private val controller: ChatProfileTransitionController,
    internal val chatId: Long,
    private val push: ChatProfilePushOrchestration? = null
) {
    private var avatarBounds: Rect? = null
    private var titleBounds: Rect? = null
    private var statusBounds: Rect? = null

    /** Real header visibility; call inside a graphicsLayer block so it stays in the draw phase. */
    fun headerVisibility(): Float = controller.chatVisibility(chatId)

    /** True while a flight of this chat runs: the hidden elements must not accept taps. */
    fun flightActive(): Boolean = controller.flight?.chatId == chatId

    /**
     * Opens the profile: starts (or reverses into) the flight when the geometry allows it and
     * navigates in either case. Navigation is never delayed by the flight.
     */
    fun open() {
        val orchestration = push ?: return
        val visuals = orchestration.visuals()
        if (visuals == null) {
            orchestration.navigate()
            return
        }
        controller.beginPush(
            chatId = chatId,
            visuals = visuals,
            current = orchestration.fingerprint(),
            push = orchestration.pushSpec(),
            pop = orchestration.popSpec()
        )
        orchestration.navigate()
    }

    /** Reports the avatar bounds; each report publishes the full merged element set. */
    fun reportAvatar(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        avatarBounds = bounds
        publish(fingerprint)
    }

    fun reportTitle(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        titleBounds = bounds
        publish(fingerprint)
    }

    fun reportStatus(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        statusBounds = bounds
        publish(fingerprint)
    }

    private fun publish(fingerprint: ChatProfileWindowFingerprint) {
        controller.reportChatHeader(chatId, fingerprint, ChatProfileElementGeometry(avatarBounds, titleBounds, statusBounds))
    }
}

/** The profile-header side of the transition; one instance per composed profile. */
@Stable
class ChatProfileHeaderTarget internal constructor(
    private val controller: ChatProfileTransitionController,
    internal val chatId: Long,
    private val back: ChatProfileBackOrchestration? = null
) {
    private var avatarBounds: Rect? = null
    private var titleBounds: Rect? = null
    private var statusBounds: Rect? = null
    private var toolbarAvatarBounds: Rect? = null
    private var toolbarTitleBounds: Rect? = null

    /** Real header visibility; call inside a graphicsLayer block so it stays in the draw phase. */
    fun headerVisibility(): Float = controller.profileVisibility(chatId)

    /** True while a flight of this chat runs: the hidden elements must not accept taps. */
    fun flightActive(): Boolean = controller.flight?.chatId == chatId

    /** Compact toolbar identity visibility: hidden exactly while the flight carries it. */
    fun toolbarVisibility(): Float = controller.profileVisibility(chatId)

    fun reportAvatar(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        avatarBounds = bounds
        controller.reportProfileHeader(chatId, fingerprint, ChatProfileElementGeometry(avatarBounds, titleBounds, statusBounds))
    }

    fun reportTitle(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        titleBounds = bounds
        controller.reportProfileHeader(chatId, fingerprint, ChatProfileElementGeometry(avatarBounds, titleBounds, statusBounds))
    }

    fun reportStatus(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        statusBounds = bounds
        controller.reportProfileHeader(chatId, fingerprint, ChatProfileElementGeometry(avatarBounds, titleBounds, statusBounds))
    }

    fun reportToolbarAvatar(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        toolbarAvatarBounds = bounds
        controller.reportProfileToolbar(chatId, fingerprint, ChatProfileElementGeometry(toolbarAvatarBounds, toolbarTitleBounds))
    }

    fun reportToolbarTitle(bounds: Rect?, fingerprint: ChatProfileWindowFingerprint) {
        toolbarTitleBounds = bounds
        controller.reportProfileToolbar(chatId, fingerprint, ChatProfileElementGeometry(toolbarAvatarBounds, toolbarTitleBounds))
    }

    /** The compact toolbar is the flight source once the identity has collapsed into it. */
    fun reportCollapsed(collapsed: Boolean) = controller.setProfileCollapsed(chatId, collapsed)

    private fun pop(visuals: ChatProfileFlightVisuals?, orchestration: ChatProfileBackOrchestration): Boolean {
        if (visuals == null) return false
        return controller.beginPop(
            chatId = chatId,
            visuals = visuals,
            current = orchestration.fingerprint(),
            pop = orchestration.popSpec()
        )
    }

    /** Back button tap: fly back when possible, else navigate through the standard transition. */
    fun back() {
        val orchestration = back ?: return
        pop(orchestration.visuals(), orchestration)
        // Navigation happens in either case; the flight only adds the spatial motion above it.
        orchestration.navigate()
    }

    /** Engages the flight for a gesture; false means the caller must fall back to plain back. */
    fun beginBackGesture(): Boolean {
        val orchestration = back ?: return false
        val visuals = orchestration.visuals() ?: return false
        return controller.beginGesturePop(
            chatId = chatId,
            visuals = visuals,
            current = orchestration.fingerprint(),
            pop = orchestration.popSpec()
        )
    }

    /** Gesture progress 0..1; mirrors the flight from the profile state toward the chat. */
    fun updateBackGesture(progress: Float) = controller.updateGesturePop(progress)

    /** The gesture was cancelled: return to the profile without navigating. */
    fun cancelBackGesture() = controller.cancelGesturePop()

    /** The gesture committed: navigate and finish the flight over the incoming chat. */
    fun commitBackGesture() {
        val orchestration = back ?: return
        controller.commitGesturePop(orchestration.navigate)
    }
}

/** Remembers one controller per account; flights never outlive their account or the composition. */
@Composable
fun rememberChatProfileTransitionController(accountId: String): ChatProfileTransitionController {
    val scope = rememberCoroutineScope()
    val controller = remember(accountId) { ChatProfileTransitionController(scope) }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    return controller
}

/**
 * The flying layer above both route contents. Reads every animated value in the draw phase, so a
 * flight frame redraws three small elements and never recomposes a screen. Semantics are cleared:
 * the overlay is pure visuals and never a second focus target.
 */
@Composable
fun ChatProfileTransitionOverlay(controller: ChatProfileTransitionController) {
    val flight = controller.flight ?: return
    val palette = beyGramPalette()
    // Window coordinates of the overlay root; reported element bounds are translated by it.
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
            .clearAndSetSemantics { }
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val chatGeometry = flight.chatGeometry
        val profileGeometry = flight.profileGeometry
        val chatAvatar = chatGeometry.avatar
        val profileAvatar = profileGeometry?.avatar
        if (chatAvatar != null && profileAvatar != null) {
            val avatarLayoutPx = with(density) { flight.visuals.avatarSize.toPx() }
            Box(
                Modifier
                    .graphicsLayer {
                        val eased = easedWindowProgress(flight.progress, 0f, ChatProfileFlightWindows.AvatarEnd)
                        val current = lerpRect(chatAvatar, profileAvatar, eased)
                        translationX = current.left - overlayOrigin.x
                        translationY = current.top - overlayOrigin.y
                        scaleX = current.width / avatarLayoutPx
                        scaleY = current.height / avatarLayoutPx
                        alpha = overlayVisibility(flight.progress)
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .size(flight.visuals.avatarSize)
            ) {
                flight.visuals.avatar(flight.visuals.avatarSize)
            }
        }
        val chatTitle = chatGeometry.title
        val profileTitle = profileGeometry?.title
        if (chatTitle != null && flight.visuals.chatTitle.isNotBlank()) {
            Text(
                flight.visuals.chatTitle,
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer {
                        val eased = easedWindowProgress(flight.progress, ChatProfileFlightWindows.TextStart, ChatProfileFlightWindows.TextEnd)
                        val current = if (profileTitle != null) lerpRect(chatTitle, profileTitle, eased) else chatTitle
                        translationX = current.left - overlayOrigin.x
                        translationY = current.top - overlayOrigin.y
                        val scale = if (profileTitle != null && chatTitle.width > 0.5f) {
                            1f + (current.width / chatTitle.width - 1f) * eased
                        } else 1f
                        scaleX = scale
                        scaleY = scale
                        alpha = overlayVisibility(flight.progress)
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            )
        }
        val chatStatus = chatGeometry.status
        if (chatStatus != null && flight.visuals.chatStatus.isNotBlank()) {
            Text(
                flight.visuals.chatStatus,
                color = palette.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .graphicsLayer {
                        val eased = easedWindowProgress(flight.progress, ChatProfileFlightWindows.TextStart, ChatProfileFlightWindows.TextEnd)
                        val target = profileGeometry?.status
                        val current = if (target != null) lerpRect(chatStatus, target, eased) else chatStatus
                        translationX = current.left - overlayOrigin.x
                        translationY = current.top - overlayOrigin.y
                        val scale = if (target != null && chatStatus.height > 0.5f) {
                            1f + (current.height / chatStatus.height - 1f) * eased
                        } else 1f
                        scaleX = scale
                        scaleY = scale
                        alpha = overlayVisibility(flight.progress)
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            )
        }
    }
}
