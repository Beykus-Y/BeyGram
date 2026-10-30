package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperPresets
import com.beykus.beygram.data.settings.ChatOverridesStore
import com.beykus.beygram.data.settings.ChatStealthMode
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
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
    isSecretChat: Boolean = false,
    onSecretChatClosed: () -> Unit = {},
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
    val secretChats by conversation.secretChats.collectAsStateWithLifecycle()
    // Null for an account without a TDLib repository: the stealth section is then left out.
    val stealthStore = (conversation as? TdLibTelegramRepository)?.stealthSettings
    val stealthSaveFailed = stringResource(R.string.stealth_save_failed)

    LaunchedEffect(chatId, conversation, isSecretChat) {
        loading = true
        error = null
        conversation.chatNotificationPreferences(chatId)
            .onSuccess { preferences = it }
            .onFailure { error = it.userMessage(context, R.string.chat_settings_load_failed) }
        conversation.savedNotificationSounds().onSuccess { sounds = it }
        if (isSecretChat) conversation.refreshSecretChat(chatId)
        loading = false
    }

    fun saveNotifications(next: ChatNotificationPreferences) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                conversation.setChatNotificationPreferences(chatId, next)
                    // Inherited values come from the scope, so read back what TDLib applies instead of echoing [next].
                    .onSuccess { preferences = conversation.chatNotificationPreferences(chatId).getOrDefault(next) }
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
                // Keep the selection within the current surface: a text-only DropdownMenu above the
                // chat settings dialog hid the appearance of every preset and blocked most of the screen.
                var showWallpaperChoices by remember(chatId) { mutableStateOf(false) }
                TextButton(onClick = { showWallpaperChoices = !showWallpaperChoices }) {
                    Text(stringResource(R.string.chat_settings_choose_wallpaper))
                }
                if (showWallpaperChoices) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(WallpaperPresets, key = { it.id }) { preset ->
                            val selected = (effectiveWallpaper as? ChatWallpaper.Preset)?.id == preset.id
                            val option = ChatWallpaper.Preset(preset.id)
                            val outline = RoundedCornerShape(12.dp)
                            Column(
                                modifier = Modifier.width(112.dp).selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { chooseWallpaper(option) }
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().height(96.dp).clip(outline)
                                        .border(if (selected) 2.dp else 1.dp, if (selected) palette.accent else palette.divider, outline)
                                ) {
                                    ChatWallpaperLayer(option, Modifier.fillMaxSize())
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = palette.accentInk,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                        )
                                    }
                                }
                                Text(
                                    stringResource(wallpaperPresetNameRes(preset.id)),
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
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
                        IconButton(onClick = { soundMenu = !soundMenu }, enabled = !busy) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                stringResource(R.string.chat_settings_choose_sound),
                                tint = palette.textSecondary
                            )
                        }
                    }
                    // No dropdown layered over the already-open settings dialog. Keep many saved sounds scrollable.
                    if (soundMenu) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.surface2)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp)
                        ) {
                            ChatSoundChoice(
                                label = stringResource(R.string.chat_settings_sound_default),
                                selected = current.useDefaultSound,
                                enabled = !busy
                            ) {
                                soundMenu = false
                                saveNotifications(current.copy(useDefaultSound = true, soundId = 0L))
                            }
                            ChatSoundChoice(
                                label = stringResource(R.string.chat_settings_sound_silent),
                                selected = !current.useDefaultSound && current.soundId == NO_SOUND_ID,
                                enabled = !busy
                            ) {
                                soundMenu = false
                                saveNotifications(current.copy(useDefaultSound = false, soundId = NO_SOUND_ID))
                            }
                            sounds.forEach { sound ->
                                ChatSoundChoice(
                                    label = sound.title,
                                    selected = !current.useDefaultSound && current.soundId == sound.id,
                                    enabled = !busy
                                ) {
                                    soundMenu = false
                                    saveNotifications(current.copy(useDefaultSound = false, soundId = sound.id))
                                }
                            }
                        }
                    }
                    SettingSwitch(
                        title = stringResource(R.string.chat_settings_preview),
                        checked = current.showPreview,
                        enabled = !busy
                    ) { saveNotifications(current.copy(showPreview = it, useDefaultShowPreview = false)) }
                    SettingSwitch(
                        title = stringResource(R.string.chat_settings_mentions),
                        checked = !current.disableMentionNotifications,
                        enabled = !busy
                    ) { saveNotifications(current.copy(disableMentionNotifications = !it, useDefaultDisableMentionNotifications = false)) }
                    TextButton(
                        onClick = {
                            chooseWallpaper(null)
                            saveNotifications(ChatNotificationPreferences())
                        },
                        enabled = !busy
                    ) { Text(stringResource(R.string.chat_settings_reset), color = palette.accent) }
                }
                stealthStore?.let { store ->
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 8.dp))
                    ChatStealthSection(
                        chatId = chatId,
                        store = store,
                        onSaved = { saved -> if (!saved) error = stealthSaveFailed else if (error == stealthSaveFailed) error = null }
                    )
                }
                if (isSecretChat) {
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 8.dp))
                    SecretChatSettingsSection(
                        chatId = chatId,
                        details = secretChats[chatId],
                        conversation = conversation,
                        onSecretChatClosed = onSecretChatClosed
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/** A radio choice in the chat settings surface, without opening another modal layer. */
@Composable
private fun ChatSoundChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
internal fun SettingSwitch(title: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            // One toggle target and one accessibility switch state. The visual thumb must not
            // expose a second action, or a tap on the thumb may bypass the row semantics.
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The per-chat stealth exception. The account-wide switches apply to every chat that is not named here. The online
 * status is the same in all chats, so no choice in this section changes it.
 */
@Composable
private fun ChatStealthSection(chatId: Long, store: StealthSettingsStore, onSaved: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    val settings by store.state.collectAsStateWithLifecycle()
    val mode = settings.chats[chatId] ?: ChatStealthMode.Inherit

    fun choose(next: ChatStealthMode) {
        val saved = store.update { current ->
            current.copy(chats = if (next == ChatStealthMode.Inherit) current.chats - chatId else current.chats + (chatId to next))
        }
        onSaved(saved)
    }

    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.stealth_chat_title), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        ChatStealthOption(stringResource(R.string.stealth_chat_inherit), selected = mode == ChatStealthMode.Inherit) { choose(ChatStealthMode.Inherit) }
        ChatStealthOption(stringResource(R.string.stealth_chat_allow), selected = mode == ChatStealthMode.Allow) { choose(ChatStealthMode.Allow) }
        ChatStealthOption(stringResource(R.string.stealth_chat_block), selected = mode == ChatStealthMode.Block) { choose(ChatStealthMode.Block) }
        Text(stringResource(R.string.stealth_chat_footnote), color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ChatStealthOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The row is the radio button for TalkBack, so the radio itself takes no clicks.
        RadioButton(selected = selected, onClick = null)
        Text(label, color = palette.textPrimary, modifier = Modifier.padding(start = 4.dp))
    }
}
