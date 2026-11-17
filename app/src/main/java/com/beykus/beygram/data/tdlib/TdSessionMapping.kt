package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ActiveSessions
import com.beykus.beygram.domain.model.DeviceSession
import com.beykus.beygram.domain.model.SessionDeviceKind
import org.drinkless.tdlib.TdApi

/**
 * Kind of device of a TDLib session, decided by the typed session type only. The free-text model is never searched.
 * A missing or unrecognised type becomes [SessionDeviceKind.Unknown], so a type added later shows a neutral icon.
 */
internal fun sessionDeviceKindOf(type: TdApi.SessionDeviceType?): SessionDeviceKind = when (type) {
    is TdApi.SessionDeviceTypeAndroid -> SessionDeviceKind.Android
    is TdApi.SessionDeviceTypeIphone -> SessionDeviceKind.IPhone
    is TdApi.SessionDeviceTypeIpad -> SessionDeviceKind.IPad
    is TdApi.SessionDeviceTypeMac -> SessionDeviceKind.Mac
    is TdApi.SessionDeviceTypeApple -> SessionDeviceKind.Apple
    is TdApi.SessionDeviceTypeWindows -> SessionDeviceKind.Windows
    is TdApi.SessionDeviceTypeLinux, is TdApi.SessionDeviceTypeUbuntu -> SessionDeviceKind.Linux
    is TdApi.SessionDeviceTypeXbox -> SessionDeviceKind.Xbox
    is TdApi.SessionDeviceTypeBrave, is TdApi.SessionDeviceTypeChrome, is TdApi.SessionDeviceTypeEdge,
    is TdApi.SessionDeviceTypeFirefox, is TdApi.SessionDeviceTypeOpera, is TdApi.SessionDeviceTypeSafari,
    is TdApi.SessionDeviceTypeVivaldi -> SessionDeviceKind.Browser
    else -> SessionDeviceKind.Unknown
}

/** One authorization as the app keeps it. Text is copied as TDLib sent it; blanks are shown as "not specified" by the UI. */
internal fun deviceSessionOf(session: TdApi.Session): DeviceSession = DeviceSession(
    id = session.id,
    current = session.isCurrent,
    kind = sessionDeviceKindOf(session.deviceType),
    device = session.deviceModel.orEmpty(),
    platform = session.platform.orEmpty(),
    systemVersion = session.systemVersion.orEmpty(),
    application = session.applicationName.orEmpty(),
    applicationVersion = session.applicationVersion.orEmpty(),
    officialApplication = session.isOfficialApplication,
    location = session.location.orEmpty(),
    ip = session.ipAddress.orEmpty(),
    logInDate = session.logInDate.toLong(),
    lastActive = session.lastActiveDate.toLong(),
    unconfirmed = session.isUnconfirmed,
    passwordPending = session.isPasswordPending,
    canAcceptCalls = session.canAcceptCalls,
    canAcceptSecretChats = session.canAcceptSecretChats
)

/** The sessions answer of TDLib: the list in the server's order, plus the inactivity limit. */
internal fun activeSessionsOf(sessions: TdApi.Sessions): ActiveSessions = ActiveSessions(
    sessions = sessions.sessions.orEmpty().map(::deviceSessionOf),
    inactiveSessionTtlDays = sessions.inactiveSessionTtlDays
)
