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

    fun isAuthorized(accountId: String): Boolean {
        if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) return false
        return preferences.getBoolean("authorized:$accountId", false)
    }

    fun isManuallyStopped(accountId: String): Boolean {
        if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) return false
        return preferences.getBoolean("manual_stop:$accountId", false)
    }

    /** The last explicit choice, even before the account has completed sign-in. */
    fun storedRoute(accountId: String): TdSecondaryRoutePreference? {
        if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) return null
        return tdParseSecondaryRoute(preferences.getString("route:$accountId", null))
    }

    fun setStoredRoute(accountId: String, choice: TdSecondaryRoutePreference) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        check(preferences.edit().putString("route:$accountId", choice.name).commit()) {
            "Unable to persist secondary connection route"
        }
    }

    fun confirmedRoute(accountId: String): TdSecondaryRoutePreference? {
        if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) return null
        return tdParseAuthorizedSecondaryRoute(
            authorized = isAuthorized(accountId) && !isManuallyStopped(accountId),
            stored = preferences.getString("route:$accountId", null)
        )
    }

    fun recordNativeReady(accountId: String, choice: TdSecondaryRoutePreference) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        check(preferences.edit()
            .putString("route:$accountId", choice.name)
            .putBoolean("authorized:$accountId", true)
            .putBoolean("manual_stop:$accountId", false)
            .commit()) { "Unable to persist secondary login restore policy" }
    }

    fun pauseAfterUserStop(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        check(preferences.edit().putBoolean("manual_stop:$accountId", true).commit()) {
            "Unable to persist manual secondary session stop"
        }
    }

    /** Persist incomplete deletion so startup can finish orphan cleanup.
     * A registered account is NEVER deleted by this recovery pass. */
    fun markDeletionPending(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID &&
            accountId.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        check(preferences.edit().putBoolean("delete_pending:$accountId", true).commit())
    }

    fun clearDeletionPending(accountId: String) {
        check(preferences.edit().remove("delete_pending:$accountId").commit())
    }

    fun deletionPending(accountId: String): Boolean =
        preferences.getBoolean("delete_pending:$accountId", false)

    fun pendingDeletionIds(): List<String> =
        preferences.all.keys.filter { it.startsWith("delete_pending:") }
            .map { it.removePrefix("delete_pending:") }
            .filter { it != TdAccountStorageLayout.LEGACY_ACCOUNT_ID &&
                it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && deletionPending(it) }

    fun forget(accountId: String) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        check(preferences.edit()
            .remove("authorized:$accountId")
            .remove("manual_stop:$accountId")
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
    return tdParseSecondaryRoute(stored)
}

internal fun tdParseSecondaryRoute(stored: String?): TdSecondaryRoutePreference? {
    if (stored == null) return null
    return TdSecondaryRoutePreference.entries.firstOrNull { it.name == stored }
}
