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
        /** The existing login always keeps its database; registry selection is not
         * consumed until account-scoped TDLib repositories exist. */
        fun from(context: Context): TdLibConfig =
            forAccount(context, TdAccountStorageLayout.LEGACY_ACCOUNT_ID)

        /** The storage contract for a future TDLib client owned by [accountId].
         * Caller must first register this account and acquire its database lease. */
        fun forAccount(context: Context, accountId: String): TdLibConfig {
            val directories = TdAccountStorageLayout(context.filesDir).forAccount(accountId)

            return TdLibConfig(
                apiId = BuildConfig.TELEGRAM_API_ID,
                apiHash = BuildConfig.TELEGRAM_API_HASH,
                databaseDirectory = directories.database.absolutePath,
                filesDirectory = directories.files.absolutePath,
                systemLanguageCode = Locale.getDefault().toLanguageTag(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                applicationVersion = BuildConfig.VERSION_NAME
            )
        }
    }
}
