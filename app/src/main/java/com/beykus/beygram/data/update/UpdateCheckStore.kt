package com.beykus.beygram.data.update

import android.content.Context

/*
 * The outcome of the last manual update check, for the Settings summary. It is written only by a check the user starts
 * on the update screen; no background check writes it and nothing here decides an update. The state of a running
 * check or a downloaded APK is not stored here: the shared update coordinator will own that state.
 */

private const val PREFS_NAME = "beygram_update_check"
private const val KEY_PACKAGE = "package"
private const val KEY_INSTALLED_CODE = "installedCode"
private const val KEY_CHANNEL = "channel"
private const val KEY_CHECKED_AT = "checkedAt"
private const val KEY_OUTCOME = "outcome"
private const val KEY_AVAILABLE_LABEL = "availableLabel"
private const val KEY_AVAILABLE_CODE = "availableCode"
private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
private const val MAX_CLOCK_SKEW_MS = 5L * 60 * 1000

enum class UpdateCheckOutcome { Current, Available, Failed }

/**
 * [availableLabel] is a validated version name (Stable) or a `dev-N` tag (DEV), never raw server text.
 * [channel] is "stable" or "dev".
 */
data class UpdateCheckRecord(
    val packageName: String,
    val installedCode: Long,
    val channel: String,
    val checkedAt: Long,
    val outcome: UpdateCheckOutcome,
    val availableLabel: String? = null,
    val availableCode: Long? = null,
)

sealed interface UpdateCheckView {
    /** Nothing was recorded for this app. */
    data object None : UpdateCheckView
    /** The record no longer describes the installed build; show its date only and do not claim it is current. */
    data class Stale(val record: UpdateCheckRecord) : UpdateCheckView
    data class Fresh(val record: UpdateCheckRecord) : UpdateCheckView
}

object UpdateCheckStore {
    /** Keeps the last record. A failed write or read never affects the update flow. */
    fun save(context: Context, record: UpdateCheckRecord) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_PACKAGE, record.packageName)
                .putLong(KEY_INSTALLED_CODE, record.installedCode)
                .putString(KEY_CHANNEL, record.channel)
                .putLong(KEY_CHECKED_AT, record.checkedAt)
                .putString(KEY_OUTCOME, record.outcome.name)
                .putString(KEY_AVAILABLE_LABEL, record.availableLabel)
                .putLong(KEY_AVAILABLE_CODE, record.availableCode ?: -1L)
                .apply()
        }
    }

    fun load(context: Context): UpdateCheckRecord? = runCatching { loadOrNull(context) }.getOrNull()

    private fun loadOrNull(context: Context): UpdateCheckRecord? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val outcome = prefs.getString(KEY_OUTCOME, null)?.let { name ->
            UpdateCheckOutcome.entries.firstOrNull { it.name == name }
        } ?: return null
        return UpdateCheckRecord(
            packageName = prefs.getString(KEY_PACKAGE, null) ?: return null,
            installedCode = prefs.getLong(KEY_INSTALLED_CODE, -1L),
            channel = prefs.getString(KEY_CHANNEL, null) ?: return null,
            checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L),
            outcome = outcome,
            availableLabel = prefs.getString(KEY_AVAILABLE_LABEL, null),
            availableCode = prefs.getLong(KEY_AVAILABLE_CODE, -1L).takeIf { it > 0L },
        )
    }

    /**
     * Pure decision for the Settings summary. A record is stale when another build is installed, when the offered
     * build is already installed, when it is older than seven days, or when it lies in the future (a clock change).
     */
    fun evaluate(record: UpdateCheckRecord?, packageName: String, installedCode: Long, now: Long): UpdateCheckView {
        if (record == null || record.packageName != packageName) return UpdateCheckView.None
        val availableCode = record.availableCode
        val stale = record.installedCode != installedCode ||
            (availableCode != null && availableCode <= installedCode) ||
            now - record.checkedAt > MAX_AGE_MS ||
            record.checkedAt > now + MAX_CLOCK_SKEW_MS
        return if (stale) UpdateCheckView.Stale(record) else UpdateCheckView.Fresh(record)
    }
}
