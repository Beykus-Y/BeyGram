package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.ui.components.beyGramPalette

/** Настройки приложения: нижнее меню, уведомления и диагностика. */
@Composable
fun SettingsScreen(onNavigationSettings: () -> Unit, onLastSeen: () -> Unit, onNotifications: () -> Unit, onStorage: () -> Unit, onProxy: () -> Unit, onVideoRecording: () -> Unit, onDiagnostics: () -> Unit, onUpdates: () -> Unit) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        Text(
            stringResource(R.string.settings_title), color = palette.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
        )
        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)) {
            SettingsLink(Icons.Rounded.Dashboard, stringResource(R.string.nav_title), onNavigationSettings)
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
            SettingsLink(Icons.Rounded.Schedule, stringResource(R.string.settings_last_seen_title), onLastSeen)
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
            SettingsLink(Icons.Rounded.Videocam, stringResource(R.string.settings_video_recording_title), onVideoRecording)
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
            SettingsLink(Icons.Rounded.Lock, stringResource(R.string.start_proxy_settings), onProxy)
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
            SettingsLink(Icons.Rounded.Notifications, stringResource(R.string.notif_title), onNotifications)
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
            SettingsLink(Icons.Rounded.Storage, stringResource(R.string.settings_storage_title), onStorage)
            HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
            SettingsLink(Icons.Rounded.BugReport, stringResource(R.string.diagnostics_title), onDiagnostics)
            if (BuildConfig.APPLICATION_ID == "com.beykus.beygram") {
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
                SettingsLink(Icons.Rounded.SystemUpdate, stringResource(R.string.updates_title), onUpdates)
            }
        }
    }
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Text(title, color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}
