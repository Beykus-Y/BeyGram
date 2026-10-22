package com.beykus.beygram.analytics

import android.os.Build
import com.beykus.beygram.BuildConfig

/**
 * Тело `POST /api/v1/analytics/sync` без поля revision. Строка сигнатуры по нему решает,
 * изменилось ли состояние. Строки строятся вручную: все значения проверяются регулярками и не требуют экранирования.
 */
internal object AnalyticsPayload {
    private val VERSION = Regex("[A-Za-z0-9._+-]{1,40}")
    private val ANDROID = Regex("[A-Za-z0-9 ._-]{1,40}")
    private val MODEL = Regex("[A-Za-z0-9 ._()/+-]{1,80}")
    private const val MAX_ACCOUNTS = 20

    fun signature(installationId: String, accountIds: List<Long>): String {
        val accounts = accountIds.filter { it > 0 }.distinct().take(MAX_ACCOUNTS)
        val fields = mutableListOf(
            "\"installation_id\":\"$installationId\"",
            "\"app_version\":\"${BuildConfig.VERSION_NAME.takeIf { VERSION.matches(it) } ?: "unknown"}\"",
            "\"app_version_code\":${BuildConfig.VERSION_CODE}",
            "\"release_channel\":\"${if (BuildConfig.FLAVOR == "dev") "dev" else "stable"}\"",
            "\"android_version\":\"${Build.VERSION.RELEASE.takeIf { ANDROID.matches(it) } ?: "unknown"}\"",
        )
        Build.MODEL?.trim()?.takeIf { MODEL.matches(it) }?.let { fields += "\"device_model\":\"$it\"" }
        fields += "\"account_count\":${accounts.size}"
        if (accounts.isNotEmpty()) fields += "\"telegram_user_ids\":[${accounts.joinToString(",")}]"
        return "{" + fields.joinToString(",") + "}"
    }

    /** Тело с ревизией: сигнатура без закрывающей скобки плюс `revision`. */
    fun body(signature: String, revision: Long): String =
        signature.dropLast(1) + ",\"revision\":$revision}"
}
