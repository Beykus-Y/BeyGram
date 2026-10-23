package com.beykus.beygram.data.tdlib

import android.content.Context

/**
 * Stores only opaque local account IDs and route selections. No phone,
 * code, password, TDLib auth key, SOCKS credentials or chat contents.
 *
 * A slot is eligible for automatic resume ONLY after native TDLib explicitly
 * reached AuthorizationStateReady. Merely reserving it never starts TDLib.
 */
internal class TdSecondaryRestoreStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "beygram_secondary_resume_v1", Context.MODE_PRIVATE
    )

    fun confirmedRoute(accountId: String): TdSecondaryRoutePreference? {
        if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) return null
        return tdParseAuthorizedSecondaryRoute(
            authorized = preferences.getBoolean("authorized:$accountId", false),
            stored = preferences.getString("route:$accountId", null)
        )
    }

    fun recordNativeReady(accountId: String, choice: TdSecondaryRoutePreference) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        check(preferences.edit()
            .putString("route:$accountId", choice.name)
            .putBoolean("authorized:$accountId", true)
            .commit()) { "Unable to persist secondary login restore policy" }
    }

    fun forget(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        check(preferences.edit()
            .remove("authorized:$accountId")
            .remove("route:$accountId")
            .commit()) { "Unable to remove secondary restore policy" }
    }

    fun candidates(registry: TdAccountRegistryState): List<Pair<String, TdSecondaryRoutePreference>> =
        registry.accountIds.mapNotNull { id -> confirmedRoute(id)?.let { id to it } }
}

internal fun tdParseAuthorizedSecondaryRoute(
    authorized: Boolean,
    stored: String?
): TdSecondaryRoutePreference? {
    if (!authorized || stored == null) return null
    return TdSecondaryRoutePreference.entries.firstOrNull { it.name == stored }
}
