package com.beykus.beygram.domain.model

enum class VpnTransport { WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE }

data class VpnNetworkState(
    val internetAvailable: Boolean = false,
    val wifiAvailable: Boolean = false,
    val cellularAvailable: Boolean = false,
    val activeTransport: VpnTransport = VpnTransport.NONE,
    val changedAtMillis: Long = 0L
)

data class MihomoConnection(
    val name: String,
    val type: String,
    val delayMillis: Long? = null,
    val selected: Boolean = false,
    val available: Boolean = true
)

data class MihomoProbeResult(
    val connectionName: String,
    val telegramReachable: Boolean,
    val delayMillis: Long?,
    val checkedAtMillis: Long,
    val error: String? = null
)

data class VpnSettings(
    val subscriptionUrl: String = "",
    val autoSelectEnabled: Boolean = false,
    val connectionGroup: String = DEFAULT_CONNECTION_GROUP,
    val handoverDelayMillis: Long = DEFAULT_HANDOVER_DELAY_MILLIS
) {
    companion object {
        const val DEFAULT_CONNECTION_GROUP = "BeyGram Telegram"
        const val DEFAULT_HANDOVER_DELAY_MILLIS = 8_000L
        const val TELEGRAM_HEALTH_CHECK_URL = "https://web.telegram.org/"
    }
}
