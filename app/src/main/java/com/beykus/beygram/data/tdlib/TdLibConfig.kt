package com.beykus.beygram.data.tdlib

import android.content.Context
import android.os.Build
import com.beykus.beygram.BuildConfig
import java.util.Locale

data class TdLibConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDirectory: String,
    val filesDirectory: String,
    val systemLanguageCode: String,
    val deviceModel: String,
    val applicationVersion: String,
    val useMessageDatabase: Boolean = true,
    val useSecretChats: Boolean = true
) {
    val hasApiCredentials: Boolean
        get() = apiId != 0 && apiHash.isNotBlank()

    companion object {
        fun from(context: Context): TdLibConfig {
            val tdlibRoot = context.filesDir.resolve("tdlib")

            return TdLibConfig(
                apiId = BuildConfig.TELEGRAM_API_ID,
                apiHash = BuildConfig.TELEGRAM_API_HASH,
                databaseDirectory = tdlibRoot.resolve("database").absolutePath,
                filesDirectory = tdlibRoot.resolve("files").absolutePath,
                systemLanguageCode = Locale.getDefault().toLanguageTag(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                applicationVersion = BuildConfig.VERSION_NAME
            )
        }
    }
}
