package com.beykus.beygram.ui.screens.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.StoryPrivacy
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette

/** Caption/privacy editor shared by the active viewer and the archived-story viewer. */
@Composable
internal fun StoryEditDialog(
    initialCaption: String,
    captionLimit: Int,
    initialPrivacy: StoryPrivacy?,
    initialSelectedUserIds: List<Long>,
    canEditCaption: Boolean,
    canEditPrivacy: Boolean,
    saving: Boolean,
    error: String?,
    repository: TelegramRepository,
    onDismiss: () -> Unit,
    onSave: (caption: String, privacy: StoryPrivacy?, selectedUserIds: List<Long>) -> Unit
) {
    val palette = beyGramPalette()
    var caption by remember { mutableStateOf(initialCaption) }
    var privacy by remember { mutableStateOf(initialPrivacy ?: StoryPrivacy.Everyone) }
    var selected by remember { mutableStateOf(initialSelectedUserIds.toSet()) }
    var showRecipients by remember { mutableStateOf(false) }
    val privacyChanged = canEditPrivacy && privacy != (initialPrivacy ?: StoryPrivacy.Everyone)
    val selectionChanged = canEditPrivacy && selected != initialSelectedUserIds.toSet()
    val captionChanged = canEditCaption && caption != initialCaption
    val canSave = !saving && (captionChanged || privacyChanged || selectionChanged) &&
        (!canEditPrivacy || privacy != StoryPrivacy.SelectedUsers || selected.isNotEmpty())

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(palette.surface).padding(18.dp)
        ) {
            Text(
                stringResource(R.string.stories_edit),
                color = palette.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = caption,
                onValueChange = { value ->
                    if (value.codePointCount(0, value.length) <= captionLimit) caption = value
                },
                label = { Text(stringResource(R.string.stories_caption)) },
                minLines = 2,
                maxLines = 5,
                enabled = !saving && canEditCaption,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            if (canEditPrivacy) {
                Text(
                    stringResource(R.string.stories_privacy),
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        StoryPrivacy.Everyone to R.string.stories_privacy_everyone,
                        StoryPrivacy.Contacts to R.string.stories_privacy_contacts,
                        StoryPrivacy.CloseFriends to R.string.stories_privacy_close_friends,
                        StoryPrivacy.SelectedUsers to R.string.stories_privacy_selected
                    ).forEach { (option, label) ->
                        StoryEditChoice(
                            label = stringResource(label),
                            isSelected = privacy == option,
                            enabled = !saving,
                            onClick = { privacy = option }
                        )
                    }
                }
                if (privacy == StoryPrivacy.SelectedUsers) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !saving, onClick = { showRecipients = true }) {
                            Text(stringResource(R.string.stories_choose_recipients))
                        }
                        Text(
                            stringResource(R.string.stories_selected_count, selected.size),
                            color = if (selected.isEmpty()) palette.danger else palette.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.stories_edit_privacy_read_only),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            error?.let {
                Text(
                    it,
                    color = palette.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).errorAnnouncement()
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(
                    enabled = canSave,
                    onClick = { onSave(caption.trimEnd(), if (canEditPrivacy) privacy else null, selected.toList()) }
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
    if (showRecipients) {
        StoryRecipientsDialog(
            repository = repository,
            selected = selected,
            onDismiss = { showRecipients = false },
            onConfirm = { selected = it; showRecipients = false }
        )
    }
}

@Composable
private fun StoryEditChoice(label: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) palette.accent else palette.surface2)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = isSelected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (isSelected) palette.onAccent else palette.textPrimary,
            fontSize = 14.sp
        )
    }
}

@Composable
internal fun StoryPrivacy.label(): String = when (this) {
    StoryPrivacy.Everyone -> stringResource(R.string.stories_privacy_everyone)
    StoryPrivacy.Contacts -> stringResource(R.string.stories_privacy_contacts)
    StoryPrivacy.CloseFriends -> stringResource(R.string.stories_privacy_close_friends)
    StoryPrivacy.SelectedUsers -> stringResource(R.string.stories_privacy_selected)
}
