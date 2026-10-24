package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Настройки группы или канала. Показываются только поля, которые роль действительно может менять:
 * название, описание и фото — с can_change_info, username — только владельцу, медленный режим и
 * одобрение заявок — с can_restrict_members, видимость истории — с can_change_info.
 */
@Composable
internal fun GroupSettingsScreen(
    info: GroupManagementInfo,
    repository: TelegramRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rights = info.rights
    val kind = info.kind
    var title by rememberSaveable(info.chatId) { mutableStateOf(info.title) }
    var description by rememberSaveable(info.chatId) { mutableStateOf(info.description) }
    var username by rememberSaveable(info.chatId) { mutableStateOf(info.username) }
    var slowMode by rememberSaveable(info.chatId) { mutableIntStateOf(info.slowModeSeconds) }
    var joinByRequest by rememberSaveable(info.chatId) { mutableStateOf(info.joinByRequest) }
    var history by rememberSaveable(info.chatId) { mutableStateOf(info.allHistoryAvailable) }
    var saving by remember(info.chatId) { mutableStateOf(false) }
    var error by remember(info.chatId) { mutableStateOf<String?>(null) }
    var notice by remember(info.chatId) { mutableStateOf<String?>(null) }
    var discardWarning by remember(info.chatId) { mutableStateOf(false) }
    var photoBusy by remember(info.chatId) { mutableStateOf(false) }
    var photoError by remember(info.chatId) { mutableStateOf<String?>(null) }
    var removingPhoto by remember(info.chatId) { mutableStateOf(false) }
    val importer = remember { OutgoingMediaImporter(context) }

    val canEditProfile = rights.canChangeInfo
    val canEditUsername = rights.canChangeUsername
    val canEditSlowMode = rights.canManageSlowMode
    val canEditRequests = rights.canToggleJoinByRequest
    val canEditHistory = rights.canToggleHistory
    val edit = GroupSettingsEdit(
        title = title.takeIf { canEditProfile && it.trim() != info.title },
        description = description.takeIf { canEditProfile && it != info.description },
        username = username.takeIf { canEditUsername && it.trim().removePrefix("@") != info.username },
        slowModeSeconds = slowMode.takeIf { canEditSlowMode && it != info.slowModeSeconds },
        joinByRequest = joinByRequest.takeIf { canEditRequests && it != info.joinByRequest },
        allHistoryAvailable = history.takeIf { canEditHistory && it != info.allHistoryAvailable }
    )
    val changed = edit != GroupSettingsEdit()
    val problem = groupSettingsProblem(edit)
    val problemText = when (problem) {
        GroupSettingsProblem.EmptyTitle -> stringResource(R.string.err_group_title_required)
        GroupSettingsProblem.TitleTooLong -> stringResource(R.string.err_group_title_too_long)
        GroupSettingsProblem.DescriptionTooLong -> stringResource(R.string.err_group_description_too_long)
        GroupSettingsProblem.UsernameInvalid -> stringResource(R.string.err_username_invalid)
        null -> null
    }
    val titleLimit = GROUP_TITLE_MAX_LENGTH
    val descriptionLimit = GROUP_DESCRIPTION_MAX_LENGTH
    val descriptionCount = description.codePointCount(0, description.length)
    val nothingAvailable = !canEditProfile && !canEditUsername && !canEditSlowMode && !canEditRequests && !canEditHistory

    fun resetChanges() {
        title = info.title
        description = info.description
        username = info.username
        slowMode = info.slowModeSeconds
        joinByRequest = info.joinByRequest
        history = info.allHistoryAvailable
        discardWarning = false
        error = null
        notice = null
    }
    fun leave() { if (!saving && !photoBusy) { if (changed && !discardWarning) discardWarning = true else onBack() } }
    BackHandler(onBack = ::leave)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            photoBusy = true
            photoError = null
            notice = null
            var prepared: OutgoingMedia? = null
            try {
                val media = importer.prepare(uri, asDocument = false, squarePhoto = true)
                prepared = media
                repository.setGroupPhoto(info.chatId, media)
                    .onSuccess {
                        notice = context.getString(R.string.group_settings_photo_updated)
                        onChanged()
                    }
                    .onFailure { photoError = it.userMessage(context, R.string.group_settings_photo_failed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                photoError = failure.userMessage(context, R.string.profile_photo_prepare_failed)
            } finally {
                prepared?.let { importer.discard(it) }
                photoBusy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
    ) {
        ProfileToolbar(
            stringResource(if (kind == ManagedChatKind.Channel) R.string.group_settings_title_channel else R.string.group_settings_title_group),
            ::leave
        ) {
            TextButton(
                enabled = changed && problem == null && !saving && !photoBusy,
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        notice = null
                        discardWarning = false
                        repository.saveGroupSettings(info.chatId, edit).fold(
                            onSuccess = {
                                notice = context.getString(R.string.group_settings_saved)
                                onChanged()
                            },
                            onFailure = {
                                error = it.userMessage(context, R.string.group_settings_save_failed)
                                // Часть запросов могла примениться до отказа: показываем актуальное состояние.
                                onChanged()
                            }
                        )
                        saving = false
                    }
                }
            ) { Text(stringResource(if (saving) R.string.common_saving else R.string.common_save)) }
        }
        if (saving || photoBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        if (discardWarning) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(palette.warningSurface)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.profile_unsaved_changes),
                color = palette.warningText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onBack() }, enabled = !saving && !photoBusy) {
                Text(stringResource(R.string.common_close), color = palette.warningText, fontWeight = FontWeight.SemiBold)
            }
        }
        GroupManagementMessage(error, error = true)
        GroupManagementMessage(notice)
        if (nothingAvailable) {
            Text(
                stringResource(R.string.group_settings_nothing),
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        } else Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (canEditProfile) {
                GroupManagementHeading(stringResource(R.string.group_settings_photo_label))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        TelegramAvatar(
                            info.initials, palette.accent, 72.dp, info.photo, observeFile, downloadFile
                        )
                        if (photoBusy) CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = !photoBusy && !saving,
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.group_settings_photo_change)) }
                        if (info.photo != null) TextButton(
                            enabled = !photoBusy && !saving,
                            onClick = { removingPhoto = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.group_settings_photo_remove), color = palette.danger) }
                    }
                }
                GroupManagementMessage(photoError, error = true)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(titleLimit) },
                    label = { Text(stringResource(R.string.group_settings_name_label)) },
                    singleLine = true,
                    enabled = !saving && !photoBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = beyGramTextFieldColors()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.group_settings_description_label)) },
                    supportingText = { Text(stringResource(R.string.group_settings_description_counter, descriptionCount, descriptionLimit)) },
                    isError = descriptionCount > descriptionLimit,
                    minLines = 3,
                    maxLines = 6,
                    enabled = !saving && !photoBusy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    colors = beyGramTextFieldColors()
                )
            }
            if (kind != ManagedChatKind.BasicGroup) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.removePrefix("@").take(32) },
                    label = { Text(stringResource(R.string.group_settings_username_label)) },
                    prefix = { Text("@") },
                    supportingText = {
                        Text(
                            if (canEditUsername) stringResource(R.string.group_settings_username_hint)
                            else stringResource(R.string.group_settings_username_owner_only)
                        )
                    },
                    singleLine = true,
                    enabled = canEditUsername && !saving && !photoBusy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = RoundedCornerShape(14.dp),
                    colors = beyGramTextFieldColors()
                )
            }
            if (canEditSlowMode) {
                GroupManagementHeading(stringResource(R.string.group_settings_slow_mode))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GROUP_SLOW_MODE_VALUES.forEach { value ->
                        FilterChip(
                            selected = slowMode == value,
                            onClick = { slowMode = value },
                            enabled = !saving && !photoBusy,
                            label = { Text(stringResource(value.slowModeLabel()), fontSize = 13.sp) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Text(stringResource(R.string.group_settings_slow_hint), color = palette.textSecondary, fontSize = 13.sp)
            }
            if (canEditRequests) {
                SettingSwitch(
                    title = stringResource(R.string.group_settings_join_by_request),
                    hint = stringResource(R.string.group_settings_join_by_request_hint),
                    checked = joinByRequest,
                    enabled = !saving && !photoBusy,
                    onChange = { joinByRequest = it }
                )
            }
            if (canEditHistory) {
                SettingSwitch(
                    title = stringResource(R.string.group_settings_history),
                    hint = stringResource(R.string.group_settings_history_hint),
                    checked = history,
                    enabled = !saving && !photoBusy,
                    onChange = { history = it }
                )
            }
            problemText?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement()) }
            if (error != null) {
                Text(
                    stringResource(R.string.group_settings_partial_hint),
                    color = palette.textSecondary, fontSize = 12.sp
                )
            }
            if (changed) TextButton(
                onClick = ::resetChanges,
                enabled = !saving && !photoBusy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.group_settings_discard)) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (removingPhoto) AlertDialog(
        onDismissRequest = { if (!photoBusy) removingPhoto = false },
        title = { Text(stringResource(R.string.group_settings_photo_remove_title)) },
        text = { Text(stringResource(R.string.group_settings_photo_remove_warning)) },
        confirmButton = {
            TextButton(
                enabled = !photoBusy,
                onClick = {
                    scope.launch {
                        photoBusy = true
                        photoError = null
                        repository.removeGroupPhoto(info.chatId)
                            .onSuccess {
                                notice = context.getString(R.string.group_settings_photo_removed)
                                removingPhoto = false
                                onChanged()
                            }
                            .onFailure { photoError = it.userMessage(context, R.string.group_settings_photo_failed) }
                        photoBusy = false
                    }
                }
            ) { Text(stringResource(R.string.group_settings_photo_remove), color = palette.danger) }
        },
        dismissButton = { TextButton(enabled = !photoBusy, onClick = { removingPhoto = false }) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = palette.textSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

private fun Int.slowModeLabel(): Int = when (this) {
    5 -> R.string.group_settings_slow_5
    10 -> R.string.group_settings_slow_10
    30 -> R.string.group_settings_slow_30
    60 -> R.string.group_settings_slow_60
    300 -> R.string.group_settings_slow_300
    900 -> R.string.group_settings_slow_900
    3600 -> R.string.group_settings_slow_3600
    else -> R.string.group_settings_slow_off
}
