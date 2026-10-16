package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ContactPreview
import kotlinx.coroutines.CancellationException
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Короче этого номер телефона быть не может, значит его ввели не полностью. */
private const val MIN_PHONE_DIGITS = 7

/** Адресная книга Telegram: список контактов и добавление по номеру. Открытие чата делает репозиторий. */
internal class TdContacts(private val client: () -> Client, private val profiles: TdProfiles) {
    private val requests = TdRequests(client)

    suspend fun load(): List<ContactPreview> {
        val ids = requests(TdApi.GetContacts()).userIds
        return ids.mapNotNull { id -> userOrNull(id)?.toContact() }.sortedBy { it.title.lowercase() }
    }

    /** Если номера нет в Telegram, TDLib возвращает идентификатор 0 вместо пользователя. */
    suspend fun add(firstName: String, lastName: String, phone: String) {
        if (phone.count { it.isDigit() } < MIN_PHONE_DIGITS) throw IllegalStateException("Введите номер телефона полностью")
        val note = TdApi.FormattedText("", emptyArray<TdApi.TextEntity>())
        val imported = try {
            requests(TdApi.ImportContacts(arrayOf(TdApi.ImportedContact(phone, firstName, lastName, note))))
        } catch (error: TdRequestError) {
            if (error.message?.contains("PHONE_NUMBER_INVALID") == true) throw IllegalStateException("Некорректный номер телефона")
            throw error
        }
        if (imported.userIds.none { it != 0L }) throw IllegalStateException("Такого номера нет в Telegram")
    }

    /** Один пользователь, которого TDLib не отдал, не должен скрывать весь список контактов. */
    private suspend fun userOrNull(id: Long): TdApi.User? = try {
        requests(TdApi.GetUser(id))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
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
