package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.ProfileMemberRole
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve

internal fun groupSenderTag(member: ProfileMember?): UiText? = member?.let {
    val title = it.tag.takeIf(String::isNotBlank)
    when {
        title != null -> UiText.Raw(title)
        it.role == ProfileMemberRole.Owner -> UiText.Res(R.string.group_members_role_owner)
        it.role == ProfileMemberRole.Admin -> UiText.Res(R.string.group_members_role_admin)
        else -> null
    }
}

@Composable
internal fun ChatGroupAuthorName(
    name: String,
    member: ProfileMember?,
    modifier: Modifier,
    observeCustomEmoji: ObserveCustomEmoji,
    observeFile: ObserveMediaFile,
    downloadFile: DownloadMediaFile
) {
    val palette = beyGramPalette()
    val authorColor = LocalChatAuthorColor.current.takeOrElse { palette.accentInk }
    val tag = groupSenderTag(member)
    val status = LocalChatAuthorStatus.current
    var statusActive by remember(status) {
        mutableStateOf(status?.isActive(System.currentTimeMillis() / 1000) == true)
    }
    LaunchedEffect(status) {
        if (status != null && status.expiresAtSeconds > 0L) {
            delay((status.expiresAtSeconds * 1000 - System.currentTimeMillis()).coerceAtLeast(0))
            statusActive = false
        }
    }
    val emoji = if (statusActive && status != null) {
        val current by remember(observeCustomEmoji, status.customEmojiId) {
            observeCustomEmoji(status.customEmojiId)
        }.collectAsStateWithLifecycle()
        current?.takeIf { it.customEmojiId == status.customEmojiId }
    } else null
    if (tag == null && emoji == null) {
        Text(name, color = authorColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = authorColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f, fill = false).alignByBaseline())
        if (emoji != null) {
            val description = stringResource(R.string.chat_author_emoji_status)
            Box(Modifier.semantics { contentDescription = description }) {
                CustomEmojiImage(emoji, 16.dp, observeFile, downloadFile) {
                    Text(emoji.emoji, fontSize = 12.sp)
                }
            }
        }
        if (tag != null) {
            Text(tag.resolve(), color = palette.textSecondary, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
        }
    }
}
