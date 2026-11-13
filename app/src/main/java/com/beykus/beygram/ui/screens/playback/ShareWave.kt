package com.beykus.beygram.ui.screens.playback

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.beykus.beygram.ui.screens.chat.ForwardChatDialog
import com.beykus.beygram.ui.screens.chat.ForwardTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Only the user's own music may be relayed: a source track (such as a streaming service) is never offered, nor is a
 * track from a secret, view-once or content-protected origin. [originUnprotected] is true only when the track's chat is
 * known not to protect its content.
 */
internal fun canShareWave(item: PlaybackItem, originUnprotected: Boolean, isProviderTrack: Boolean): Boolean =
    !isProviderTrack && item.isMusic && !item.secret && !item.isSecretChat && !item.viewOnce &&
        !item.isRestrictedCatalog && !item.systemPrivate && originUnprotected

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
        chats = waveTargetChats(chats + archivedChats),
        busy = sending,
        error = failure,
        observeFile = repository::observeFile,
        downloadFile = repository::downloadFile,
        asCopy = true,
        allowComment = false,
        onSend = { targets, _ ->
            failure = null
            val target = targets.singleOrNull()
            if (target == null) {
                failure = onePersonText
                return@ForwardChatDialog targets
            }
            val answer = CompletableDeferred<Boolean>()
            confirm = target to answer
            val accepted = answer.await()
            confirm = null
            if (!accepted) return@ForwardChatDialog targets
            sending = true
            BgWaveHub.share(accountId, target.chatId, target.chatId)
            sending = false
            val state = BgWaveHub.controller.state.value
            if (state?.phase == BgWavePhase.Failed) {
                failure = BgWaveErrorTexts.of(context, state.errorKey) ?: failedText
                BgWaveHub.controller.dismiss()
                targets
            } else {
                Toast.makeText(context, R.string.bgwave_share_sent, Toast.LENGTH_SHORT).show()
                emptyList()
            }
        },
        onClose = onClose,
    )
    confirm?.let { (target, answer) ->
        AlertDialog(
            onDismissRequest = { answer.complete(false) },
            title = { Text(stringResource(R.string.bgwave_share_confirm_title, target.title)) },
            text = { Text(stringResource(R.string.bgwave_share_confirm_body)) },
            confirmButton = { TextButton(onClick = { answer.complete(true) }) { Text(stringResource(R.string.bgwave_share_confirm_send)) } },
            dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text(stringResource(R.string.bgwave_share_confirm_cancel)) } },
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
