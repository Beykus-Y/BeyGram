package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ContactPreview
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Адресная книга Telegram: список контактов и добавление по номеру. Открытие чата делает репозиторий. */
internal class TdContacts(private val client: () -> Client, private val profiles: TdProfiles) {
    private val requests = TdRequests(client)

    suspend fun load(): List<ContactPreview> {
        val ids = requests(TdApi.GetContacts()).userIds
        return ids.map { id -> requests(TdApi.GetUser(id)).toContact() }.sortedBy { it.title.lowercase() }
    }

    /** Если номера нет в Telegram, TDLib возвращает идентификатор 0 вместо пользователя. */
    suspend fun add(firstName: String, lastName: String, phone: String) {
        val note = TdApi.FormattedText("", emptyArray<TdApi.TextEntity>())
        val imported = requests(TdApi.ImportContacts(arrayOf(TdApi.ImportedContact(phone, firstName, lastName, note))))
        if (imported.userIds.none { it != 0L }) throw IllegalStateException("Такого номера нет в Telegram")
    }

    private fun TdApi.User.toContact(): ContactPreview = ContactPreview(
        userId = id,
        title = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Пользователь" },
        subtitle = subtitle(),
        photo = profilePhoto?.let { profiles.photo(it.small, it.big) },
        avatarColorArgb = id.stableColor()
    )

    private fun TdApi.User.subtitle(): String {
        val username = usernames?.editableUsername
        if (!username.isNullOrBlank()) return "@$username"
        val phone = phoneNumber.orEmpty().removePrefix("+")
        return if (phone.isBlank()) "" else "+$phone"
    }
}
