package com.beykus.beygram.ui.screens.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.notifications.TelegramNotificationService
import com.beykus.beygram.notifications.AndroidNotificationRenderer
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.resolve

@Composable
internal fun NotificationPermissionCoordinator(runtime: NotificationRuntime) {
    val context = LocalContext.current
    val config by runtime.preferences.state.collectAsStateWithLifecycle()
    val sessionReady by runtime.authorized.collectAsStateWithLifecycle()
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    LaunchedEffect(runtime) {
        if (Build.VERSION.SDK_INT >= 33 && !runtime.preferences.permissionAsked && !AndroidNotificationRenderer.canNotify(context)) {
            runtime.preferences.permissionAsked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LifecycleResumeEffect(runtime, config, permissionRevision to sessionReady) {
        runtime.refresh()
        TelegramNotificationService.reconcileFromAllowedContext(context, "ui")
        onPauseOrDispose { }
    }
}

@Composable
fun NotificationSettingsScreen(
    runtime: NotificationRuntime,
    connectionState: TelegramConnectionState,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val config by runtime.preferences.state.collectAsStateWithLifecycle()
    val running by runtime.serviceRunning.collectAsStateWithLifecycle()
    val error by runtime.serviceError.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(AndroidNotificationRenderer.canNotify(context)) }
    var messagesBlocked by remember { mutableStateOf(AndroidNotificationRenderer.messagesChannelBlocked(context)) }
    var connectionBlocked by remember { mutableStateOf(AndroidNotificationRenderer.channelBlocked(context, AndroidNotificationRenderer.CONNECTION)) }
    var proxyBlocked by remember { mutableStateOf(AndroidNotificationRenderer.channelBlocked(context, AndroidNotificationRenderer.PROXY)) }
    val power = context.getSystemService(PowerManager::class.java)
    var unrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    LifecycleResumeEffect(runtime, permissionRevision) {
        allowed = AndroidNotificationRenderer.canNotify(context)
        messagesBlocked = AndroidNotificationRenderer.messagesChannelBlocked(context)
        connectionBlocked = AndroidNotificationRenderer.channelBlocked(context, AndroidNotificationRenderer.CONNECTION)
        proxyBlocked = AndroidNotificationRenderer.channelBlocked(context, AndroidNotificationRenderer.PROXY)
        unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        runtime.refresh()
        TelegramNotificationService.reconcileFromAllowedContext(context, "ui")
        onPauseOrDispose { }
    }
    BackHandler(onBack = onBack)
    fun open(intent: Intent) { runCatching { context.startActivity(intent) } }
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.notif_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.notif_no_google_title), color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.notif_no_google_body), color = palette.textSecondary)
            Text(stringResource(R.string.settings_autosave_note), color = palette.textSecondary, fontSize = 13.sp)
            SettingToggle(stringResource(R.string.notif_toggle_messages), stringResource(R.string.notif_toggle_messages_hint), config.enabled) { runtime.preferences.update(config.copy(enabled = it)); runtime.refresh() }
            SettingToggle(stringResource(R.string.notif_toggle_background), stringResource(R.string.notif_toggle_background_hint), config.background, config.enabled) { runtime.preferences.update(config.copy(background = it)) }
            SettingToggle(stringResource(R.string.notif_toggle_previews), stringResource(R.string.notif_toggle_previews_hint), config.previews, config.enabled) { runtime.preferences.update(config.copy(previews = it)) }
            HorizontalDivider(color = palette.divider)
            Text(if (allowed) stringResource(R.string.notif_permission_granted) else stringResource(R.string.notif_permission_denied), color = if (allowed) palette.textPrimary else palette.warningText)
            Text(if (running) stringResource(R.string.notif_background_running) else stringResource(R.string.notif_background_stopped), color = palette.textSecondary)
            if (messagesBlocked) Text(stringResource(R.string.notif_messages_channel_blocked), color = palette.warningText)
            if (!allowed) Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !runtime.preferences.permissionAsked) { runtime.preferences.permissionAsked = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(stringResource(R.string.notif_allow_notifications)) }
            TextButton(onClick = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(stringResource(R.string.notif_sound_settings)) }
            ChannelStatus(stringResource(R.string.notif_channel_messages), messagesBlocked)
            TextButton(onClick = { open(channelSettings(context, AndroidNotificationRenderer.MESSAGES)) }) { Text(stringResource(R.string.notif_channel_settings_messages)) }
            ChannelStatus(stringResource(R.string.notif_channel_connection), connectionBlocked)
            TextButton(onClick = { open(channelSettings(context, AndroidNotificationRenderer.CONNECTION)) }) { Text(stringResource(R.string.notif_channel_settings_connection)) }
            ChannelStatus(stringResource(R.string.proxy_notification_title), proxyBlocked)
            TextButton(onClick = { open(channelSettings(context, AndroidNotificationRenderer.PROXY)) }) { Text(stringResource(R.string.notif_channel_settings_proxy)) }
            HorizontalDivider(color = palette.divider)
            Text(if (unrestricted) stringResource(R.string.notif_battery_unrestricted) else stringResource(R.string.notif_battery_restricted), color = palette.textSecondary)
            if (!unrestricted) OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                if (intent.resolveActivity(context.packageManager) != null) open(intent) else open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text(stringResource(R.string.notif_allow_background_work)) }
            Text(stringResource(R.string.notif_battery_note), color = palette.textSecondary, fontSize = 13.sp)
            error?.let { Text(it.resolve(), color = palette.danger) }
            HorizontalDivider(color = palette.divider)
            Row(Modifier.fillMaxWidth().clickable(onClick = onDiagnostics), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notif_diagnostics_title), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.notif_connection_status, stringResource(connectionState.statusLabelRes())), color = palette.textSecondary)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary)
            }
        }
    }
}

private fun channelSettings(context: Context, channelId: String): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

@Composable
private fun ChannelStatus(name: String, blocked: Boolean) {
    val palette = beyGramPalette()
    Text(
        if (blocked) stringResource(R.string.notif_channel_status_off, name) else stringResource(R.string.notif_channel_status_on, name),
        color = if (blocked) palette.warningText else palette.textSecondary
    )
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = palette.textSecondary, fontSize = 14.sp, lineHeight = 19.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@StringRes
internal fun TelegramConnectionState.statusLabelRes(): Int = when (this) {
    TelegramConnectionState.Connected -> R.string.connection_state_connected
    TelegramConnectionState.Connecting -> R.string.connection_state_connecting
    TelegramConnectionState.Updating -> R.string.connection_state_updating
    TelegramConnectionState.WaitingForNetwork -> R.string.connection_state_waiting_network
    TelegramConnectionState.Unknown -> R.string.connection_state_unknown
}
