package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperPresets
import com.beykus.beygram.data.settings.ChatOverridesStore
import com.beykus.beygram.domain.model.ChatNotificationPreferences
import com.beykus.beygram.domain.model.NotificationSoundOption
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.theme.wallpaperLabel
import com.beykus.beygram.ui.screens.theme.wallpaperPresetNameRes
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

private const val NO_SOUND_ID = 0L

/**
 * Local wallpaper plus TDLib-backed notification overrides for one chat. Nothing is written to the global
 * appearance config, so other chats keep the app-wide choice.
 */
@Composable
internal fun ChatSettingsSheet(
    chatId: Long,
    conversation: ConversationRepository,
    overrides: ChatOverridesStore,
    globalWallpaper: ChatWallpaper,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOverride = overrides.state.value[chatId]?.wallpaper
    var wallpaper by remember(chatId) { mutableStateOf(currentOverride) }
    var preferences by remember(chatId) { mutableStateOf<ChatNotificationPreferences?>(null) }
    var sounds by remember(chatId) { mutableStateOf<List<NotificationSoundOption>>(emptyList()) }
    var loading by remember(chatId) { mutableStateOf(true) }
    var busy by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var soundMenu by remember { mutableStateOf(false) }

    LaunchedEffect(chatId, conversation) {
        loading = true
        error = null
        conversation.chatNotificationPreferences(chatId)
            .onSuccess { preferences = it }
            .onFailure { error = it.userMessage(context, R.string.chat_settings_load_failed) }
        conversation.savedNotificationSounds().onSuccess { sounds = it }
        loading = false
    }

    fun saveNotifications(next: ChatNotificationPreferences) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                conversation.setChatNotificationPreferences(chatId, next)
                    .onSuccess { preferences = next }
                    .onFailure { error = it.userMessage(context, R.string.chat_settings_save_failed) }
            } finally {
                busy = false
            }
        }
    }

    fun chooseWallpaper(next: ChatWallpaper?) {
        wallpaper = next
        overrides.setWallpaper(chatId, next)
    }

    val effectiveWallpaper = wallpaper ?: globalWallpaper
    val soundLabel = when {
        preferences?.useDefaultSound != false -> stringResource(R.string.chat_settings_sound_default)
        (preferences?.soundId ?: 0L) == NO_SOUND_ID -> stringResource(R.string.chat_settings_sound_silent)
        else -> sounds.firstOrNull { it.id == preferences?.soundId }?.title
            ?: stringResource(R.string.chat_settings_sound_custom)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.chat_settings_title), color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                error?.let { Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = palette.accent, strokeWidth = 2.dp)
                    return@Column
                }

                Text(stringResource(R.string.chat_settings_wallpaper), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(wallpaperLabel(effectiveWallpaper), color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { chooseWallpaper(null) }) { Text(stringResource(R.string.chat_settings_wallpaper_global)) }
                    TextButton(onClick = { chooseWallpaper(ChatWallpaper.None) }) { Text(stringResource(R.string.chat_settings_wallpaper_none)) }
                }
                var wallpaperMenu by remember { mutableStateOf(false) }
                Column {
                    TextButton(onClick = { wallpaperMenu = true }) { Text(stringResource(R.string.chat_settings_choose_wallpaper)) }
                    DropdownMenu(expanded = wallpaperMenu, onDismissRequest = { wallpaperMenu = false }) {
                        WallpaperPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(stringResource(wallpaperPresetNameRes(preset.id))) },
                                onClick = {
                                    wallpaperMenu = false
                                    chooseWallpaper(ChatWallpaper.Preset(preset.id))
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.chat_settings_notifications), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val current = preferences
                if (current == null) {
                    Text(stringResource(R.string.chat_settings_load_failed), color = palette.danger, fontSize = 13.sp)
                } else {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.chat_settings_sound), color = palette.textPrimary)
                            Text(soundLabel, color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { soundMenu = true }, enabled = !busy) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.chat_settings_choose_sound), tint = palette.textSecondary)
                        }
                    }
                    DropdownMenu(expanded = soundMenu, onDismissRequest = { soundMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_settings_sound_default)) },
                            onClick = {
                                soundMenu = false
                                saveNotifications(current.copy(useDefaultSound = true, soundId = 0L))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_settings_sound_silent)) },
                            onClick = {
                                soundMenu = false
                                saveNotifications(current.copy(useDefaultSound = false, soundId = NO_SOUND_ID))
                            }
                        )
                        sounds.forEach { sound ->
                            DropdownMenuItem(
                                text = { Text(sound.title) },
                                leadingIcon = if (current.soundId == sound.id && !current.useDefaultSound) {
                                    { Icon(Icons.Rounded.Check, null, tint = palette.accent) }
                                } else null,
                                onClick = {
                                    soundMenu = false
                                    saveNotifications(current.copy(useDefaultSound = false, soundId = sound.id))
                                }
                            )
                        }
                    }
                    SettingSwitch(
                        title = stringResource(R.string.chat_settings_preview),
                        checked = current.showPreview,
                        enabled = !busy
                    ) { saveNotifications(current.copy(showPreview = it)) }
                    SettingSwitch(
                        title = stringResource(R.string.chat_settings_mentions),
                        checked = !current.disableMentionNotifications,
                        enabled = !busy
                    ) { saveNotifications(current.copy(disableMentionNotifications = !it)) }
                    TextButton(
                        onClick = {
                            chooseWallpaper(null)
                            saveNotifications(ChatNotificationPreferences())
                        },
                        enabled = !busy
                    ) { Text(stringResource(R.string.chat_settings_reset), color = palette.accent) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = palette.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
