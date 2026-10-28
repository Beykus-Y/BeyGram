package com.beykus.beygram.ui.screens.chats

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.components.beyGramPalette

/** A read mark that stealth mode refused. It waits for the user's one-off answer before anything is sent. */
internal sealed interface MarkReadRequest {
    /** One chat, from its read action. */
    data class Chat(val chat: ChatPreview) : MarkReadRequest

    /** "Mark all": every chat in [chats] is read if the user agrees; [refused] of them are ones stealth mode would refuse. */
    data class All(val chats: List<ChatPreview>, val refused: Int) : MarkReadRequest
}

/** The one open question. It is shown above the whole app, so every screen that marks a read shares it. */
internal class MarkReadConfirmationState {
    var request by mutableStateOf<MarkReadRequest?>(null)
        private set

    fun ask(next: MarkReadRequest) {
        request = next
    }

    fun close() {
        request = null
    }
}

/** Asks before a read that stealth mode would refuse. [onConfirm] sends the read with this one-off consent. */
@Composable
internal fun MarkReadConfirmationDialog(state: MarkReadConfirmationState, onConfirm: (MarkReadRequest) -> Unit) {
    val request = state.request ?: return
    val palette = beyGramPalette()
    val all = request as? MarkReadRequest.All
    AlertDialog(
        onDismissRequest = state::close,
        title = { Text(stringResource(if (all != null) R.string.chats_read_all_title else R.string.stealth_mark_read_title)) },
        text = {
            Text(
                if (all != null) pluralStringResource(R.plurals.stealth_mark_all_read_text, all.refused, all.refused)
                else stringResource(R.string.stealth_mark_read_text)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                state.close()
                onConfirm(request)
            }) { Text(stringResource(R.string.stealth_mark_read_confirm), color = palette.accent) }
        },
        dismissButton = { TextButton(onClick = state::close) { Text(stringResource(R.string.common_cancel)) } }
    )
}
