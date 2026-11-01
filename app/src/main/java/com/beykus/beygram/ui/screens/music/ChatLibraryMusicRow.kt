package com.beykus.beygram.ui.screens.music

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.music.fromChat
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * What a chat's profile needs to show the library tracks of that chat: the account whose library is counted, which is also the
 * account the opened collection plays from, and the action that opens the collection for a chat id.
 */
internal class ChatLibraryLink(val accountId: String, val onOpen: (chatId: Long) -> Unit)

/**
 * The entry of a chat's profile that opens the library tracks which came from [chatId], in the library of [accountId]. Nothing
 * is shown when that library holds no such track, so a chat without music in the library gets no row.
 */
@Composable
internal fun ChatLibraryMusicRow(chatId: Long, accountId: String, onOpen: () -> Unit) {
    val palette = beyGramPalette()
    val library = rememberMusicLibrary(accountId)
    val count = remember(library, chatId) { library?.let { fromChat(it, chatId).size } ?: 0 }
    if (count == 0) return
    TextButton(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp)
    ) {
        Text(
            stringResource(R.string.profile_chat_music_row, pluralStringResource(R.plurals.music_track_count, count, count)),
            color = palette.accentInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
