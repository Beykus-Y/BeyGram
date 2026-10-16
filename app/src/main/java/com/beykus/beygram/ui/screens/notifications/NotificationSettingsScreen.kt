package com.beykus.beygram.ui.screens.notifications

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.notifications.TelegramNotificationService
import com.beykus.beygram.notifications.AndroidNotificationRenderer
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

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
        TelegramNotificationService.reconcileFromAllowedContext(context)
        onPauseOrDispose { }
    }
}

@Composable
fun NotificationSettingsScreen(runtime: NotificationRuntime, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val config by runtime.preferences.state.collectAsStateWithLifecycle()
    val running by runtime.serviceRunning.collectAsStateWithLifecycle()
    val error by runtime.serviceError.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(AndroidNotificationRenderer.canNotify(context)) }
    val power = context.getSystemService(PowerManager::class.java)
    var unrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    LifecycleResumeEffect(runtime, permissionRevision) {
        allowed = AndroidNotificationRenderer.canNotify(context)
        unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        runtime.refresh()
        TelegramNotificationService.reconcileFromAllowedContext(context)
        onPauseOrDispose { }
    }
    BackHandler(onBack = onBack)
    fun open(intent: Intent) { runCatching { context.startActivity(intent) } }
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar("Уведомления", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Без Google-сервисов", color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("BeyGram получает сообщения напрямую от Telegram. Google Play Services и Firebase не используются.", color = palette.textSecondary)
            SettingToggle("Уведомления о сообщениях", "Учитывают настройки чатов в Telegram", config.enabled) { runtime.preferences.update(config.copy(enabled = it)); runtime.refresh() }
            SettingToggle("Получать в фоне", "Поддерживать подключение после закрытия экрана приложения. В шторке будет тихое постоянное уведомление.", config.background, config.enabled) { runtime.preferences.update(config.copy(background = it)) }
            SettingToggle("Показывать текст сообщения", "Секретные чаты и запрет предпросмотра в Telegram всегда скрывают содержимое", config.previews, config.enabled) { runtime.preferences.update(config.copy(previews = it)) }
            HorizontalDivider(color = palette.divider)
            Text(if (allowed) "Разрешение Android: выдано" else "Уведомления запрещены в Android", color = if (allowed) palette.textPrimary else palette.warningText)
            Text(if (running) "Фоновое подключение работает" else "Фоновое подключение остановлено", color = palette.textSecondary)
            if (!allowed) Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !runtime.preferences.permissionAsked) { runtime.preferences.permissionAsked = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Разрешить уведомления") }
            TextButton(onClick = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Звук и системные настройки") }
            HorizontalDivider(color = palette.divider)
            Text(if (unrestricted) "Батарея: работа без оптимизации разрешена" else "Батарея: Android может задерживать получение сообщений во сне", color = palette.textSecondary)
            if (!unrestricted) OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                if (intent.resolveActivity(context.packageManager) != null) open(intent) else open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("Разрешить работу во сне") }
            Text("Постоянное подключение расходует больше батареи. После принудительной остановки приложения откройте BeyGram снова. Для Telegram по-прежнему нужен доступ к сети или работающий VPN/прокси.", color = palette.textSecondary, fontSize = 13.sp)
            error?.let { Text(it, color = palette.danger) }
        }
    }
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
