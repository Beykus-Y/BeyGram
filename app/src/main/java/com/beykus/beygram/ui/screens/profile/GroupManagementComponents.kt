package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.GroupAdminRights
import com.beykus.beygram.domain.model.GroupMemberEntry
import com.beykus.beygram.domain.model.GroupMemberStatus
import com.beykus.beygram.domain.model.ManagedChatKind
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.ProfileKind
import com.beykus.beygram.ui.accessibility.accessibleDescription
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date

/** Строка-раздел обзора управления: иконка, название, подпись и переход. */
@Composable
internal fun GroupManagementSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 2)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

/** Пустое, ошибочное или подтверждающее сообщение; ошибка озвучивается TalkBack без переноса фокуса. */
@Composable
internal fun GroupManagementMessage(text: String?, error: Boolean = false) {
    if (text.isNullOrBlank()) return
    val palette = beyGramPalette()
    Text(
        text,
        color = if (error) palette.danger else palette.textSecondary,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .then(if (error) Modifier.errorAnnouncement() else Modifier)
    )
}

/** Роль участника: владелец, администратор (в том числе анонимный), участник, ограничен или забанен. */
@Composable
internal fun GroupMemberRolePill(member: GroupMemberEntry) {
    val palette = beyGramPalette()
    val (container, content) = when (member.status) {
        GroupMemberStatus.Owner -> palette.accent to palette.onAccent
        GroupMemberStatus.Administrator -> palette.accentSoft to palette.accentInk
        GroupMemberStatus.Restricted -> palette.warningSurface to palette.warningText
        GroupMemberStatus.Banned -> palette.surface2 to palette.danger
        GroupMemberStatus.Member, GroupMemberStatus.Left -> palette.surface2 to palette.textSecondary
    }
    val text = when {
        member.status == GroupMemberStatus.Member && member.tag.isNotBlank() -> member.tag
        member.status == GroupMemberStatus.Administrator && member.tag.isNotBlank() -> member.tag
        else -> stringResource(member.status.roleLabelRes(member.isAnonymous))
    }
    Text(
        text,
        color = content,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 150.dp).clip(RoundedCornerShape(percent = 50))
            .background(container).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

internal fun GroupMemberStatus.roleLabelRes(anonymous: Boolean): Int = when (this) {
    GroupMemberStatus.Owner -> R.string.group_members_role_owner
    GroupMemberStatus.Administrator -> if (anonymous) R.string.group_members_role_anonymous_admin else R.string.group_members_role_admin
    GroupMemberStatus.Restricted -> R.string.group_members_role_restricted
    GroupMemberStatus.Banned -> R.string.group_members_role_banned
    GroupMemberStatus.Member, GroupMemberStatus.Left -> R.string.group_members_role_member
}

/** Подпись роли для сводки: владелец либо администратор. */
@Composable
internal fun groupViewerRole(rights: GroupAdminRights): String = when {
    rights.isOwner -> stringResource(R.string.group_manage_role_owner)
    rights.isAdministrator -> stringResource(R.string.group_manage_role_admin)
    else -> ""
}

/** Строка участника: профиль открывается нажатием, меню действий — отдельной кнопкой. */
@Composable
internal fun GroupMemberRow(
    member: GroupMemberEntry,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenProfile: () -> Unit,
    onActions: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    val role = stringResource(member.status.roleLabelRes(member.isAnonymous))
    val youLabel = if (member.isCurrentUser) stringResource(R.string.group_members_status_you) else null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpenProfile)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleDescription(
                        member.name.takeIf { it.isNotBlank() },
                        role,
                        member.tag.takeIf { it.isNotBlank() && it != role },
                        youLabel
                    )
                }
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelegramAvatar(member.initials, Color(member.avatarColorArgb), 44.dp, member.photo, observeFile, downloadFile, Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    member.name.ifBlank { stringResource(R.string.user_name_unknown) },
                    color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val secondary = when {
                    member.isCurrentUser -> stringResource(R.string.group_members_status_you)
                    member.online -> stringResource(R.string.presence_online)
                    member.tag.isNotBlank() -> member.tag
                    else -> null
                }
                if (secondary != null) Text(secondary, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GroupMemberRolePill(member)
        }
        if (onActions != null) IconButton(onClick = onActions) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.group_members_actions), tint = palette.textSecondary)
        } else {
            Spacer(Modifier.width(16.dp))
        }
    }
}

/** Раздел с заголовком и карточкой: используется в обзоре и настройках. */
@Composable
internal fun GroupManagementHeading(text: String) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp).semantics { heading() }
    )
}

internal fun formatGroupDate(seconds: Int): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds.coerceAtLeast(0).toLong() * 1000L))

/** Кнопка входа в управление; показывается только роли с хотя бы одним доступным разделом. */
@Composable
internal fun GroupManagementEntry(kind: ProfileKind, onClick: () -> Unit) {
    val palette = beyGramPalette()
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accentSoft, contentColor = palette.accentInk),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp)
    ) {
        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (kind == ProfileKind.Channel) R.string.group_manage_title_channel else R.string.group_manage_title_group),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Ссылки и настройки показывают только то, что разрешено роли и типу чата. */
internal fun showAddMembers(rights: GroupAdminRights): Boolean =
    rights.canAddMembers && rights.kind != ManagedChatKind.Channel
