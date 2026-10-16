package com.beykus.beygram.domain.model

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

    /** Пользователь успешно вошёл. */
    data object Ready : AuthState

    /** TDLib не ответила на сетевой запрос за отведённое время. */
    data object ConnectionTimeout : AuthState

    /** Что-то пошло не так; [message] — текст ошибки от TDLib. */
    data class Error(val message: String) : AuthState
}
