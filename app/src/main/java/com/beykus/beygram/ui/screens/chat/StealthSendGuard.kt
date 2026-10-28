package com.beykus.beygram.ui.screens.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.repository.ConversationRepository
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

    /** Runs [action] at once, or after the user confirms the question. */
    fun run(action: () -> Unit) {
        if (stealthSendNeedsConfirmation(conversation.stealthWarnsBeforeSending(), confirmed)) {
            pending = action
        } else {
            action()
        }
    }

    @Composable
    fun Dialog() {
        val action = pending ?: return
        val palette = beyGramPalette()
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.stealth_send_warning_title)) },
            text = { Text(stringResource(R.string.stealth_send_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    confirmed = true
                    action()
                }) { Text(stringResource(R.string.stealth_send_warning_send), color = palette.accent) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
internal fun rememberStealthSendGuard(chatId: Long, conversation: ConversationRepository): StealthSendGuard =
    remember(chatId, conversation) { StealthSendGuard(conversation) }
