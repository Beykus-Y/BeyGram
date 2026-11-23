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

/** What submitPhoneNumber must do for the current TDLib phase (wire state from authWireState(), null = not known yet). */
internal enum class PhoneSubmitRoute { SEND, CANCEL_QR_THEN_SEND, WAIT_FOR_CLIENT, IGNORE }

internal fun phoneSubmitRoute(
    phase: String?,
    qrActive: Boolean,
    reopening: Boolean,
    authQueryInFlight: Boolean,
): PhoneSubmitRoute = when {
    phase == "ready" -> PhoneSubmitRoute.IGNORE
    reopening -> PhoneSubmitRoute.WAIT_FOR_CLIENT
    qrActive || phase == "wait_other_device" -> PhoneSubmitRoute.CANCEL_QR_THEN_SEND
    // TDLib accepts a phone number in wait_phone even while another query is pending.
    phase == "wait_phone" -> PhoneSubmitRoute.SEND
    // TDLib allows these only when no authentication query is pending.
    phase == "wait_code" || phase == "wait_password" ->
        if (authQueryInFlight) PhoneSubmitRoute.IGNORE else PhoneSubmitRoute.SEND
    else -> PhoneSubmitRoute.WAIT_FOR_CLIENT
}

/** State after "reconnect" on a timed-out login. Without a new auth update a known wait_phone must restore the form. */
internal fun reconnectAuthState(phase: String?, phonePending: Boolean): AuthState =
    if (phase == "wait_phone" && !phonePending) AuthState.WaitingForPhone else AuthState.Loading

/**
 * Tracks authentication queries (phone/code/password) by attempt. Only the result of the latest attempt
 * may change the login state; a late result of a superseded attempt or of a closed client is ignored.
 */
internal class AuthQueryTracker {
    private var latest = 0
    private var pending: Int? = null

    /** Starts a new attempt and returns its id; earlier attempts become superseded. */
    @Synchronized fun begin(): Int {
        latest++
        pending = latest
        return latest
    }

    /** True if [attempt] is the latest one; then it is no longer pending. A superseded attempt changes nothing. */
    @Synchronized fun finish(attempt: Int): Boolean {
        if (attempt != latest) return false
        pending = null
        return true
    }

    /** The client was recreated: every earlier attempt is superseded and nothing is pending. */
    @Synchronized fun reset() {
        latest++
        pending = null
    }

    val inFlight: Boolean
        @Synchronized get() = pending != null
}

/** A TDLib callback belongs to the current client only if it was created for the same client generation. */
internal fun isCurrentClientCallback(callbackGeneration: Int, currentGeneration: Int): Boolean =
    callbackGeneration == currentGeneration

/** State for the "Start" button: null keeps the current state. Only Idle may change. */
internal fun startAuthorizationState(current: AuthState, phase: String?): AuthState? = when {
    current != AuthState.Idle -> null
    phase == "wait_phone" -> AuthState.WaitingForPhone
    else -> AuthState.Loading
}

/** Stable, payload-free reason key for diagnostics. Never returns the raw TDLib message. */
internal fun authErrorReason(message: String?): String {
    val text = message.orEmpty()
    return when {
        text.contains("requestQrCodeAuthentication") || text.contains("QR code authentication") -> "qr_failed"
        text.contains("PHONE_NUMBER_INVALID") -> "phone_invalid"
        text.contains("PHONE_NUMBER_BANNED") -> "phone_banned"
        text.contains("PHONE_CODE_INVALID") -> "code_invalid"
        text.contains("PHONE_CODE_EXPIRED") -> "code_expired"
        text.contains("PASSWORD_HASH_INVALID") -> "password_invalid"
        text.contains("FLOOD_WAIT") || text.contains("Too Many Requests") -> "flood_wait"
        text.contains("unexpected", ignoreCase = true) -> "unexpected_state"
        text.contains("Request aborted", ignoreCase = true) || text.contains("closed", ignoreCase = true) -> "client_closed"
        else -> "other"
    }
}
