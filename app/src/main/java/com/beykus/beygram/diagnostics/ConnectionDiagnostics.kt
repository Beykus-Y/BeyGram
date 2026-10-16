package com.beykus.beygram.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.FileProvider
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DIAGNOSTICS_DIRECTORY = "diagnostics"
private const val STALE_REPORT_AGE_MILLIS = 10 * 60 * 1000L

/**
 * Собирает текстовый отчёт о подключении и кладёт его в кэш приложения.
 * Ничего не отправляется по сети: файл передаётся только системному «Поделиться».
 */
suspend fun createConnectionDiagnostics(context: Context, connection: TelegramConnectionState): File =
    withContext(Dispatchers.IO) {
        val report = connectionDiagnosticsReport(context, connection)
        writeDiagnosticsFile(context, report, System.currentTimeMillis())
    }

/** Copies the same redacted report used by file sharing. */
suspend fun copyConnectionDiagnosticsToClipboardOrError(
    context: Context,
    connection: TelegramConnectionState
): String? = try {
    val report = withContext(Dispatchers.IO) { connectionDiagnosticsReport(context, connection) }
    withContext(Dispatchers.Main) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: error("Clipboard service is unavailable")
        clipboard.setPrimaryClip(ClipData.newPlainText("BeyGram diagnostics", report))
    }
    null
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    "Не удалось скопировать логи (${failure.javaClass.simpleName})"
}

/**
 * Собирает отчёт и открывает «Поделиться». Возвращает текст ошибки для экрана или null.
 * Используется и в настройках, и на экране входа, где аккаунта ещё нет.
 */
suspend fun shareConnectionDiagnosticsOrError(context: Context, connection: TelegramConnectionState): String? =
    try {
        shareConnectionDiagnostics(context, createConnectionDiagnostics(context, connection))
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        "Не удалось подготовить логи (${failure.javaClass.simpleName})"
    }

/** Открывает системное «Поделиться» для уже записанного отчёта. */
fun shareConnectionDiagnostics(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "BeyGram: диагностика подключения")
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Поделиться логами"))
}

internal fun TelegramConnectionState.diagnosticsLabel(): String = when (this) {
    TelegramConnectionState.Connected -> "подключено"
    TelegramConnectionState.Connecting -> "подключаемся"
    TelegramConnectionState.Updating -> "восстанавливаем соединение"
    TelegramConnectionState.WaitingForNetwork -> "ждём сеть"
    TelegramConnectionState.Unknown -> "неизвестно"
}

internal fun buildDiagnosticsReport(
    context: Context,
    connection: TelegramConnectionState,
    events: List<String>,
    nowMillis: Long = System.currentTimeMillis()
): String = buildString {
    appendLine("BeyGram: диагностика подключения")
    appendLine("Создано: ${REPORT_TIME.format(Instant.ofEpochMilli(nowMillis))}")
    appendLine()
    appendLine("[Приложение]")
    appendLine("applicationId: ${BuildConfig.APPLICATION_ID}")
    appendLine("версия: ${BuildConfig.VERSION_NAME}, сборка ${BuildConfig.BUILD_TYPE}, вариант ${BuildConfig.FLAVOR}")
    appendLine()
    appendLine("[Устройство]")
    appendLine("производитель: ${Build.MANUFACTURER}")
    appendLine("модель: ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    appendLine("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "неизвестно"}")
    val ignoresBattery = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
    appendLine("работа без ограничений батареи: ${yesNo(ignoresBattery)}")
    appendLine()
    appendLine("[Соединение]")
    appendLine("состояние TDLib сейчас: ${connection.diagnosticsLabel()} (${connection.name})")
    appendLine(networkSummary(context))
    appendLine()
    appendLine("[Недавние события, до ${ConnectionEventLog.DEFAULT_CAPACITY}, старые сверху]")
    if (events.isEmpty()) appendLine("нет событий с момента запуска приложения")
    events.forEach { appendLine(it) }
}

private fun networkSummary(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java)
        ?: return "сеть: сервис ConnectivityManager недоступен"
    val network = manager.activeNetwork ?: return "сеть сейчас: нет активной сети"
    val capabilities = manager.getNetworkCapabilities(network)
        ?: return "сеть сейчас: нет данных о сети"
    return buildString {
        append("сеть сейчас: интернет=${yesNo(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))}")
        append(", подтверждена=${yesNo(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))}")
        append(", Wi-Fi=${yesNo(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))}")
        append(", мобильная=${yesNo(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))}")
        append(", VPN=${yesNo(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))}")
        append(", без лимита=${yesNo(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))}")
    }
}

private fun writeDiagnosticsFile(context: Context, report: String, nowMillis: Long): File {
    val directory = File(context.cacheDir, DIAGNOSTICS_DIRECTORY).apply { mkdirs() }
    // Удаляем только старые отчёты: получатель прошлого «Поделиться» мог ещё не дочитать файл.
    directory.listFiles()
        ?.filter { nowMillis - it.lastModified() > STALE_REPORT_AGE_MILLIS }
        ?.forEach { it.delete() }
    val stamp = FILE_TIME.format(Instant.ofEpochMilli(nowMillis))
    return File(directory, "beygram-diagnostics-$stamp.txt").apply { writeText(report, Charsets.UTF_8) }
}

private fun connectionDiagnosticsReport(context: Context, connection: TelegramConnectionState): String =
    DiagnosticsRedactor.redact(buildDiagnosticsReport(context, connection, ConnectionEventLog.shared.snapshot()))

private fun yesNo(value: Boolean) = if (value) "да" else "нет"

private val REPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT).withZone(ZoneId.systemDefault())
private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault())
