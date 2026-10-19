package com.beykus.beygram.domain.model

/** An authorized Telegram device returned by TDLib, not Android-local processes. */
data class DeviceSession(
    val id: Long,
    val current: Boolean,
    val device: String,
    val application: String,
    val location: String,
    val ip: String,
    val lastActive: Long
)
