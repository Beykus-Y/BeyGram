package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.ProfileMemberRole
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.StateFlow

/** Пометка рядом с именем участника. Текст выбирает интерфейс: строки живут в ресурсах. */
internal sealed interface MemberMark {
    /** Владелец или администратор: [tag] заменяет подпись роли, если он задан. */
    data class Staff(val isOwner: Boolean, val tag: String) : MemberMark

    /** Обычный участник с тегом. */
    data class Tag(val tag: String) : MemberMark
}

/** Пометка для участника; null, если у него нет ни роли, ни тега. */
internal fun memberMark(member: ProfileMember): MemberMark? = when (member.role) {
    ProfileMemberRole.Owner -> MemberMark.Staff(isOwner = true, tag = member.tag)
    ProfileMemberRole.Admin -> MemberMark.Staff(isOwner = false, tag = member.tag)
    ProfileMemberRole.Member -> member.tag.takeIf { it.isNotBlank() }?.let { MemberMark.Tag(it) }
}

/** Строка участника: аватар, имя, статус и пометка роли или тега. Нажатие открывает профиль участника. */
@Composable
internal fun MemberRow(
    member: ProfileMember,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(member.initials, Color(member.avatarColorArgb), 48.dp, member.photo, observeFile, downloadFile)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                member.name.ifBlank { stringResource(R.string.user_name_unknown) }, color = palette.textPrimary,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (member.isBot) Text(stringResource(R.string.presence_bot), color = palette.textSecondary, fontSize = 14.sp, maxLines = 1)
            else if (member.online) Text(stringResource(R.string.presence_online), color = palette.accent, fontSize = 14.sp, maxLines = 1)
        }
        memberMark(member)?.let { mark -> MemberMarkPill(mark) }
    }
}

/** Таблетка с званием или тегом: владелец — залитая акцентом, администратор — мягкий акцент, тег участника — нейтральная. */
@Composable
private fun MemberMarkPill(mark: MemberMark) {
    val palette = beyGramPalette()
    val (container, contentColor) = when {
        mark is MemberMark.Staff && mark.isOwner -> palette.accent to palette.onAccent
        mark is MemberMark.Staff -> palette.accentSoft to palette.accentInk
        else -> palette.surface2 to palette.textSecondary
    }
    val text = when (mark) {
        is MemberMark.Staff -> if (mark.tag.isNotBlank()) mark.tag else stringResource(if (mark.isOwner) R.string.profile_admin_owner else R.string.profile_admin_role)
        is MemberMark.Tag -> mark.tag
    }
    Text(
        text, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp).widthIn(max = 150.dp).clip(RoundedCornerShape(percent = 50)).background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
