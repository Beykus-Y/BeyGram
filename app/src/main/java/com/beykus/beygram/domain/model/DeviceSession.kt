package com.beykus.beygram.domain.model

/**
 * An authorized Telegram device returned by TDLib, not Android-local processes.
 *
 * Values are the server's own. An empty string or 0 means the server sent nothing, and the UI says so instead of
 * guessing. [location] and [ip] describe the authorization, not where the device is now.
 */
data class DeviceSession(
    val id: Long,
    val current: Boolean,
    /** Kind of device, from TDLib's typed session type. Used for the icon. */
    val kind: SessionDeviceKind,
    /** Device model, for example "Pixel 8". */
    val device: String,
    /** Operating system name, for example "Android" or "Windows". */
    val platform: String,
    /** Operating system version, for example "14". */
    val systemVersion: String,
    /** Name of the client application. */
    val application: String,
    val applicationVersion: String,
    /** True for the official client of the service, false for a third-party client. */
    val officialApplication: Boolean,
    /** Location recorded when the authorization was created. */
    val location: String,
    /** IP address recorded when the authorization was created. */
    val ip: String,
    /** Unix time in seconds when the authorization was created. 0 when unknown. */
    val logInDate: Long,
    /** Unix time in seconds of the last activity. 0 when unknown. */
    val lastActive: Long,
    /** A login from a new device that the user has not confirmed yet. */
    val unconfirmed: Boolean,
    /** The sign-in still waits for the two-step verification password on this device. */
    val passwordPending: Boolean,
    /** Whether this session accepts incoming calls. */
    val canAcceptCalls: Boolean,
    /** Whether this session accepts secret chats. */
    val canAcceptSecretChats: Boolean
)

/** Kind of device behind a session. Every TDLib device type maps to one of these; unknown types map to [Unknown]. */
enum class SessionDeviceKind { Android, IPhone, IPad, Mac, Apple, Windows, Linux, Xbox, Browser, Unknown }

/** The sessions of the selected account and the server's inactivity limit, read from one TDLib response. */
data class ActiveSessions(
    val sessions: List<DeviceSession>,
    /** Days without activity after which Telegram ends a session by itself. TDLib accepts 1 to 366. */
    val inactiveSessionTtlDays: Int
)
