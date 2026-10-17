package com.beykus.beygram.domain.model

/** Как сейчас уходит соединение с Telegram. */
sealed interface TelegramRoute {
    /** Напрямую, без прокси. */
    data object Direct : TelegramRoute

    /** Через прокси, сохранённый вручную. */
    data class Proxy(val kind: TelegramProxyKind, val server: String, val port: Int) : TelegramRoute

    /** Через локальный прокси подписки серверов. */
    data object Subscription : TelegramRoute
}
