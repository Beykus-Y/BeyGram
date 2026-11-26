package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.TelegramBoundedText

private const val MAX_ACTIVE_COMPOSER_LAUNCHES = 4

/** Owns the visual transfer from the draft to a measured timeline row. The send queue owns delivery. */
internal class ComposerLaunchState {
    private val composerLaunches = mutableStateListOf<ComposerLaunch>()
    val launches: List<ComposerLaunch> get() = composerLaunches

    private var counter = 0L
    private var draftLaunchKey by mutableStateOf<Long?>(null)
    private val completedLocalLaunches = mutableListOf<Triple<String, Long?, Long>>()
    var fieldBounds by mutableStateOf<Rect?>(null)
    var overlayBounds by mutableStateOf<Rect?>(null)

    fun start(outgoing: TelegramBoundedText, replyId: Long?, baselineSequence: Long, speed: Float): ComposerLaunch? {
        val source = fieldBounds?.takeUnless { it.isEmpty } ?: return null
        val launch = ComposerLaunch(
            key = ++counter,
            text = outgoing.text,
            entities = outgoing.entities,
            replyToMessageId = replyId,
            sourceBounds = source,
            baselineSequence = baselineSequence,
            speed = speed,
            isTargetVisible = ::isRowVisible
        )
        composerLaunches += launch
        draftLaunchKey = launch.key
        if (composerLaunches.size > MAX_ACTIVE_COMPOSER_LAUNCHES) cancel(composerLaunches.first())
        return launch
    }

    fun hidesDraft(editing: Boolean, draft: String): Boolean {
        val key = draftLaunchKey
        return shouldHideComposerDraft(editing, draft, key, composerLaunches.firstOrNull { it.key == key })
    }

    fun releaseDraft(launch: ComposerLaunch) {
        if (draftLaunchKey == launch.key) draftLaunchKey = null
    }

    fun cancel(launch: ComposerLaunch) {
        composerLaunches.remove(launch)
        releaseDraft(launch)
    }

    fun finish(launch: ComposerLaunch) {
        if (launch.messageId == null && launch.localAttemptId != null) {
            completedLocalLaunches += Triple(launch.text, launch.replyToMessageId, launch.baselineSequence)
            if (completedLocalLaunches.size > MAX_ACTIVE_COMPOSER_LAUNCHES) completedLocalLaunches.removeAt(0)
        }
        cancel(launch)
    }

    private fun isRowVisible(bounds: Rect): Boolean {
        val viewport = overlayBounds ?: return false
        val bottom = minOf(viewport.bottom, fieldBounds?.top ?: viewport.bottom)
        return !bounds.isEmpty && bounds.top >= viewport.top && bounds.bottom <= bottom
    }

    fun targets(history: ChatHistory, localIds: Set<Long>): Map<Long, ComposerLaunch> =
        resolveComposerLaunchTargets(launches, history, localIds)

    fun claimCompletedArrivals(history: ChatHistory, animationTracker: MessageArrivalTracker) {
        if (completedLocalLaunches.isNotEmpty()) {
            val claimed = HashSet<Long>()
            val iterator = completedLocalLaunches.iterator()
            while (iterator.hasNext()) {
                val (launchText, launchReply, launchBaseline) = iterator.next()
                val match = findComposerLaunchMessage(
                    history = history,
                    text = launchText,
                    replyToMessageId = launchReply,
                    baselineSequence = launchBaseline,
                    assignedIds = claimed
                )
                if (match != null) {
                    claimed += resolveMessageIdThroughReplacements(match.id, history.recentReplacements)
                    animationTracker.claim(listOf(match.id))
                    iterator.remove()
                }
            }
        }
    }

    fun updateHistory(history: ChatHistory, queuedTextAttempts: List<TextSendAttempt>) {
        if (composerLaunches.isEmpty()) return
        val assigned = composerLaunches.mapNotNull { launch ->
            launch.messageId?.let { resolveMessageIdThroughReplacements(it, history.recentReplacements) }
        }.toMutableSet()
        composerLaunches.sortedBy { it.key }.toList().forEach { launch ->
            val localId = launch.localAttemptId
            if (localId != null && queuedTextAttempts.any { it.attemptId == localId && it.phase == TextSendAttemptPhase.Failed }) {
                cancel(launch)
                return@forEach
            }
            val existing = launch.messageId?.let { resolveMessageIdThroughReplacements(it, history.recentReplacements) }
            if (existing != null) {
                val current = history.messages.firstOrNull { it.id == existing }
                when {
                    current == null -> Unit
                    current.sendState == MessageSendState.Failed -> cancel(launch)
                    else -> launch.message = current
                }
            } else {
                val candidate = findComposerLaunchMessage(
                    history, launch.text, launch.replyToMessageId, launch.baselineSequence, assigned
                )
                if (candidate != null) {
                    if (candidate.sendState == MessageSendState.Failed) {
                        cancel(launch)
                    } else {
                        launch.messageId = candidate.id
                        launch.message = candidate
                        assigned += resolveMessageIdThroughReplacements(candidate.id, history.recentReplacements)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposerLaunchHistoryEffect(
    state: ComposerLaunchState,
    history: ChatHistory,
    queuedTextAttempts: List<TextSendAttempt>
) {
    LaunchedEffect(state, state.launches.toList(), history.messages, history.liveArrivals, history.recentReplacements, queuedTextAttempts) {
        state.updateHistory(history, queuedTextAttempts)
    }
}

@Composable
internal fun ChatComposerLaunchOverlay(state: ComposerLaunchState, chatId: Long) {
    ComposerLaunchOverlay(state.launches, chatId, state.overlayBounds, state::finish, state::cancel)
}
