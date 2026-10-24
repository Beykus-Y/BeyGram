package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

sealed interface AuthState {
    /** Начальное состояние — TDLib ещё не инициализирован. */
    data object Idle : AuthState

    /** TDLib инициализируется / ждём ответа на запрос. */
    data object Loading : AuthState

    /** TDLib готов, ждёт ввода номера телефона. */
    data object WaitingForPhone : AuthState

    /** Код отправлен на [phone]. */
    data class WaitingForCode(val phone: String) : AuthState

    /** Включена двухфакторная аутентификация; [hint] — подсказка к паролю. */
    data class WaitingForPassword(val hint: String) : AuthState

    /** Ожидаем подтверждения входа по QR-коду; [link] — `tg://login?token=...` от TDLib. */
    data class WaitingForQr(val link: String) : AuthState {
        // The link contains a login secret and must never reach a log through a generic state dump.
        override fun toString(): String = "WaitingForQr(***)"
    }

    /** Пользователь успешно вошёл. */
    data object Ready : AuthState

    /** TDLib не ответила на сетевой запрос за отведённое время. */
    data object ConnectionTimeout : AuthState

    /** Что-то пошло не так; [text] — текст ошибки для показа. */
    data class Error(val text: UiText) : AuthState
}
