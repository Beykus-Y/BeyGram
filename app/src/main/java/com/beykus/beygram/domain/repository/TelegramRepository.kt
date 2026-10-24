package com.beykus.beygram.domain.repository

import com.beykus.beygram.diagnostics.report.BotConfirmation
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.ChannelAdminEventPage
import com.beykus.beygram.domain.model.ChannelEventFilter
import com.beykus.beygram.domain.model.ChannelStatisticsSummary
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatInvitePreview
import com.beykus.beygram.domain.model.ChatMembersPage
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.InviteJoinOutcome
import com.beykus.beygram.domain.model.MessageLinkTarget
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.BlockedSenderKind
import com.beykus.beygram.domain.model.BlockedSendersPage
import com.beykus.beygram.domain.model.PrivacyRules
import com.beykus.beygram.domain.model.PrivacySetting
import com.beykus.beygram.domain.model.PrivacySettingState
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.model.TelegramProxyDraft
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.ReportBotChannel
import kotlinx.coroutines.flow.StateFlow

interface TelegramRepository : ConversationRepository, NotificationRepository, ReportBotChannel, StorageRepository, GroupAdministrationRepository {
    /** Текущее состояние авторизации. Обновляется реактивно при каждом ответе TDLib. */
    val authState: StateFlow<AuthState>

    /** Список чатов. Обновляется реактивно по мере получения обновлений от TDLib. */
    val chats: StateFlow<List<ChatPreview>>

    /** Архивные чаты. Не входят в [chats]; обновляются так же реактивно. */
    val archivedChats: StateFlow<List<ChatPreview>>

    /** Authoritative server folder metadata and its Main tab position (TDLib UpdateChatFolders). */
    val chatFolders: StateFlow<com.beykus.beygram.domain.model.TelegramChatFolders>

    /** Separately loaded TDLib ChatListFolder chats: includes chats absent from Main. */
    val folderChats: StateFlow<Map<Int, List<ChatPreview>>>
    val folderLoadStates: StateFlow<Map<Int, com.beykus.beygram.domain.model.TelegramFolderLoadState>>
    suspend fun loadMoreFolderChats(folderId: Int): Result<Unit>
    suspend fun setFolderChatPinned(folderId: Int, chatId: Long, pinned: Boolean): Result<Unit>


    /** Read and change actual TDLib server chat folder rules, never local chat filters. */
    suspend fun getFolderDraft(folderId: Int): Result<com.beykus.beygram.domain.model.TelegramFolderDraft>
    suspend fun saveFolderDraft(draft: com.beykus.beygram.domain.model.TelegramFolderDraft): Result<Int>
    suspend fun deleteServerFolder(folderId: Int): Result<Unit>
    suspend fun reorderServerFolders(ids: List<Int>, mainPosition: Int): Result<Unit>

    val connectionState: StateFlow<TelegramConnectionState>
    val myProfile: StateFlow<ProfileState>
    val bioLengthLimit: StateFlow<Int>
    fun observeChatInfo(chatId: Long): StateFlow<ProfileState>
    fun refreshChatInfo(chatId: Long)
    /** Страница участников группы или супергруппы; у каналов без прав TDLib откажет. */
    suspend fun loadChatMembers(chatId: Long, offset: Int, limit: Int): Result<ChatMembersPage>
    fun refreshMyProfile()
    suspend fun updateMyProfile(edit: ProfileEdit): Result<Unit>
    suspend fun setMyProfilePhoto(photo: OutgoingMedia): Result<Unit>
    /** Only available TDLib photo history, fetched on demand, never as part of ProfileInfo. */
    suspend fun loadProfilePhotos(profileId: Long, offset: Int, limit: Int): Result<com.beykus.beygram.domain.model.ProfilePhotosPage>
    /** Delete the selected own Telegram profile photo, by its TDLib photo id. */
    suspend fun deleteMyProfilePhoto(photoId: Long): Result<Unit>
    /** Reuse an existing Telegram profile photo as the current (non-public) avatar. */
    suspend fun setPreviousProfilePhoto(photoId: Long): Result<Unit>

    /** Переносит чат в архив или возвращает его в основной список. Списки обновятся по ответу TDLib. */
    suspend fun setChatArchived(chatId: Long, archived: Boolean): Result<Unit>
    /** Pin/unpin a conversation on Telegram's main chat list. */
    suspend fun setChatPinned(chatId: Long, pinned: Boolean): Result<Unit>
    /** Mute indefinitely or explicitly enable notifications for one conversation. */
    suspend fun setChatMuted(chatId: Long, muted: Boolean): Result<Unit>
    /** Remove this conversation and its history only for the signed-in user, never for everyone. */
    suspend fun deleteChatForSelf(chatId: Long): Result<Unit>
    /** Block the other user from a private conversation. Refuses group/channel and self. */
    suspend fun blockPrivateChat(chatId: Long): Result<Unit>
    /** Block or unblock the other user in a private conversation; the block list is account-wide. */
    suspend fun setPrivateChatBlocked(chatId: Long, blocked: Boolean): Result<Unit>
    /** Telegram privacy rules are stored on the server and shared by every signed-in device. */
    suspend fun loadPrivacySettings(): Result<List<PrivacySettingState>>
    /** Replaces one privacy setting; exceptions are sent as TDLib allow/restrict user rules. */
    suspend fun setPrivacySetting(setting: PrivacySetting, rules: PrivacyRules): Result<Unit>
    /** A page of TDLib's main block list, including users and chats returned by the server. */
    suspend fun loadBlockedSenders(offset: Int, limit: Int = 50): Result<BlockedSendersPage>
    /** Changes the account block list for a user or chat sender. Never deletes a contact. */
    suspend fun setSenderBlocked(kind: BlockedSenderKind, senderId: Long, blocked: Boolean): Result<Unit>
    /** Reports a private chat for spam using Telegram's required report option flow. */
    suspend fun reportSpam(chatId: Long): Result<Unit>
    /** Resolves a blocked user's private chat and reports it for spam without opening it. */
    suspend fun reportSpamForUser(userId: Long): Result<Unit>
    /** Create a Telegram broadcast channel; returns its new chat ID. */
    suspend fun createChannel(title: String, description: String): Result<Long>
    /** Create a basic Telegram group with explicitly selected contact IDs. */
    /** Process-owned creation; caller can be disposed without canceling TDLib's request. */
    val groupCreation: StateFlow<com.beykus.beygram.domain.model.GroupCreationState>
    fun beginGroupCreation(title: String, memberUserIds: List<Long>): Result<Unit>
    fun acknowledgeGroupCreation()
    /** Explicit acknowledgement after the app restarts with an unconfirmed request. */
    fun acknowledgeUncertainGroupCreation()

    /** Current Telegram session and other authorized devices. */
    suspend fun activeSessions(): Result<List<com.beykus.beygram.domain.model.DeviceSession>>
    suspend fun terminateOtherSession(sessionId: Long): Result<Unit>
    suspend fun terminateAllOtherSessions(): Result<Unit>

    /** Настройка аккаунта: возвращать ли непрочитанный архивный чат в основной список при новом сообщении. */
    suspend fun archiveReturnsOnNewMessage(): Result<Boolean>

    /** Меняет настройку аккаунта, поэтому она действует на все устройства, где вошёл этот аккаунт. */
    suspend fun setArchiveReturnsOnNewMessage(enabled: Boolean): Result<Unit>

    /** Отмечает прочитанными все сообщения чата, в том числе у архивных чатов. */
    suspend fun markChatRead(chatId: Long): Result<Unit>


    /** Контакты из адресной книги Telegram, по имени. */
    suspend fun loadContacts(): Result<List<ContactPreview>>

    /** Добавляет контакт по номеру телефона; номер должен быть зарегистрирован в Telegram. */
    suspend fun addContact(firstName: String, lastName: String, phone: String): Result<Unit>

    /** Изменяет имя и телефон существующего контакта в адресной книге Telegram. */
    suspend fun updateContact(userId: Long, firstName: String, lastName: String, phone: String): Result<Unit>

    /** Удаляет контакт из адресной книги; чат и блокировка не затрагиваются. */
    suspend fun deleteContact(userId: Long): Result<Unit>

    /** Отправляет контакт в чат сообщением TDLib с карточкой и vCard. */
    suspend fun sendContact(chatId: Long, userId: Long): Result<Unit>

    /** Открывает личный чат с пользователем и возвращает идентификатор чата. */
    suspend fun openPrivateChat(userId: Long): Result<Long>

    /** Resolve a validated public username or bot through TDLib and return its chat ID. */
    suspend fun resolvePublicChat(username: String): Result<Long>

    /** Resolve a validated phone number through TDLib and return the private chat ID. */
    suspend fun resolvePhoneNumber(phone: String): Result<Long>

    /** Inspect an invite link without joining it. The UI must ask for confirmation before [joinInviteLink]. */
    suspend fun checkInviteLink(url: String): Result<ChatInvitePreview>

    /** Join an invite link only after an explicit user confirmation. */
    suspend fun joinInviteLink(url: String): Result<InviteJoinOutcome>

    /** Resolve a t.me message link (including /c/ links) and its forum topic without sending any action. */
    suspend fun resolveMessageLink(url: String): Result<MessageLinkTarget>

    /** Channel statistics for an administrator; TDLib rejects the request for ordinary members. */
    suspend fun channelStatistics(chatId: Long): Result<ChannelStatisticsSummary>

    /** Admin log page, newest first. Pass 0 for the first page, then [ChannelAdminEventPage.nextFromEventId]. */
    suspend fun channelAdminEvents(chatId: Long, fromEventId: Long, filter: ChannelEventFilter): Result<ChannelAdminEventPage>

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

    /** Запрашивает вход по QR-коду; ссылка придёт через [authState] как [AuthState.WaitingForQr]. */
    fun requestQrLogin()

    /** Отменяет вход по QR-коду и возвращает экран ввода телефона. */
    fun cancelQrLogin()

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

    /** Маршрут соединения с Telegram прямо сейчас: напрямую, через прокси или через подписку серверов. */
    suspend fun currentRoute(): Result<TelegramRoute>

    /** Время TCP-соединения с сервером прокси, в миллисекундах. */
    suspend fun measureProxyLatency(server: String, port: Int): Result<Long>

    /** Истина, если TDLib сейчас работает через локальный listener подписки Mihomo. Ошибка запроса не равна «выключено». */
    suspend fun isMihomoProxyEnabled(): Result<Boolean>

    /** Route TDLib through BeyGram's local Mihomo SOCKS listener and restore the prior proxy on disable. */
    suspend fun setMihomoProxyEnabled(enabled: Boolean): Result<Unit>

    /** Opt-in synthetic video-note round trip through the user's Saved Messages. */
    suspend fun sendVideoNoteLossTest(media: OutgoingMedia, remoteCopy: java.io.File): Result<com.beykus.beygram.domain.model.VideoNoteLossRemoteResult>
    /** Delete only a message previously sent by this diagnostic, after user confirmation. */
    suspend fun deleteVideoNoteLossTest(messageId: Long): Result<Unit>

    fun observeFile(fileId: Int): StateFlow<MediaFileState>
    fun downloadFile(fileId: Int)
}
