package com.beykus.beygram.ui.screens.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.StealthScheduleUnavailableException
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.text.userMessage
import com.beykus.beygram.ui.components.beyGramPalette

/** A send waits for the user's confirmation while hidden presence warns about sending and this chat session has not confirmed one. */
internal fun stealthSendNeedsConfirmation(warns: Boolean, confirmed: Boolean): Boolean = warns && !confirmed

/**
 * Asks before the first message sent in a chat while online status is hidden and warnings are on: Telegram can show the
 * account online for a short while after a send. Remembered per chat screen, so the question comes back when the chat opens again.
 */
internal class StealthSendGuard(private val conversation: ConversationRepository) {
    private var confirmed by mutableStateOf(false)
    private var pending by mutableStateOf<(() -> Unit)?>(null)
    private var pendingCancel: () -> Unit = {}

    /** Runs [action] at once, or after the user confirms the question. [onCancel] undoes what the caller staged for it. */
    fun run(onCancel: () -> Unit = {}, action: () -> Unit) {
        // A send that stealth mode schedules on the server is not published now, so there is nothing to warn about yet.
        // Should scheduling fail, StealthScheduleFallback asks before anything goes out at once.
        if (!conversation.stealthSchedulesSends() && stealthSendNeedsConfirmation(conversation.stealthWarnsBeforeSending(), confirmed)) {
            pending = action
            pendingCancel = onCancel
        } else {
            action()
        }
    }

    private fun cancel() {
        pending = null
        pendingCancel().also { pendingCancel = {} }
    }

    @Composable
    fun Dialog() {
        val action = pending ?: return
        val palette = beyGramPalette()
        AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text(stringResource(R.string.stealth_send_warning_title)) },
            text = { Text(stringResource(R.string.stealth_send_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    pendingCancel = {}
                    confirmed = true
                    action()
                }) { Text(stringResource(R.string.stealth_send_warning_send), color = palette.accent) }
            },
            dismissButton = { TextButton(onClick = ::cancel) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
internal fun rememberStealthSendGuard(chatId: Long, conversation: ConversationRepository): StealthSendGuard =
    remember(chatId, conversation) { StealthSendGuard(conversation) }

/** True when stealth mode will turn a send with [options] into a server-scheduled message. */
internal fun stealthWillSchedule(conversation: ConversationRepository, options: MessageSendOptions): Boolean =
    options.scheduledAtSeconds == null && !options.sendNowConsent && conversation.stealthSchedulesSends()

/** Tells the user that stealth mode handed the message to Telegram's scheduled queue instead of sending it now. */
internal fun showStealthScheduledNotice(context: Context) {
    Toast.makeText(context, context.getString(R.string.stealth_schedule_done), Toast.LENGTH_SHORT).show()
}

/**
 * Stealth mode could not schedule a send on the server (#541). Nothing was sent; the user may agree to an ordinary send,
 * which Telegram can show as activity. There is never a silent fallback: [sendNow] runs only from the dialog's button.
 */
internal class StealthScheduleFallback {
    private var pending by mutableStateOf<Pair<StealthScheduleUnavailableException, () -> Unit>?>(null)

    /** Asks the user when [error] is a refused stealth schedule and returns true; any other error is left to the caller. */
    fun offer(error: Throwable?, sendNow: () -> Unit): Boolean {
        if (error !is StealthScheduleUnavailableException) return false
        pending = error to sendNow
        return true
    }

    @Composable
    fun Dialog() {
        val (error, sendNow) = pending ?: return
        val palette = beyGramPalette()
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.stealth_schedule_fallback_title)) },
            text = {
                Text(error.userMessage(context, R.string.stealth_schedule_failed) + "\n\n" + stringResource(R.string.stealth_schedule_fallback_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    sendNow()
                }) { Text(stringResource(R.string.stealth_schedule_fallback_send_now), color = palette.accent) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
