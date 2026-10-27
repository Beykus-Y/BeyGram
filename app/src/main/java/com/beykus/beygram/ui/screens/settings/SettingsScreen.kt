package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette

/** Settings grouped by purpose. Routes remain owned by the caller. */
@Composable
fun SettingsScreen(onNavigationSettings: () -> Unit, onAppearance: () -> Unit, onLastSeen: () -> Unit,
    onNotifications: () -> Unit, onStorage: () -> Unit, onProxy: () -> Unit, onVideoRecording: () -> Unit,
    onDiagnostics: () -> Unit, onUpdates: () -> Unit, onLanguage: () -> Unit, onSessions: () -> Unit,
    onSecondaryAccounts: () -> Unit, onPrivacy: () -> Unit, onAccountSecurity: () -> Unit, onAutoDownload: () -> Unit, onAppLock: () -> Unit = {}, onEqualizer: () -> Unit = {}, onMusicSearch: () -> Unit = {}) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.settings_title), color = palette.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp).semantics { heading() }
        )

        SettingsGroup(stringResource(R.string.settings_group_interface)) {
            SettingsLink(Icons.Rounded.Language, stringResource(R.string.feature_language), onLanguage)
            SettingsLink(Icons.Rounded.Language, stringResource(R.string.feature_language), onLanguage)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Dashboard, stringResource(R.string.nav_title), onNavigationSettings)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Palette, stringResource(R.string.theme_title), onAppearance)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Videocam, stringResource(R.string.settings_video_recording_title), onVideoRecording)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Notifications, stringResource(R.string.notif_title), onNotifications)
        }
        SettingsGroup(stringResource(R.string.settings_group_sound_playback)) {
            SettingsLink(Icons.Rounded.GraphicEq, stringResource(R.string.settings_equalizer_title), onEqualizer)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.MusicNote, stringResource(R.string.settings_music_title), onMusicSearch)
        }
        SettingsGroup(stringResource(R.string.settings_group_devices_privacy)) {
            SettingsLink(Icons.Rounded.Key, stringResource(R.string.account_security_title), onAccountSecurity)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Fingerprint, stringResource(R.string.app_lock_title), onAppLock)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Security, stringResource(R.string.privacy_title), onPrivacy)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.People, stringResource(R.string.secondary_accounts_title), onSecondaryAccounts)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Devices, stringResource(R.string.feature_sessions), onSessions)
            SettingsRowDivider()
            // Local text formatting only; the server privacy rule lives under PrivacySettings.
            SettingsLink(Icons.Rounded.Schedule, stringResource(R.string.settings_last_seen_title), onLastSeen)
        }
        SettingsGroup(stringResource(R.string.settings_group_network)) {
            SettingsLink(Icons.Rounded.Lock, stringResource(R.string.start_proxy_settings), onProxy)
        }
        SettingsGroup(stringResource(R.string.settings_group_data_diagnostics)) {
            SettingsLink(Icons.Rounded.Storage, stringResource(R.string.settings_storage_title), onStorage)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.Download, stringResource(R.string.settings_auto_download_title), onAutoDownload)
            SettingsRowDivider()
            SettingsLink(Icons.Rounded.BugReport, stringResource(R.string.diagnostics_title), onDiagnostics)
        }
        SettingsGroup(stringResource(R.string.settings_group_updates)) {
            SettingsLink(Icons.Rounded.SystemUpdate, stringResource(R.string.updates_title), onUpdates)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Every group has its own heading and card; labels are announced as headings by TalkBack. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            title,
            color = palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 8.dp)
                .semantics { heading() }
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Text(title, color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}
