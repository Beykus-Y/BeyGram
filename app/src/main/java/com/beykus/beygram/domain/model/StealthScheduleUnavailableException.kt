package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException

/**
 * Stealth mode sends through Telegram's scheduled messages, and this send cannot be scheduled: the chat or the content
 * does not support it, or the server refused the date. Nothing was sent. The caller may send again with
 * [MessageSendOptions.sendNowConsent] once the user explicitly agrees to an ordinary send.
 */
class StealthScheduleUnavailableException(text: UiText, cause: Throwable? = null) : UserMessageException(text, cause)
