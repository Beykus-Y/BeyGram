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

/**
 * Whether a view-once message must wait for the user's consent before its viewer shows it. Stealth mode refuses to report
 * the opening, so without the question the content would open while the sender is never told and the message stays.
 */
internal fun viewOnceNeedsConsent(isViewOnce: Boolean, allowed: Boolean, consented: Boolean): Boolean =
    isViewOnce && !allowed && !consented

/**
 * The view-once messages the user agreed to open on one chat (or thread) screen. Consent is kept per message, and the key
 * holds the chat too, because message ids are only unique inside one chat. The state lives as long as the screen does, so
 * the question comes back when the chat opens again.
 */
internal class ViewOnceStealthGate {
    private var consentedKeys by mutableStateOf(emptySet<Pair<Long, Long>>())

    fun needsConsent(chatId: Long, messageId: Long, isViewOnce: Boolean, allowed: Boolean): Boolean =
        viewOnceNeedsConsent(isViewOnce, allowed, consented(chatId, messageId))

    fun consented(chatId: Long, messageId: Long): Boolean = (chatId to messageId) in consentedKeys

    /** With the user's consent the opening skips the stealth refusal; otherwise it is the normal request, which the switch decides. */
    fun openContent(conversation: ConversationRepository, chatId: Long, messageId: Long) {
        if (consented(chatId, messageId)) conversation.openMessageContentWithConsent(chatId, messageId)
        else conversation.openMessageContent(chatId, messageId)
    }

    /** Shown in place of the viewer. "Open" remembers the consent; "Cancel" or dismissing sends nothing. */
    @Composable
    fun ConsentDialog(chatId: Long, messageId: Long, onCancel: () -> Unit) {
        val palette = beyGramPalette()
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.stealth_view_once_title)) },
            text = { Text(stringResource(R.string.stealth_view_once_text)) },
            confirmButton = {
                TextButton(onClick = { consentedKeys = consentedKeys + (chatId to messageId) }) {
                    Text(stringResource(R.string.stealth_view_once_open), color = palette.accent)
                }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
internal fun rememberViewOnceStealthGate(chatKey: Any): ViewOnceStealthGate = remember(chatKey) { ViewOnceStealthGate() }
