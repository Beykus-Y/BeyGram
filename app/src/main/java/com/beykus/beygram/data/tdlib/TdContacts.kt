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

/** Короче этого номер телефона быть не может, значит его ввели не полностью. */
private const val MIN_PHONE_DIGITS = 7

/** Адресная книга Telegram: список контактов и добавление по номеру. Открытие чата делает репозиторий. */
internal class TdContacts(private val client: () -> Client, private val profiles: TdProfiles) {
    private val requests = TdRequests(client)

    /** Пользователей запрашиваем параллельно: у большой адресной книги последовательные запросы заметно медленные. */
    suspend fun load(): List<ContactPreview> = coroutineScope {
        val ids = requests(TdApi.GetContacts()).userIds
        ids.map { id -> async { userOrNull(id) } }
            .awaitAll()
            .filterNotNull()
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
        if (phone.count { it.isDigit() } < MIN_PHONE_DIGITS) throw UserMessageException(UiText.Res(R.string.err_phone_incomplete))
        val note = TdApi.FormattedText("", emptyArray<TdApi.TextEntity>())
        val imported = try {
            requests(TdApi.ImportContacts(arrayOf(TdApi.ImportedContact(phone, firstName, lastName, note))))
        } catch (error: TdRequestError) {
            if (error.message?.contains("PHONE_NUMBER_INVALID") == true) throw UserMessageException(UiText.Res(R.string.err_phone_invalid), error)
            throw error
        }
        if (imported.userIds.none { it != 0L }) throw UserMessageException(UiText.Res(R.string.err_phone_not_on_telegram))
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
        title = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
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
