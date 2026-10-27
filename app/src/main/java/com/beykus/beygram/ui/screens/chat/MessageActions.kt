package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width

/** Replaces the chat top bar while a message is selected (long press). */
@Composable
internal fun MessageActionsBar(
    capabilities: MessageCapabilities?,
    error: String?,
    hasText: Boolean,
    isMedia: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onCopyLink: () -> Unit = {},
    onCopyEntity: (() -> Unit)? = null,
    onForward: () -> Unit = {},
    onForwardCopy: () -> Unit = {},
    onSelect: () -> Unit = {},
    onTranslate: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    var moreOpen by remember(capabilities, canPin) { mutableStateOf(false) }
    val translate = onTranslate
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textPrimary) }
            Text(stringResource(R.string.message_title), color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            when {
                capabilities != null -> {
                    if (capabilities.canReply) ActionIcon(Icons.AutoMirrored.Rounded.Reply, stringResource(R.string.message_action_reply), palette.accent, onReply)
                    if (capabilities.canCopy && hasText) ActionIcon(Icons.Rounded.ContentCopy, stringResource(R.string.message_action_copy_text), palette.accent, onCopy)
                    if (capabilities.canEdit) ActionIcon(Icons.Rounded.Edit, if (isMedia) stringResource(R.string.message_action_edit_caption) else stringResource(R.string.message_action_edit), palette.accent, onEdit)
                    if (capabilities.canDeleteForSelf || capabilities.canDeleteForAll) ActionIcon(Icons.Rounded.Delete, stringResource(R.string.common_delete), palette.danger, onDelete)
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.message_action_more), tint = palette.accent)
                        }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            onCopyEntity?.let { copy ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.message_action_copy_entity)) },
                                    onClick = { moreOpen = false; copy() }
                                )
                            }
                            if (canPin) DropdownMenuItem(
                                text = { Text(stringResource(if (isPinned) R.string.message_action_unpin else R.string.message_action_pin)) },
                                onClick = { moreOpen = false; onTogglePin() }
                            )
                            if (capabilities.canGetLink) DropdownMenuItem(
                                text = { Text(stringResource(R.string.message_action_copy_link)) },
                                onClick = { moreOpen = false; onCopyLink() }
                            )
                            if (capabilities.canForward) DropdownMenuItem(
                                text = { Text(stringResource(R.string.message_action_forward)) },
                                onClick = { moreOpen = false; onForward() }
                            )
                            if (capabilities.canForward) DropdownMenuItem(
                                text = { Text(stringResource(R.string.message_action_forward_copy)) },
                                onClick = { moreOpen = false; onForwardCopy() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.message_action_select)) },
                                onClick = { moreOpen = false; onSelect() }
                            )
                            if (translate != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.message_action_translate), color = palette.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.Translate, null, tint = palette.accent) },
                                onClick = { moreOpen = false; translate() }
                            )
                            onReport?.let { report -> DropdownMenuItem(
                                text = { Text(stringResource(R.string.message_action_report)) },
                                onClick = { moreOpen = false; report() }
                            ) }
                        }
                    }
                }
                error != null -> TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry), color = palette.accent) }
                else -> CircularProgressIndicator(Modifier.padding(end = 14.dp).size(22.dp), color = palette.accent, strokeWidth = 2.dp)
            }
        }
        error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).errorAnnouncement()) }
        HorizontalDivider(color = palette.divider)
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, description, tint = tint) }
}

/**
 * A long press cannot select a single link inside a bubble without breaking message actions, so this explicit list
 * copies the exact entity the reader chose. Values are already normalized by the caller.
 */
@Composable
internal fun MessageLinkCopyDialog(links: List<String>, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_action_copy_entity)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                links.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onCopy(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            value,
                            color = palette.accentInk,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.ContentCopy, null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider(color = palette.divider)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = palette.accent) }
        }
    )
}

/** Inline delete confirmation shown above the input field. */
@Composable
internal fun DeleteConfirmPanel(
    capabilities: MessageCapabilities,
    everyone: Boolean,
    busy: Boolean,
    error: String?,
    onChoose: (everyone: Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.message_delete_title), color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (capabilities.canDeleteForSelf) DeleteChoice(stringResource(R.string.message_delete_only_me), !everyone, !busy) { onChoose(false) }
        if (capabilities.canDeleteForAll) DeleteChoice(stringResource(R.string.message_delete_for_all), everyone, !busy) { onChoose(true) }
        Text(stringResource(R.string.message_delete_irreversible), color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).errorAnnouncement()) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = palette.accent, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(4.dp))
            TextButton(enabled = !busy, onClick = onConfirm) { Text(stringResource(R.string.common_delete), color = palette.danger, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DeleteChoice(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The whole row is the radio button, so TalkBack has one target instead of a row and a nested radio.
        RadioButton(selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = palette.accent))
        Text(text, color = palette.textPrimary, fontSize = 16.sp)
    }
}

/** Banner above the field while editing: the original text is shown and can be dismissed. */
@Composable
internal fun EditBanner(original: String, isCaption: Boolean, count: Int, limit: Int, error: String?, onClose: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().motionEntrance(MotionToken.BannerChange, 8.dp).background(palette.surface).padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Edit, null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (isCaption) stringResource(R.string.message_edit_caption_banner) else stringResource(R.string.message_edit_banner), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(error ?: original, color = if (error != null) palette.danger else palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = if (error != null) Modifier.errorAnnouncement() else Modifier)
        }
        Text("$count / $limit", color = if (count > limit) palette.danger else palette.textSecondary, fontSize = 12.sp)
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.message_edit_cancel_description), tint = palette.textSecondary) }
    }
}

@Composable internal fun ChatMessage.summary(): String = text.ifBlank {
    when (val content = special) {
        is MessageSpecialContent.Poll -> content.poll.question
        is MessageSpecialContent.Location -> stringResource(R.string.chat_preview_location)
        is MessageSpecialContent.LiveLocation -> stringResource(R.string.chat_preview_live_location)
        is MessageSpecialContent.Venue -> content.venue.title.ifBlank { stringResource(R.string.chat_preview_location) }
        is MessageSpecialContent.Contact -> content.contact.displayName
        is MessageSpecialContent.Dice -> stringResource(R.string.chat_preview_dice, content.dice.emoji)
        is MessageSpecialContent.PaidMedia -> stringResource(R.string.paid_content_preview_media)
        is MessageSpecialContent.Invoice -> content.invoice.title.ifBlank { stringResource(R.string.paid_content_invoice) }
        is MessageSpecialContent.Unsupported -> stringResource(R.string.chat_unsupported_message)
        null -> media?.kind?.label(LocalContext.current) ?: stringResource(R.string.message_title)
    }
}.take(180)
