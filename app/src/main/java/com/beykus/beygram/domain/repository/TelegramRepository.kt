package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.model.TelegramProxyDraft
import kotlinx.coroutines.flow.StateFlow

interface TelegramRepository : ConversationRepository, NotificationRepository {
    /** Текущее состояние авторизации. Обновляется реактивно при каждом ответе TDLib. */
    val authState: StateFlow<AuthState>

    /** Список чатов. Обновляется реактивно по мере получения обновлений от TDLib. */
    val chats: StateFlow<List<ChatPreview>>

    /** Архивные чаты. Не входят в [chats]; обновляются так же реактивно. */
    val archivedChats: StateFlow<List<ChatPreview>>

    val connectionState: StateFlow<TelegramConnectionState>
    val myProfile: StateFlow<ProfileState>
    val bioLengthLimit: StateFlow<Int>
    fun observeChatInfo(chatId: Long): StateFlow<ProfileState>
    fun refreshChatInfo(chatId: Long)
    fun refreshMyProfile()
    suspend fun updateMyProfile(edit: ProfileEdit): Result<Unit>
    suspend fun setMyProfilePhoto(photo: OutgoingMedia): Result<Unit>

    /** Переносит чат в архив или возвращает его в основной список. Списки обновятся по ответу TDLib. */
    suspend fun setChatArchived(chatId: Long, archived: Boolean): Result<Unit>

    /** Отмечает прочитанными все сообщения чата, в том числе у архивных чатов. */
    suspend fun markChatRead(chatId: Long): Result<Unit>

    /** Контакты из адресной книги Telegram, по имени. */
    suspend fun loadContacts(): Result<List<ContactPreview>>

    /** Добавляет контакт по номеру телефона; номер должен быть зарегистрирован в Telegram. */
    suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Unit>

    /** Открывает личный чат с пользователем и возвращает идентификатор чата. */
    suspend fun openPrivateChat(userId: Long): Result<Long>

    /** Страница истории звонков. Первая страница запрашивается с пустым [offset], дальше — с [CallHistoryPage.nextOffset]. */
    suspend fun loadCallHistory(offset: String): Result<CallHistoryPage>

    /** Инициирует процесс авторизации (отправляет параметры TDLib). */
    fun startAuthorization()

    /** Отправляет номер телефона; результат придёт через [authState]. */
    fun submitPhoneNumber(phone: String)

    /** Отправляет проверочный код; результат придёт через [authState]. */
    fun submitCode(code: String)

    /** Повторно запрашивает SMS/код без смены экрана. */
    fun resendCode()

    /** Отправляет облачный пароль (2FA); результат придёт через [authState]. */
    fun submitPassword(password: String)

    /** Сбрасывает ошибку обратно в [AuthState.WaitingForPhone]. */
    fun resetError()

    /** Reopen the transport while preserving an in-flight authorization request. */
    fun reconnect()

    /** Прокси, сохранённые в TDLib. Служебный адрес подписки Mihomo в список не входит. */
    suspend fun loadSavedProxies(): Result<List<SavedTelegramProxy>>

    /** Сохраняет прокси в TDLib. С [enable] = true он сразу становится текущим. */
    suspend fun addProxy(draft: TelegramProxyDraft, enable: Boolean): Result<Unit>

    /** Делает сохранённый прокси текущим. */
    suspend fun enableProxy(id: Int): Result<Unit>

    /** Отключает прокси: соединение с Telegram идёт напрямую. */
    suspend fun disableProxy(): Result<Unit>

    /** Удаляет сохранённый прокси. */
    suspend fun removeProxy(id: Int): Result<Unit>

    /** Время TCP-соединения с сервером прокси, в миллисекундах. */
    suspend fun measureProxyLatency(server: String, port: Int): Result<Long>

    /** Истина, если TDLib сейчас работает через локальный listener подписки Mihomo. Ошибка запроса не равна «выключено». */
    suspend fun isMihomoProxyEnabled(): Result<Boolean>

    /** Route TDLib through BeyGram's local Mihomo SOCKS listener and restore the prior proxy on disable. */
    suspend fun setMihomoProxyEnabled(enabled: Boolean): Result<Unit>

    fun observeFile(fileId: Int): StateFlow<MediaFileState>
    fun downloadFile(fileId: Int)
}
