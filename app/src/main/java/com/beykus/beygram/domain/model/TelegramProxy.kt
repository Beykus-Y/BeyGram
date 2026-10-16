package com.beykus.beygram.domain.model

enum class TelegramProxyKind { MTPROTO, SOCKS5, HTTP }

/** Данные, которые пользователь вводит на экране «Подключение к Telegram». */
data class TelegramProxyDraft(
    val kind: TelegramProxyKind,
    val server: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val secret: String = ""
)

/** Прокси, сохранённый в TDLib. Служебный адрес Mihomo сюда не входит. */
data class SavedTelegramProxy(
    val id: Int,
    val kind: TelegramProxyKind,
    val server: String,
    val port: Int,
    val enabled: Boolean
)
