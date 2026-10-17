package com.beykus.beygram.domain

import androidx.annotation.StringRes

/** Текст для экрана: ресурс с аргументами или готовая строка, которую не нужно переводить. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val text: String) : UiText
}
