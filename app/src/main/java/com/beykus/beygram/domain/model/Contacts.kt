package com.beykus.beygram.domain.model

/**
 * Контакт из адресной книги Telegram. [subtitle] — @имя пользователя, а если его нет, номер телефона.
 * [firstName], [lastName] и [phone] — исходные поля TDLib для диалога редактирования; [title] остаётся
 * готовой подписью строки и учитывает уже сохранённое на сервере имя.
 */
data class ContactPreview(
    val userId: Long,
    val title: String,
    val subtitle: String,
    val photo: AvatarPhoto?,
    val avatarColorArgb: Long,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    /** Secret chats require a regular user; bots are never eligible. */
    val isBot: Boolean = false
) {
    val initials: String get() = initialsOf(title)
}

/**
 * Итог звонка. [Answered] — разговор состоялся, [Missed] — входящий пропущенный,
 * [Declined] — отклонён, [NoAnswer] — исходящий без ответа или отменённый.
 */
enum class CallStatus { Answered, Missed, Declined, NoAnswer }

/** Звонок из истории. Собеседник и аватар берутся из чата, в котором звонок был. */
data class CallRecord(
    val messageId: Long,
    val chatId: Long,
    val title: String,
    val photo: AvatarPhoto?,
    val avatarColorArgb: Long,
    val isVideo: Boolean,
    val isOutgoing: Boolean,
    val status: CallStatus,
    val durationSeconds: Int,
    val dateSeconds: Long
) {
    val initials: String get() = initialsOf(title)
}

/** Страница истории звонков. Пустой [nextOffset] означает, что звонков больше нет. */
data class CallHistoryPage(val calls: List<CallRecord>, val nextOffset: String)
