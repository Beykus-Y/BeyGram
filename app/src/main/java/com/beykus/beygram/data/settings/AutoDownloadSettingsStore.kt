package com.beykus.beygram.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.beykus.beygram.data.media.AutoDownloadNetworkPolicy
import com.beykus.beygram.data.media.AutoDownloadRule
import com.beykus.beygram.data.media.AutoDownloadSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Auto-download preferences that the settings screen writes and the download policy reads. */
interface AutoDownloadSettingsSource {
    val settings: StateFlow<AutoDownloadSettings>

    /** Saves [transform]; on a failed write the previous settings stay visible and in use. */
    fun update(transform: (AutoDownloadSettings) -> AutoDownloadSettings): Result<Unit>
}

/**
 * Auto-download settings kept per Telegram account. They live in ordinary app preferences, not in the
 * TDLib database, so the UI and the download gate always read the same values. No credentials are stored.
 */
class AutoDownloadSettingsStore private constructor(context: Context, accountId: String) : AutoDownloadSettingsSource {
    private val preferences = context.getSharedPreferences("$PREFERENCES_PREFIX$accountId", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    override val settings: StateFlow<AutoDownloadSettings> = _settings.asStateFlow()

    @Synchronized
    override fun update(transform: (AutoDownloadSettings) -> AutoDownloadSettings): Result<Unit> {
        val next = transform(_settings.value)
        if (!persist(next)) {
            return Result.failure(IllegalStateException("Could not save auto-download settings"))
        }
        _settings.value = next
        return Result.success(Unit)
    }

    private fun persist(settings: AutoDownloadSettings): Boolean = preferences.edit()
        .putPolicy(PREFIX_WIFI, settings.wifi)
        .putPolicy(PREFIX_MOBILE, settings.mobile)
        .putPolicy(PREFIX_ROAMING, settings.roaming)
        .putBoolean(KEY_DATA_SAVER, settings.dataSaver)
        .commit()

    private fun SharedPreferences.Editor.putPolicy(prefix: String, policy: AutoDownloadNetworkPolicy): SharedPreferences.Editor {
        putRule("${prefix}_photos", policy.photos)
        putRule("${prefix}_videos", policy.videos)
        putRule("${prefix}_files", policy.files)
        putRule("${prefix}_music", policy.music)
        return this
    }

    private fun SharedPreferences.Editor.putRule(prefix: String, rule: AutoDownloadRule) {
        putBoolean("${prefix}_enabled", rule.enabled)
        putLong("${prefix}_max_bytes", rule.maxBytes)
    }

    private fun readSettings(): AutoDownloadSettings {
        val defaults = AutoDownloadSettings.defaults()
        return AutoDownloadSettings(
            wifi = readPolicy(PREFIX_WIFI, defaults.wifi),
            mobile = readPolicy(PREFIX_MOBILE, defaults.mobile),
            roaming = readPolicy(PREFIX_ROAMING, defaults.roaming),
            dataSaver = preferences.getBoolean(KEY_DATA_SAVER, defaults.dataSaver)
        )
    }

    private fun readPolicy(prefix: String, defaults: AutoDownloadNetworkPolicy): AutoDownloadNetworkPolicy =
        AutoDownloadNetworkPolicy(
            photos = readRule("${prefix}_photos", defaults.photos),
            videos = readRule("${prefix}_videos", defaults.videos),
            files = readRule("${prefix}_files", defaults.files),
            music = readRule("${prefix}_music", defaults.music)
        )

    private fun readRule(prefix: String, defaults: AutoDownloadRule): AutoDownloadRule {
        val bytes = preferences.getLong("${prefix}_max_bytes", defaults.maxBytes)
        return AutoDownloadRule(
            enabled = preferences.getBoolean("${prefix}_enabled", defaults.enabled),
            // A corrupt negative value must not turn into an unlimited rule.
            maxBytes = bytes.takeIf { it > 0L } ?: defaults.maxBytes
        )
    }

    companion object {
        private const val PREFERENCES_PREFIX = "auto-download-settings-"
        private const val KEY_DATA_SAVER = "data_saver"
        private const val PREFIX_WIFI = "wifi"
        private const val PREFIX_MOBILE = "mobile"
        private const val PREFIX_ROAMING = "roaming"
        private const val LEGACY_ACCOUNT_ID = "legacy"

        private val instances = java.util.concurrent.ConcurrentHashMap<String, AutoDownloadSettingsStore>()

        /**
         * Settings are kept per Telegram account: TDLib auto-download settings are account-scoped,
         * and switching an account must not silently apply another account's limits.
         */
        fun getInstance(context: Context, accountId: String = LEGACY_ACCOUNT_ID): AutoDownloadSettingsStore {
            val id = accountId.ifBlank { LEGACY_ACCOUNT_ID }
            return synchronized(instances) {
                instances[id] ?: AutoDownloadSettingsStore(context.applicationContext, id).also { instances[id] = it }
            }
        }
    }
}
