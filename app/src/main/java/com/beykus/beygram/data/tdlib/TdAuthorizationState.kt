package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.QrLoginToken
import org.drinkless.tdlib.TdApi

/**
 * Stable authorization labels for diagnostics.
 *
 * These strings are deliberately independent of the payload: a QR authorization state contains the login token,
 * and that token must never reach a log, an analytics event or a problem report.
 */
internal fun TdApi.AuthorizationState.authWireState(): String = when (this) {
    is TdApi.AuthorizationStateWaitTdlibParameters -> "wait_parameters"
    is TdApi.AuthorizationStateWaitPhoneNumber -> "wait_phone"
    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> "wait_other_device"
    is TdApi.AuthorizationStateWaitCode -> "wait_code"
    is TdApi.AuthorizationStateWaitPassword -> "wait_password"
    is TdApi.AuthorizationStateReady -> "ready"
    is TdApi.AuthorizationStateLoggingOut -> "logging_out"
    is TdApi.AuthorizationStateClosed -> "closed"
    else -> "other"
}

internal fun TdApi.AuthorizationState.authLogLabel(): String = when (this) {
    is TdApi.AuthorizationStateWaitTdlibParameters -> "подготовка"
    is TdApi.AuthorizationStateWaitPhoneNumber -> "ожидание номера"
    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> "ожидание подтверждения"
    is TdApi.AuthorizationStateWaitCode -> "ожидание кода"
    is TdApi.AuthorizationStateWaitPassword -> "ожидание пароля"
    is TdApi.AuthorizationStateReady -> "вход выполнен"
    is TdApi.AuthorizationStateLoggingOut -> "выход"
    is TdApi.AuthorizationStateClosed -> "закрыто"
    else -> "другой этап"
}

/** Maps a TDLib QR link to the UI state. Invalid links are shown as a recoverable error, never as a raw crash. */
internal fun qrAuthState(rawLink: String?): AuthState {
    val token = QrLoginToken.parse(rawLink)
        ?: return AuthState.Error(UiText.Res(R.string.err_auth_qr_link))
    return AuthState.WaitingForQr(token.link)
}
