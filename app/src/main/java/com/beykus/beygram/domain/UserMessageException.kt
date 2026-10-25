package com.beykus.beygram.domain

/** Ошибка, текст которой показывается пользователю; сам текст задан ключом ресурса, а не строкой. */
open class UserMessageException(val text: UiText, cause: Throwable? = null) : RuntimeException(text.toString(), cause)
