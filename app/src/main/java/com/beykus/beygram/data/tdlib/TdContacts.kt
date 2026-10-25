package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.GlobalSearchPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

private const val MAX_CONTACT_NAME_LENGTH = 64

/** Адресная книга Telegram: список, добавление, изменение, удаление и карточка для отправки. */
internal class TdContacts(private val client: () -> Client, private val profiles: TdProfiles) {
    private val requests = TdRequests(client)

    /** Пользователей запрашиваем параллельно: у большой адресной книги последовательные запросы заметно медленные. */
    suspend fun load(): List<ContactPreview> = coroutineScope {
        requests(TdApi.GetContacts()).userIds
            .map { id -> async { userOrNull(id) } }
            .awaitAll()
            .filterNotNull()
            // TDLib source of truth: the same user must never be rendered twice after a merge/sync.
            .distinctBy { it.id }
            .map { it.toContact() }
            .sortedBy { it.title.lowercase() }
    }

    /**
     * Global contact search for the unified search screen. TDLib returns user ids plus a total count;
     * the total is not an offset, so a later page asks for a larger limit and the UI merges by user id.
     * Unlike [load], this keeps the relevance order returned by TDLib.
     */
    suspend fun search(query: String, limit: Int): GlobalSearchPage<ContactPreview> = coroutineScope {
        val found = requests(TdApi.SearchContacts(query, limit.coerceAtLeast(1)))
        val contacts = found.userIds.toList().distinct()
            .map { id -> async { userOrNull(id) } }
            .awaitAll()
            .filterNotNull()
            .map { it.toContact() }
        GlobalSearchPage(contacts, found.totalCount)
    }

    /** Если номера нет в Telegram, TDLib возвращает идентификатор 0 вместо пользователя. */
    suspend fun add(firstName: String, lastName: String, phone: String) {
        validateNames(firstName, lastName)
        val normalized = ContactCard.normalizePhone(phone)
        if (!ContactCard.isValidPhone(normalized, allowEmpty = false)) {
            throw UserMessageException(UiText.Res(R.string.err_phone_incomplete))
        }
        val imported = contactRequest {
            requests(TdApi.ImportContacts(arrayOf(importedContact(firstName.trim(), lastName.trim(), normalized))))
        }
        if (imported.userIds.none { it != 0L }) throw UserMessageException(UiText.Res(R.string.err_phone_not_on_telegram))
    }

    /** Редактирует существующего пользователя по его TDLib id; телефон можно оставить пустым. */
    suspend fun update(userId: Long, firstName: String, lastName: String, phone: String) {
        validateNames(firstName, lastName)
        val normalized = if (phone.isBlank()) "" else ContactCard.normalizePhone(phone)
        if (phone.isNotBlank() && normalized.isEmpty()) throw UserMessageException(UiText.Res(R.string.err_phone_invalid))
        if (!ContactCard.isValidPhone(normalized, allowEmpty = true)) {
            throw UserMessageException(UiText.Res(R.string.err_phone_incomplete))
        }
        contactRequest {
            requests(addContactRequest(userId, firstName.trim(), lastName.trim(), normalized))
        }
    }

    /** Удаляет только запись адресной книги. Блокировка и история чата этим запросом не меняются. */
    suspend fun delete(userId: Long) {
        contactRequest { requests(removeContactRequest(userId)) }
    }

    /** Свежие имя и телефон из TDLib: список мог быть загружен раньше, а контакт уже изменился. */
    suspend fun shareCard(userId: Long): TdApi.Contact = contactRequest {
        val user = requests(TdApi.GetUser(userId))
        val firstName = user.firstName.orEmpty()
        val lastName = user.lastName.orEmpty()
        val phone = user.phoneNumber.orEmpty()
        TdApi.Contact(phone, firstName, lastName, ContactCard.vCard(firstName, lastName, phone), user.id)
    }

    private fun validateNames(firstName: String, lastName: String) {
        val first = firstName.trim()
        val last = lastName.trim()
        if (first.isBlank() && last.isBlank()) throw UserMessageException(UiText.Res(R.string.err_name_empty))
        if (first.length > MAX_CONTACT_NAME_LENGTH || last.length > MAX_CONTACT_NAME_LENGTH) {
            throw UserMessageException(UiText.Res(R.string.err_name_too_long, listOf(MAX_CONTACT_NAME_LENGTH)))
        }
    }

    /** Понятные ошибки Telegram: неверный/занятый номер или исчезнувший пользователь. */
    private suspend fun <T> contactRequest(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: TdRequestError) {
        val text = error.contactErrorText() ?: throw error
        throw UserMessageException(text, error)
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
        title = ContactCard.fullName(firstName.orEmpty(), lastName.orEmpty()),
        subtitle = subtitle(),
        photo = profilePhoto?.let { profiles.photo(it.small, it.big) },
        avatarColorArgb = id.stableColor(),
        firstName = firstName.orEmpty(),
        lastName = lastName.orEmpty(),
        phone = phoneNumber.orEmpty(),
        isBot = type is TdApi.UserTypeBot
    )

    private fun TdApi.User.subtitle(): String {
        val username = usernames?.editableUsername
        if (!username.isNullOrBlank()) return "@$username"
        val phone = phoneNumber.orEmpty().removePrefix("+")
        return if (phone.isBlank()) "" else "+$phone"
    }
}

private fun TdRequestError.contactErrorText(): UiText? {
    val text = message.orEmpty()
    return when {
        "PHONE_NUMBER_INVALID" in text -> UiText.Res(R.string.err_phone_invalid)
        "PHONE_NUMBER_OCCUPIED" in text -> UiText.Res(R.string.err_phone_occupied)
        "PHONE_NUMBER_NOT_OCCUPIED" in text || "PHONE_NUMBER_UNOCCUPIED" in text ->
            UiText.Res(R.string.err_phone_not_on_telegram)
        "USER_NOT_FOUND" in text || "USER_ID_INVALID" in text -> UiText.Res(R.string.err_contact_not_found)
        else -> null
    }
}
