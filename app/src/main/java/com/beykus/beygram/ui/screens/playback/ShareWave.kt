package com.beykus.beygram.ui.screens.playback

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.media.bgwave.BgWaveErrors
import com.beykus.beygram.media.bgwave.BgWaveHub
import com.beykus.beygram.media.bgwave.BgWavePhase
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.screens.chat.ForwardChatDialog
import com.beykus.beygram.ui.screens.chat.ForwardTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Music that may be offered as a wave. A provider track (Yandex) is always allowed, since it is relayed as live sound
 * only. Any other track must be the user's own and not restricted, and its origin must be [originUnprotected], which is
 * true only when the track's chat is known not to protect its content. Secret, view-once and system-private items are
 * never offered.
 */
internal fun canShareWave(item: PlaybackItem, originUnprotected: Boolean, isProviderTrack: Boolean): Boolean =
    item.isMusic && !item.secret && !item.isSecretChat && !item.viewOnce && !item.systemPrivate &&
        (isProviderTrack || (!item.isRestrictedCatalog && originUnprotected))

/** A wave goes to one person: a private chat that is not Saved Messages, a secret chat or a group, and where the user may write. */
internal fun waveTargetChats(chats: List<ChatPreview>): List<ChatPreview> =
    chats.filter { it.canPost && !it.isGroup && !it.isChannel && !it.isSecret && !it.isSavedMessages }.distinctBy { it.id }

/** True while a wave is starting or running, so the menu offers to stop it instead of starting another. */
@Composable
internal fun rememberWaveActive(): Boolean {
    val state by BgWaveHub.controller.state.collectAsStateWithLifecycle()
    val phase = state?.phase
    return phase != null && phase != BgWavePhase.Ended && phase != BgWavePhase.Failed
}

/**
 * Picks one personal chat, shows what will be sent and what the peer will see, and sends the invitation. Nothing leaves
 * the device until the confirmation is accepted; declining it keeps the chat selected, closing the picker cancels.
 */
@Composable
internal fun ShareWaveDialog(repository: TelegramRepository, accountId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val chats by repository.chats.collectAsStateWithLifecycle()
    val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
    val onePersonText = stringResource(R.string.bgwave_share_one_person)
    val failedText = stringResource(R.string.bgwave_error_send_failed)
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<ForwardTarget, CompletableDeferred<Boolean>>?>(null) }
    ForwardChatDialog(
        chats = chats + archivedChats,
        recipientRepository = repository,
        candidatePolicy = ::waveTargetChats,
        busy = sending,
        error = failure,
        observeFile = repository::observeFile,
        downloadFile = repository::downloadFile,
        titleOverride = stringResource(R.string.player_share),
        sendLabel = stringResource(R.string.share_send),
        allowComment = false,
        singleRecipient = true,
        freezeSelectionAfterAttempt = false,
        onSend = { targets, _ ->
            failure = null
            val target = targets.singleOrNull()
            if (target == null || waveTargetChats((repository.chats.value + repository.archivedChats.value + repository.folderChats.value.values.flatten()).distinctBy { it.id }).none { it.id == target.chatId }) {
                failure = onePersonText
                return@ForwardChatDialog targets
            }
            val answer = CompletableDeferred<Boolean>()
            confirm = target to answer
            val accepted = answer.await()
            confirm = null
            if (!accepted) return@ForwardChatDialog targets
            if (waveTargetChats((repository.chats.value + repository.archivedChats.value + repository.folderChats.value.values.flatten()).distinctBy { it.id }).none { it.id == target.chatId }) {
                failure = onePersonText
                return@ForwardChatDialog targets
            }
            sending = true
            val sent = try {
                BgWaveHub.share(accountId, target.chatId, target.chatId)
            } finally {
                sending = false
            }
            val state = BgWaveHub.controller.state.value
            if (!sent) {
                val ownFailure = state?.takeIf {
                    it.accountId == accountId && it.chatId == target.chatId && it.phase == BgWavePhase.Failed
                }
                failure = if (ownFailure != null) BgWaveErrorTexts.of(context, ownFailure.errorKey) ?: failedText
                    else if (state != null && state.phase != BgWavePhase.Ended && state.phase != BgWavePhase.Failed)
                        BgWaveErrorTexts.of(context, BgWaveErrors.BUSY) ?: failedText
                    else failedText
                targets
            } else {
                Toast.makeText(context, R.string.bgwave_share_sent, Toast.LENGTH_SHORT).show()
                emptyList()
            }
        },
        onClose = onClose,
    )
    confirm?.let { (target, answer) ->
        BeyGramDialog(
            onDismissRequest = { answer.complete(false) },
            title = stringResource(R.string.bgwave_share_confirm_title, target.title),
            message = stringResource(R.string.bgwave_share_confirm_body),
            confirmText = stringResource(R.string.bgwave_share_confirm_send),
            onConfirm = { answer.complete(true) },
            dismissText = stringResource(R.string.bgwave_share_confirm_cancel),
            onDismiss = { answer.complete(false) },
        )
    }
}

/** Texts of failure keys for places that have no composition, such as a toast. */
internal object BgWaveErrorTexts {
    fun of(context: android.content.Context, key: String?): String? = when (key) {
        BgWaveErrors.UNAVAILABLE -> context.getString(R.string.bgwave_error_unavailable)
        BgWaveErrors.SEND_FAILED -> context.getString(R.string.bgwave_error_send_failed)
        BgWaveErrors.NO_CONNECTION -> context.getString(R.string.bgwave_error_no_connection)
        BgWaveErrors.BUSY -> context.getString(R.string.bgwave_error_busy)
        BgWaveErrors.EXPIRED -> context.getString(R.string.bgwave_error_expired)
        BgWaveErrors.REJECTED -> context.getString(R.string.bgwave_error_rejected)
        else -> null
    }
}
