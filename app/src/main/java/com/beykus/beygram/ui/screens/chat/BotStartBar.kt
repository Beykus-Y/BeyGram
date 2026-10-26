package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.launch

/** Telegram bots use the same command as every other client; the button does not depend on inline or web-app support. */
private const val BOT_START_COMMAND = "/start"

/**
 * The ordinary bot start path. It sends a text command, so it works even when the bot has no inline mode,
 * no commands menu and no Mini App.
 */
@Composable
internal fun BotStartBar(
    conversation: ConversationRepository,
    chatId: Long,
    forumTopicId: Long,
    onError: (Throwable) -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var busy by remember(chatId, forumTopicId) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            conversation.sendMessage(
                                chatId,
                                BOT_START_COMMAND,
                                forumTopicId = forumTopicId,
                                clearDraft = false
                            ).onFailure(onError)
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.chat_bot_start), color = palette.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
