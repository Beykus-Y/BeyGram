package com.beykus.beygram.data.vpn

import android.content.Context
import com.beykus.beygram.domain.model.VpnSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class VpnSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<VpnSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(transform: (VpnSettings) -> VpnSettings) {
        val next = transform(_settings.value)
        require(next.handoverDelayMillis in MIN_HANDOVER_DELAY_MS..MAX_HANDOVER_DELAY_MS)
        require(next.connectionGroup.isNotBlank())
        val saved = preferences.edit()
            .putString(KEY_SUBSCRIPTION_URL, next.subscriptionUrl.trim())
            .putBoolean(KEY_AUTO_SELECT, next.autoSelectEnabled)
            .putString(KEY_GROUP, next.connectionGroup.trim())
            .putLong(KEY_HANDOVER_DELAY, next.handoverDelayMillis)
            .commit()
        check(saved) { "Could not persist VPN settings" }
        _settings.value = next
    }

    private fun read() = VpnSettings(
        subscriptionUrl = preferences.getString(KEY_SUBSCRIPTION_URL, "").orEmpty(),
        autoSelectEnabled = preferences.getBoolean(KEY_AUTO_SELECT, false),
        connectionGroup = preferences.getString(KEY_GROUP, VpnSettings.DEFAULT_CONNECTION_GROUP)
            ?.takeIf(String::isNotBlank) ?: VpnSettings.DEFAULT_CONNECTION_GROUP,
        handoverDelayMillis = preferences.getLong(KEY_HANDOVER_DELAY, VpnSettings.DEFAULT_HANDOVER_DELAY_MILLIS)
            .coerceIn(MIN_HANDOVER_DELAY_MS, MAX_HANDOVER_DELAY_MS)
    )

    companion object {
        const val MIN_HANDOVER_DELAY_MS = 5_000L
        const val MAX_HANDOVER_DELAY_MS = 10_000L
        private const val PREFERENCES = "vpn-settings"
        private const val KEY_SUBSCRIPTION_URL = "subscription_url"
        private const val KEY_AUTO_SELECT = "auto_select"
        private const val KEY_GROUP = "connection_group"
        private const val KEY_HANDOVER_DELAY = "handover_delay_ms"
    }
}
