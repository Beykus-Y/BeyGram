package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.ui.components.beyGramPalette
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width

/** Replaces the chat top bar while a message is selected (long press). */
@Composable
internal fun MessageActionsBar(
    capabilities: MessageCapabilities?,
    error: String?,
    hasText: Boolean,
    isMedia: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть", tint = palette.textPrimary) }
            Text("Сообщение", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            when {
                capabilities != null -> {
                    if (capabilities.canReply) ActionIcon(Icons.AutoMirrored.Rounded.Reply, "Ответить", palette.accent, onReply)
                    if (capabilities.canCopy && hasText) ActionIcon(Icons.Rounded.ContentCopy, "Копировать текст", palette.accent, onCopy)
                    if (capabilities.canEdit) ActionIcon(Icons.Rounded.Edit, if (isMedia) "Изменить подпись" else "Редактировать", palette.accent, onEdit)
                    if (capabilities.canDeleteForSelf || capabilities.canDeleteForAll) ActionIcon(Icons.Rounded.Delete, "Удалить", palette.danger, onDelete)
                }
                error != null -> TextButton(onClick = onRetry) { Text("Повторить", color = palette.accent) }
                else -> CircularProgressIndicator(Modifier.padding(end = 14.dp).size(22.dp), color = palette.accent, strokeWidth = 2.dp)
            }
        }
        error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) }
        HorizontalDivider(color = palette.divider)
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, description, tint = tint) }
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
        Text("Удалить сообщение?", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (capabilities.canDeleteForSelf) DeleteChoice("Только у меня", !everyone, !busy) { onChoose(false) }
        if (capabilities.canDeleteForAll) DeleteChoice("У всех", everyone, !busy) { onChoose(true) }
        Text("Удаление нельзя отменить.", color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = onCancel) { Text("Отмена", color = palette.accent, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(4.dp))
            TextButton(enabled = !busy, onClick = onConfirm) { Text("Удалить", color = palette.danger, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DeleteChoice(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = onSelect, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = palette.accent))
        Text(text, color = palette.textPrimary, fontSize = 16.sp)
    }
}

/** Banner above the field while editing: the original text is shown and can be dismissed. */
@Composable
internal fun EditBanner(original: String, isCaption: Boolean, count: Int, limit: Int, error: String?, onClose: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().background(palette.surface).padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Edit, null, tint = palette.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (isCaption) "Редактирование подписи" else "Редактирование", color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(error ?: original, color = if (error != null) palette.danger else palette.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text("$count / $limit", color = if (count > limit) palette.danger else palette.textSecondary, fontSize = 12.sp)
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Отменить редактирование", tint = palette.textSecondary) }
    }
}

internal fun ChatMessage.summary(): String = text.ifBlank { media?.kind?.label() ?: "Сообщение" }.take(180)
