package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMemoryVersion
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.sheetSurfaceColor
import java.text.DateFormat
import java.util.Date

/** The earlier texts that Chat Memory kept for [message], oldest first. Opened from the bubble's versions link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMemoryVersionsSheet(
    message: ChatMessage,
    load: suspend (chatId: Long, messageId: Long) -> List<ChatMemoryVersion>,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val versions by produceState<List<ChatMemoryVersion>?>(initialValue = null, message.chatId, message.id) {
        value = load(message.chatId, message.id)
    }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetSurfaceColor()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.chat_memory_versions_title), color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            val list = versions
            when {
                list == null -> CircularProgressIndicator(Modifier.padding(vertical = 20.dp))
                list.isEmpty() -> Text(stringResource(R.string.chat_memory_versions_empty), color = palette.textSecondary,
                    fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                else -> list.forEach { version ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.chat_memory_version_replaced, formatter.format(Date(version.replacedAtMillis))),
                            color = palette.textSecondary, fontSize = 12.sp)
                        if (version.text.isNotEmpty()) Text(version.text, color = palette.textPrimary, fontSize = 15.sp)
                        if (version.attachmentNotKept) Text(stringResource(R.string.chat_memory_attachment_not_kept),
                            color = palette.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
