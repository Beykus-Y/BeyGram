package com.beykus.beygram.data.yandex

import android.content.Context
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.shareConnectionDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val YANDEX_DIAGNOSTICS_PREFIX = "beygram-yandex-status-"
private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

/**
 * Writes the redacted subscription status to the app cache and opens the system share sheet, through the same
 * path the connection diagnostics use. Returns an error text for the screen, or null on success.
 */
internal suspend fun shareYandexSubscriptionDiagnostics(context: Context, summary: String): String? = try {
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val name = YANDEX_DIAGNOSTICS_PREFIX + STAMP.format(Instant.ofEpochMilli(now)) + ".txt"
        File(directory, name).apply {
            writeText(
                buildString {
                    appendLine("BeyGram ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})")
                    appendLine("Yandex Music subscription status, service response key names and scalar values only.")
                    appendLine(summary)
                },
                Charsets.UTF_8,
            )
        }
    }
    withContext(Dispatchers.Main) { shareConnectionDiagnostics(context, file) }
    null
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    context.getString(R.string.diagnostics_prepare_failed, failure.javaClass.simpleName)
}
