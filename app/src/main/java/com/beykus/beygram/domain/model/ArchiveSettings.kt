package com.beykus.beygram.domain.model

/** Настройки архива. По умолчанию архивные чаты приходят без звука и не возвращаются сами. */
data class ArchiveSettings(val returnOnNewMessage: Boolean = false, val archiveSound: Boolean = false)
