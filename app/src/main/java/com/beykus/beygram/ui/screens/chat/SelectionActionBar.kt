package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette

/** Top bar for the multi-select mode: selection count and the batch actions that apply to the current selection. */
@Composable
internal fun SelectionActionBar(
    count: Int,
    busy: Boolean,
    error: String?,
    canCopy: Boolean,
    canSave: Boolean,
    onClose: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    val palette = beyGramPalette()
    val countLabel = stringResource(R.string.message_selection_count, count)
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.message_selection_exit), tint = palette.textPrimary)
            }
            Text(
                countLabel,
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SelectionAction(Icons.AutoMirrored.Rounded.Forward, stringResource(R.string.message_action_forward), palette.accent, enabled = !busy && count > 0, onClick = onForward)
            SelectionAction(Icons.Rounded.ContentCopy, stringResource(R.string.message_selection_copy), palette.accent, enabled = !busy && canCopy, onClick = onCopy)
            SelectionAction(Icons.Rounded.Download, stringResource(R.string.message_selection_save), palette.accent, enabled = !busy && canSave, onClick = onSave)
            SelectionAction(Icons.Rounded.Delete, stringResource(R.string.common_delete), palette.danger, enabled = !busy && count > 0, onClick = onDelete)
        }
        error?.let {
            Text(
                it,
                color = palette.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).errorAnnouncement()
            )
        }
        HorizontalDivider(color = palette.divider)
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(enabled = enabled, onClick = onClick) {
        Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}

/** Confirmation for deleting a selection; only scopes supported by every selected message are offered. */
@Composable
internal fun SelectionDeleteDialog(
    count: Int,
    rights: BatchDeleteRights,
    everyone: Boolean,
    busy: Boolean,
    error: String?,
    onChoose: (everyone: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.message_selection_delete_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(stringResource(R.string.message_selection_count, count), color = palette.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                if (rights.canDeleteForSelf) DeleteChoice(
                    stringResource(R.string.message_delete_only_me), !everyone, !busy
                ) { onChoose(false) }
                if (rights.canDeleteForAll) DeleteChoice(
                    stringResource(R.string.message_delete_for_all), everyone, !busy
                ) { onChoose(true) }
                Text(
                    stringResource(R.string.message_delete_irreversible),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                error?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).errorAnnouncement())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) {
                Text(stringResource(R.string.common_delete), color = palette.danger, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun DeleteChoice(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = palette.accent)
        )
        Text(text, color = palette.textPrimary, fontSize = 16.sp)
    }
}

/** Check circle shown over every selected bubble; an empty circle marks selectable messages while selection is active. */
@Composable
internal fun SelectionCheckBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Box(
        modifier.size(24.dp).clip(CircleShape)
            .background(if (selected) palette.accent else palette.surface.copy(alpha = 0.9f))
            .border(2.dp, palette.accent, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(16.dp))
    }
}
