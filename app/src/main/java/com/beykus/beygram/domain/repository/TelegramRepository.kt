package com.beykus.beygram.domain.repository

import com.beykus.beygram.diagnostics.report.BotConfirmation
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChannelAdminEventPage
import com.beykus.beygram.domain.model.ChannelEventFilter
import com.beykus.beygram.domain.model.ChannelStatisticsSummary
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatInvitePreview
import com.beykus.beygram.domain.model.ChatMembersPage
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.InviteJoinOutcome
import com.beykus.beygram.domain.model.MessageLinkTarget
import com.beykus.beygram.domain.model.GiftSendOutcome
import com.beykus.beygram.domain.model.GiftsInfo
import com.beykus.beygram.domain.model.PremiumCheckoutOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeApplyOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeInfo
import com.beykus.beygram.domain.model.PremiumInfo
import com.beykus.beygram.domain.model.PremiumPlan
import com.beykus.beygram.domain.model.StarTopUpOption
import com.beykus.beygram.domain.model.StarsTopUpOutcome
import com.beykus.beygram.domain.model.StarTransactionsPage
import com.beykus.beygram.domain.model.StarsInfo
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.BlockedSenderKind
import com.beykus.beygram.domain.model.BlockedSendersPage
import com.beykus.beygram.domain.model.PrivacyRules
import com.beykus.beygram.domain.model.PrivacySetting
import com.beykus.beygram.domain.model.PrivacySettingState
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryArchivePage
import com.beykus.beygram.domain.model.StoryPostEvent
import com.beykus.beygram.domain.model.StoryPostPermission
import com.beykus.beygram.domain.model.StoryPostTarget
import com.beykus.beygram.domain.model.StoryPostTicket
import com.beykus.beygram.domain.model.StorySubmissionState
import com.beykus.beygram.domain.model.StoryPrivacy
import com.beykus.beygram.domain.model.ProfileState
import com.beykus.beygram.domain.model.ProfileEdit
import com.beykus.beygram.domain.model.SavedTelegramProxy
import com.beykus.beygram.domain.model.TelegramProxyDraft
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.ReportBotChannel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TelegramRepository : ConversationRepository, NotificationRepository, ReportBotChannel, StorageRepository,
    AccountSecurityRepository, GroupAdministrationRepository {
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

    /** Active stories from the main story list, already filtered for secret chats and blocked senders. */
    val activeStories: StateFlow<List<ChatStories>>
    /** Asks TDLib for the main story list again; updates then arrive through [activeStories]. */
    fun refreshActiveStories()
    /** Loads the next page when the story bar reaches its end. */
    fun loadMoreActiveStories()
    /** Terminal results of PostStory, including the later upload success/failure update. */
    val storyPostEvents: SharedFlow<StoryPostEvent>
    /** Full story content is requested only when the viewer opens it. */
    suspend fun loadStory(posterChatId: Long, storyId: Int): Result<StoryDetail>
    /** Informs TDLib that the story is being viewed; the unread marker is updated optimistically. */
    suspend fun openStory(posterChatId: Long, storyId: Int): Result<Unit>
    suspend fun closeStory(posterChatId: Long, storyId: Int): Result<Unit>
    suspend fun storyReactions(): Result<List<ReactionOption>>
    /** Pass null to remove the current reaction. */
    suspend fun setStoryReaction(posterChatId: Long, storyId: Int, reaction: ReactionOption?): Result<Unit>
    /** Sends a normal chat message that quotes the story, as Telegram clients do. */
    suspend fun replyToStory(posterChatId: Long, storyId: Int, text: String): Result<Unit>
    suspend fun deleteStory(posterChatId: Long, storyId: Int): Result<Unit>
    /** Expired stories of the signed-in user, newest first. Pass the last page's nextFromStoryId for more. */
    suspend fun storyArchive(fromStoryId: Int = 0): Result<StoryArchivePage>
    suspend fun storyPostPermission(chatId: Long): Result<StoryPostPermission>
    /** Chats where the signed-in user may post a story; self is always included first. */
    suspend fun storyPostTargets(): Result<List<StoryPostTarget>>
    /** Lets a closed publisher hand a still-uploading story's file cleanup to the repository. */
    fun abandonStoryUpload(posterChatId: Long, storyId: Int)
    /** The publisher closed before PostStory returned a ticket. */
    fun abandonStorySubmission(submissionId: String)
    suspend fun storyCaptionLimit(): Result<Int>
    /** Edit a caption of an own story; pass an empty string to clear it. */
    suspend fun editStoryCaption(posterChatId: Long, storyId: Int, caption: String): Result<Unit>
    /** Privacy can only be changed for the signed-in user's own story. */
    suspend fun setStoryPrivacy(storyId: Int, privacy: StoryPrivacy, selectedUserIds: List<Long> = emptyList()): Result<Unit>
    /** Toggle whether an expired story stays accessible in the profile. */
    suspend fun setStoryPostedToChatPage(posterChatId: Long, storyId: Int, posted: Boolean): Result<Unit>
    /** State of one publisher-owned submission, keyed by a UI-persisted identifier. */
    fun storySubmissionState(submissionId: String): StateFlow<StorySubmissionState>
    suspend fun publishStory(
        submissionId: String,
        targetChatId: Long,
        media: OutgoingMedia,
        caption: String,
        privacy: StoryPrivacy,
        selectedUserIds: List<Long> = emptyList(),
        activePeriodSeconds: Int,
        protectContent: Boolean,
        keepInProfile: Boolean
    ): Result<StoryPostTicket>

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

    /** Read-only Premium status, capabilities and plans. */
    suspend fun premiumInfo(): Result<PremiumInfo>

    /**
     * Re-checks a confirmed plan against TDLib and resolves Telegram's external checkout URL.
     * The app never charges, never collects card data and never retries this request.
     */
    suspend fun premiumPaymentUrl(plan: PremiumPlan): PremiumCheckoutOutcome

    /** Inspect a Premium gift code without applying it. The code is never stored or logged. */
    suspend fun checkPremiumGiftCode(code: String): Result<PremiumGiftCodeInfo>

    /** Apply one Premium gift code after explicit confirmation. Never retried by the repository. */
    suspend fun applyPremiumGiftCode(code: String): PremiumGiftCodeApplyOutcome

    /** Stars balance, the first page of transactions and the current top-up options. */
    suspend fun starsInfo(): Result<StarsInfo>

    /**
     * Re-checks a confirmed Stars package and returns only external payment pages. A card form
     * or a form that spends existing Stars is rejected; SendPaymentForm is never called.
     */
    suspend fun starsTopUpPayment(option: StarTopUpOption): StarsTopUpOutcome

    /** Next transaction page, newest-offset cursor from [StarsInfo.nextOffset]. */
    suspend fun starTransactions(offset: String): Result<StarTransactionsPage>

    /** Gift catalog and, for the own profile, received gifts and collections. */
    suspend fun giftsInfo(profileId: Long, isSelf: Boolean): Result<GiftsInfo>

    /** Sends one gift after explicit confirmation. The repository never retries it. */
    suspend fun sendGift(profileId: Long, isSelf: Boolean, giftId: Long): GiftSendOutcome

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

    /**
     * Creates a device-specific E2E secret chat with a regular user through CreateNewSecretChat and returns its chat id.
     * The caller must not create one with itself or a bot; the implementation validates this again with TDLib.
     */
    suspend fun createSecretChat(userId: Long): Result<Long>

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

    /** Explicit user download; always allowed and unaffected by auto-download limits. */
    fun downloadFile(fileId: Int)
    /**
     * Downloads a small UI asset such as an avatar or thumbnail. It does not apply the media policy,
     * but it is counted as automatic traffic because the user did not press Download.
     */
    fun downloadFileInBackground(fileId: Int)
    /** Stops a download that was only needed by a closed screen; completed files stay available. */
    fun cancelFileDownload(fileId: Int)

    /** Automatic download that the repository may deny, cancel or skip according to the local policy. */
    fun autoDownloadFile(request: AutoDownloadRequest)
}
