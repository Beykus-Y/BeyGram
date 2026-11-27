package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ProfileMember
import com.beykus.beygram.domain.model.ProfileMemberRole
import com.beykus.beygram.ui.components.beyGramPalette

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
internal fun ChatGroupAuthorName(name: String, member: ProfileMember?, modifier: Modifier) {
    val palette = beyGramPalette()
    val tag = groupSenderTag(member)
    if (tag == null) {
        Text(name, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f, fill = false).alignByBaseline())
        Text(tag.resolve(), color = palette.textSecondary, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
    }
}
