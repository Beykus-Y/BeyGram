package com.beykus.beygram.data.update

import android.content.Context

/**
 * Bookkeeping of the unattended update check, per package and channel. Deliberately separate from
 * [UpdateStateStore] (the user's download/install state) and from [UpdateCheckStore] (the manual-check summary,
 * which no background check is allowed to rewrite).
 *
 * Everything here is advisory: a lost or damaged value only means one extra check, never a wrong offer.
 */
internal class UpdateNoticeStore(context: Context) : UpdateNoticeStorage {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = "${context.packageName}:${channelSuffix(context)}"

    override fun attempts(): UpdateAutoCheckPolicy.Attempts = UpdateAutoCheckPolicy.Attempts(
        lastAttemptAt = long("$KEY_LAST_ATTEMPT:$scope"),
        lastSuccessAt = long("$KEY_LAST_SUCCESS:$scope"),
        consecutiveFailures = prefs.getInt("$KEY_FAILURES:$scope", 0).coerceAtLeast(0),
        rateLimitedUntil = long("$KEY_RATE_LIMITED_UNTIL:$scope"),
    )

    /** Written before the request goes out, so a crash inside the call cannot turn into a retry loop. */
    override fun recordAttempt(now: Long) {
        prefs.edit().putLong("$KEY_LAST_ATTEMPT:$scope", now).apply()
    }

    /**
     * The attempt was skipped because the user was already working with the updater: it never reached the network, so
     * it must not delay the next unattended trigger. Restores the value that was stored before the attempt.
     */
    override fun undoAttempt(previousLastAttemptAt: Long?) {
        val editor = prefs.edit()
        if (previousLastAttemptAt == null) {
            editor.remove("$KEY_LAST_ATTEMPT:$scope")
        } else {
            editor.putLong("$KEY_LAST_ATTEMPT:$scope", previousLastAttemptAt)
        }
        editor.apply()
    }

    override fun recordSuccess(now: Long) {
        prefs.edit()
            .putLong("$KEY_LAST_SUCCESS:$scope", now)
            .putInt("$KEY_FAILURES:$scope", 0)
            .remove("$KEY_RATE_LIMITED_UNTIL:$scope")
            .apply()
    }

    override fun recordFailure(now: Long, rateLimited: Boolean) {
        val failures = attempts().consecutiveFailures + 1
        val editor = prefs.edit().putInt("$KEY_FAILURES:$scope", failures)
        if (rateLimited) editor.putLong("$KEY_RATE_LIMITED_UNTIL:$scope", UpdateAutoCheckPolicy.afterRateLimit(now))
        editor.apply()
    }

    /** A check that was skipped because the user was already working with the updater must not count as a failure. */
    override fun notifiedVersionCode(): Long? = long("$KEY_NOTIFIED_CODE:$scope")

    override fun saveNotifiedVersionCode(code: Long?) = edit {
        if (code == null) remove("$KEY_NOTIFIED_CODE:$scope") else putLong("$KEY_NOTIFIED_CODE:$scope", code)
    }

    override fun snoozedVersionCode(): Long? = long("$KEY_SNOOZED_CODE:$scope")

    override fun snoozedUntil(): Long? = long("$KEY_SNOOZED_UNTIL:$scope")

    /** "Позже" hides the in-app banner for this build only, and only until the window ends. */
    override fun snoozeBanner(code: Long, until: Long) = edit {
        putLong("$KEY_SNOOZED_CODE:$scope", code)
        putLong("$KEY_SNOOZED_UNTIL:$scope", until)
    }

    override fun clearSnooze() = edit {
        remove("$KEY_SNOOZED_CODE:$scope")
        remove("$KEY_SNOOZED_UNTIL:$scope")
    }

    /** Unattended checks are on by default. The manual check on the Updates screen never consults this value. */
    override fun autoCheckEnabled(): Boolean = prefs.getBoolean("$KEY_AUTO_CHECK:$scope", true)

    override fun setAutoCheckEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("$KEY_AUTO_CHECK:$scope", enabled).apply()

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    private fun long(key: String): Long? =
        if (prefs.contains(key)) prefs.getLong(key, 0L).takeIf { it > 0L } else null

    companion object {
        private const val PREFS = "beygram_update_notice"
        private const val KEY_LAST_ATTEMPT = "lastAttemptAt"
        private const val KEY_LAST_SUCCESS = "lastSuccessAt"
        private const val KEY_FAILURES = "consecutiveFailures"
        private const val KEY_RATE_LIMITED_UNTIL = "rateLimitedUntil"
        private const val KEY_NOTIFIED_CODE = "notifiedVersionCode"
        private const val KEY_NOTIFIED_CHANNEL = "notifiedChannel"
        private const val KEY_SNOOZED_CODE = "snoozedVersionCode"
        private const val KEY_SNOOZED_UNTIL = "snoozedUntil"
        private const val KEY_AUTO_CHECK = "autoCheckEnabled"

        /** How long "Позже" hides the banner. A different build appears immediately. */
        const val BANNER_SNOOZE_MS = 24L * 60 * 60 * 1000
    }
}

internal fun channelSuffix(context: Context): String =
    if (updateChannelFor(context.packageName) == UpdateChannel.DevBeta) "dev" else "stable"