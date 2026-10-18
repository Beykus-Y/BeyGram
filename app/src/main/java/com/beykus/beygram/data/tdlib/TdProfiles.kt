package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** TDLib objects stay behind this lock; UI observes immutable domain snapshots. */
internal class TdProfiles(private val client: () -> Client, private val files: MediaFileStore) {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chats = mutableMapOf<Long, TdApi.Chat>()
    private val users = mutableMapOf<Long, TdApi.User>()
    private val userFull = mutableMapOf<Long, TdApi.UserFullInfo>()
    private val groups = mutableMapOf<Long, TdApi.BasicGroup>()
    private val groupFull = mutableMapOf<Long, TdApi.BasicGroupFullInfo>()
    private val supers = mutableMapOf<Long, TdApi.Supergroup>()
    private val superFull = mutableMapOf<Long, TdApi.SupergroupFullInfo>()
    private val onlineCounts = mutableMapOf<Long, Int>()
    private val sharedCounts = mutableMapOf<Long, SharedCounts>()
    private val chatAdmins = mutableMapOf<Long, List<ProfileAdmin>>()
    private val chatMemberPreview = mutableMapOf<Long, List<ProfileMember>>()
    private val typingBy = mutableMapOf<Long, MutableMap<String, Typist>>()
    private val typingFlows = ConcurrentHashMap<Long, MutableStateFlow<List<String>>>()
    private val details = ConcurrentHashMap<Long, MutableStateFlow<ProfileState>>()
    private var myId = 0L
    private val _me = MutableStateFlow(ProfileState())
    val me = _me.asStateFlow()
    private val _bioLimit = MutableStateFlow(70)
    val bioLimit = _bioLimit.asStateFlow()
    fun senderName(sender: TdApi.MessageSender?): String = synchronized(lock) {
        when (sender) {
            is TdApi.MessageSenderUser -> users[sender.userId]?.let { listOf(it.firstName, it.lastName).filter { name -> !name.isNullOrBlank() }.joinToString(" ") }.orEmpty()
            is TdApi.MessageSenderChat -> chats[sender.chatId]?.title.orEmpty()
            else -> ""
        }
    }

    fun photo(photo: TdApi.ChatPhotoInfo?): AvatarPhoto? = photo?.let { photo(it.small, it.big) }
    fun photo(small: TdApi.File, big: TdApi.File): AvatarPhoto {
        files.register(small.toMediaFileState()); files.register(big.toMediaFileState())
        return AvatarPhoto(small.id, big.id)
    }
    fun putChat(chat: TdApi.Chat) = synchronized(lock) { chats[chat.id] = chat; publish() }
    fun observe(chatId: Long): StateFlow<ProfileState> = synchronized(lock) {
        details.getOrPut(chatId) { MutableStateFlow(ProfileState(info = chatInfo(chatId))) }.asStateFlow()
    }
    /** Names of people typing in the chat now. The same flow instance comes back on every call, so collectors are not restarted. */
    fun observeTyping(chatId: Long): StateFlow<List<String>> = typingFlow(chatId)
    private fun typingFlow(chatId: Long): MutableStateFlow<List<String>> = typingFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    fun handle(update: TdApi.Object) = synchronized(lock) {
        when (update) {
            is TdApi.UpdateUser -> users[update.user.id] = update.user
            is TdApi.UpdateUserStatus -> users[update.userId]?.status = update.status
            is TdApi.UpdateUserFullInfo -> userFull[update.userId] = update.userFullInfo
            is TdApi.UpdateBasicGroup -> groups[update.basicGroup.id] = update.basicGroup
            is TdApi.UpdateBasicGroupFullInfo -> groupFull[update.basicGroupId] = update.basicGroupFullInfo
            is TdApi.UpdateSupergroup -> supers[update.supergroup.id] = update.supergroup
            is TdApi.UpdateSupergroupFullInfo -> superFull[update.supergroupId] = update.supergroupFullInfo
            is TdApi.UpdateChatPhoto -> chats[update.chatId]?.photo = update.photo
            is TdApi.UpdateChatTitle -> chats[update.chatId]?.title = update.title
            is TdApi.UpdateChatOnlineMemberCount -> onlineCounts[update.chatId] = update.onlineMemberCount
            is TdApi.UpdateChatAction -> { updateTyping(update); return@synchronized }
            is TdApi.UpdateOption -> if (update.name == "bio_length_max") (update.value as? TdApi.OptionValueInteger)?.let { _bioLimit.value = it.value.toInt().coerceIn(1, 4096) }
            else -> return@synchronized
        }
        publish()
    }

    fun refreshMe() {
        synchronized(lock) { if (_me.value.isLoading) return; _me.value = _me.value.copy(isLoading = true, error = null) }
        scope.launch {
            val result = runCatching { withTimeout(25_000) {
                val user = request(TdApi.GetMe())
                synchronized(lock) { myId = user.id; users[user.id] = user; publish() }
                val full = request(TdApi.GetUserFullInfo(user.id))
                synchronized(lock) { userFull[user.id] = full; publish() }
                val option = request(TdApi.GetOption("bio_length_max"))
                if (option is TdApi.OptionValueInteger) _bioLimit.value = option.value.toInt().coerceIn(1, 4096)
            } }
            synchronized(lock) { _me.value = _me.value.copy(isLoading = false, error = result.exceptionOrNull()?.profileError()) }
        }
    }

    fun refreshChat(chatId: Long) {
        val state = synchronized(lock) {
            val state = details.getOrPut(chatId) { MutableStateFlow(ProfileState(info = chatInfo(chatId))) }
            if (state.value.isLoading) return
            state.value = state.value.copy(isLoading = true, error = null)
            state
        }
        scope.launch {
            val result = runCatching { withTimeout(25_000) {
                val chat = request(TdApi.GetChat(chatId))
                putChat(chat)
                when (val type = chat.type) {
                    is TdApi.ChatTypePrivate -> loadUser(type.userId)
                    is TdApi.ChatTypeSecret -> loadUser(type.userId)
                    is TdApi.ChatTypeBasicGroup -> {
                        val group = request(TdApi.GetBasicGroup(type.basicGroupId))
                        val full = request(TdApi.GetBasicGroupFullInfo(type.basicGroupId))
                        synchronized(lock) { groups[group.id] = group; groupFull[group.id] = full }
                    }
                    is TdApi.ChatTypeSupergroup -> {
                        val group = request(TdApi.GetSupergroup(type.supergroupId))
                        val full = request(TdApi.GetSupergroupFullInfo(type.supergroupId))
                        synchronized(lock) { supers[group.id] = group; superFull[group.id] = full }
                    }
                }
            } }
            synchronized(lock) { publish(); state.value = state.value.copy(isLoading = false, error = result.exceptionOrNull()?.profileError()) }
            // Счётчики не входят в общий таймаут: медленный подсчёт не должен показать ошибку загрузки профиля.
            scope.launch {
                val counts = runCatching { withTimeout(25_000) { loadSharedCounts(chatId) } }.getOrNull() ?: return@launch
                synchronized(lock) { sharedCounts[chatId] = counts; publish() }
            }
            scope.launch {
                val admins = runCatching { withTimeout(25_000) { loadAdministrators(chatId) } }.getOrNull() ?: return@launch
                synchronized(lock) { chatAdmins[chatId] = admins; publish() }
            }
            scope.launch {
                if (synchronized(lock) { chatProfile(chatId)?.canListMembers } != true) return@launch
                val page = runCatching { withTimeout(25_000) { loadMembers(chatId, 0, PREVIEW_MEMBERS) } }.getOrNull() ?: return@launch
                synchronized(lock) { chatMemberPreview[chatId] = page.members; publish() }
            }
        }
    }
    /** Владелец и администраторы групп и каналов. Личным чатам TDLib их не отдаёт, поэтому запрос не отправляем. */
    private suspend fun loadAdministrators(chatId: Long): List<ProfileAdmin> {
        val type = synchronized(lock) { chats[chatId]?.type }
        if (type == null || type is TdApi.ChatTypePrivate || type is TdApi.ChatTypeSecret) return emptyList()
        val admins = request(TdApi.GetChatAdministrators(chatId)).administrators.take(MAX_ADMINS)
        return admins.mapNotNull { admin ->
            val user = synchronized(lock) { users[admin.userId] } ?: fetchUser(admin.userId) ?: return@mapNotNull null
            ProfileAdmin(
                userId = admin.userId, name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
                isOwner = admin.isOwner, customTitle = admin.customTitle.orEmpty()
            )
        }
    }
    /** Страница участников для экрана списка; у обычной группы TDLib отдаёт список целиком, поэтому страница режется локально. */
    suspend fun loadMembers(chatId: Long, offset: Int, limit: Int): ChatMembersPage = withTimeout(25_000) {
        val (total, raw) = fetchMembers(chatId, offset, limit)
        // Незнакомых пользователей догружаем параллельно: страница до 50 записей не должна тянуть их по одной.
        val missing = raw.mapNotNull { (it.memberId as? TdApi.MessageSenderUser)?.userId }.distinct()
            .filter { id -> synchronized(lock) { users[id] } == null }
        coroutineScope { missing.map { id -> async { fetchUser(id) } }.awaitAll() }
        // Звания администраторов TDLib отдаёт отдельным списком: запрашиваем его, только если в странице есть владелец или админ без тега.
        val titles = if (raw.any { it.tag.isNullOrBlank() && it.status.isStaff() }) adminTitles(chatId) else emptyMap()
        ChatMembersPage(total, raw.mapNotNull { member ->
            val id = (member.memberId as? TdApi.MessageSenderUser)?.userId ?: return@mapNotNull null
            val user = synchronized(lock) { users[id] } ?: return@mapNotNull null
            val isBot = user.type is TdApi.UserTypeBot
            ProfileMember(
                userId = id,
                name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
                role = when (member.status) {
                    is TdApi.ChatMemberStatusCreator -> ProfileMemberRole.Owner
                    is TdApi.ChatMemberStatusAdministrator -> ProfileMemberRole.Admin
                    else -> ProfileMemberRole.Member
                },
                // TDLib может присылать ботам статус «в сети», но в списке их показываем как ботов.
                online = !isBot && (user.status as? TdApi.UserStatusOnline)?.let { it.expires > System.currentTimeMillis() / 1000 } == true,
                isBot = isBot,
                tag = member.tag.orEmpty().ifBlank { titles[id].orEmpty() },
                photo = user.profilePhoto?.let { photo(it.small, it.big) },
                avatarColorArgb = id.stableColor()
            )
        }, fetched = raw.size)
    }
    /** Звания администраторов по id. Сбой не срывает страницу: тогда у администратора останется роль. */
    private suspend fun adminTitles(chatId: Long): Map<Long, String> = try {
        request(TdApi.GetChatAdministrators(chatId)).administrators
            .filter { !it.customTitle.isNullOrBlank() }
            .associate { it.userId to it.customTitle.orEmpty() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        emptyMap()
    }
    private suspend fun fetchMembers(chatId: Long, offset: Int, limit: Int): Pair<Int, List<TdApi.ChatMember>> {
        return when (val type = synchronized(lock) { chats[chatId]?.type }) {
            is TdApi.ChatTypeSupergroup -> request(TdApi.GetSupergroupMembers(type.supergroupId, TdApi.SupergroupMembersFilterRecent(), offset, limit))
                .let { it.totalCount to it.members.toList() }
            is TdApi.ChatTypeBasicGroup -> {
                val full = synchronized(lock) { groupFull[type.basicGroupId] }
                    ?: request(TdApi.GetBasicGroupFullInfo(type.basicGroupId)).also { loaded -> synchronized(lock) { groupFull[type.basicGroupId] = loaded } }
                val all = full.members.toList()
                all.size to all.drop(offset).take(limit)
            }
            else -> throw UserMessageException(UiText.Res(R.string.profile_members_load_failed))
        }
    }
    private suspend fun fetchUser(id: Long): TdApi.User? = try {
        request(TdApi.GetUser(id)).also { user -> synchronized(lock) { users[id] = user } }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null
    }
    private suspend fun loadUser(id: Long) {
        val user = request(TdApi.GetUser(id))
        synchronized(lock) { users[id] = user; publish() }
        val full = request(TdApi.GetUserFullInfo(id))
        synchronized(lock) { userFull[id] = full }
    }

    suspend fun edit(edit: ProfileEdit): Result<Unit> = runCatching {
        val current = me.value.info ?: throw UserMessageException(UiText.Res(R.string.err_profile_not_loaded))
        if (edit.firstName.trim().isEmpty()) throw UserMessageException(UiText.Res(R.string.err_name_empty))
        if (edit.firstName.length > 64 || edit.lastName.length > 64) throw UserMessageException(UiText.Res(R.string.err_name_too_long, listOf(64)))
        if (edit.bio.codePointCount(0, edit.bio.length) > bioLimit.value) throw UserMessageException(UiText.Res(R.string.err_bio_too_long, listOf(bioLimit.value)))
        val username = edit.username.trim().removePrefix("@")
        if (!(username.isBlank() || username.matches(Regex("[A-Za-z][A-Za-z0-9_]{4,31}")) || username == current.username)) throw UserMessageException(UiText.Res(R.string.err_username_format))
        // Each accepted change is immediately published by TDLib. Refresh even after a partial failure.
        try {
            withTimeout(30_000) {
                if (username != current.username) request(TdApi.SetUsername(username))
                if (edit.firstName.trim() != current.firstName || edit.lastName.trim() != current.lastName) request(TdApi.SetName(edit.firstName.trim(), edit.lastName.trim()))
                if (edit.bio != current.bio) request(TdApi.SetBio(edit.bio))
            }
        } finally { refreshMe() }
    }.mapProfileError()

    suspend fun setPhoto(media: OutgoingMedia): Result<Unit> = runCatching {
        try { withTimeout(60_000) { request(TdApi.SetProfilePhoto(TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(media.path)), false)) } }
        finally { refreshMe() }
        Unit
    }.mapProfileError()

    private fun publish() {
        users[myId]?.let { _me.value = _me.value.copy(info = userInfo(it)) }
        details.forEach { (id, state) -> state.value = state.value.copy(info = chatInfo(id)) }
    }
    private fun updateTyping(update: TdApi.UpdateChatAction) {
        val sender = update.senderId
        // TDLib не присылает собственный набор. Апдейты не отбрасываем, пока myId не загружен: иначе при сбое GetMe чужой набор пропал бы.
        if (sender is TdApi.MessageSenderUser && sender.userId == myId) return
        // Темы форума не различаем: строка набора общая для всего чата.
        val key = when (sender) {
            is TdApi.MessageSenderUser -> "u${sender.userId}"
            is TdApi.MessageSenderChat -> "c${sender.chatId}"
            else -> return
        }
        val typists = typingBy.getOrPut(update.chatId) { mutableMapOf() }
        if (update.action is TdApi.ChatActionTyping) {
            val typist = Typist(sender)
            // put по существующему ключу сохраняет место в порядке имён, поэтому повторный набор не переставляет строку.
            typists.put(key, typist)?.expiry?.cancel()
            typist.expiry = scope.launch {
                delay(TYPING_TIMEOUT_MS)
                synchronized(lock) {
                    val current = typingBy[update.chatId]
                    if (current != null && current[key] === typist) current.remove(key)
                    publishTyping(update.chatId)
                }
            }
        } else {
            typists.remove(key)?.expiry?.cancel()
        }
        publishTyping(update.chatId)
    }
    private fun publishTyping(chatId: Long) {
        val typists = typingBy[chatId]
        if (typists != null && typists.isEmpty()) typingBy.remove(chatId)
        typingFlow(chatId).value = typists?.values?.map { typingName(it.sender) }.orEmpty()
    }
    private fun typingName(sender: TdApi.MessageSender): String {
        val name = when (sender) {
            is TdApi.MessageSenderUser -> users[sender.userId]?.firstName
            is TdApi.MessageSenderChat -> chats[sender.chatId]?.title
            else -> null
        }
        return name?.takeIf { it.isNotBlank() }.orEmpty()
    }
    private fun userInfo(user: TdApi.User): ProfileInfo {
        val full = userFull[user.id]
        return ProfileInfo(
            id = user.id, title = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
            kind = if (user.type is TdApi.UserTypeBot) ProfileKind.Bot else ProfileKind.Person,
            photo = user.profilePhoto?.let { photo(it.small, it.big) }, firstName = user.firstName.orEmpty(), lastName = user.lastName.orEmpty(),
            username = user.usernames?.editableUsername.orEmpty(), usernames = user.usernames?.activeUsernames.orEmpty().toList(),
            phone = user.phoneNumber.orEmpty(), bio = full?.bio?.text.orEmpty(), status = status(user),
            birthday = full?.birthdate?.let { ProfileBirthday(it.day, it.month, it.year.takeIf { year -> year > 0 }) },
            address = full?.businessInfo?.location?.address.orEmpty(),
            commonGroupCount = full?.groupInCommonCount?.takeIf { it > 0 && user.id != myId },
            isSelf = user.id == myId, isPremium = user.isPremium, isVerified = user.verificationStatus?.isVerified == true
        )
    }
    private fun chatInfo(id: Long): ProfileInfo? = chatProfile(id)?.copy(
        sharedCounts = sharedCounts[id], administrators = chatAdmins[id].orEmpty(), memberPreview = chatMemberPreview[id].orEmpty()
    )
    private fun chatProfile(id: Long): ProfileInfo? {
        val chat = chats[id] ?: return null
        val base = ProfileInfo(id, chat.title, photo = photo(chat.photo), onlineMemberCount = onlineCounts[id]?.takeIf { it > 0 })
        return when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> users[type.userId]?.let { userInfo(it).copy(id = id, title = chat.title, photo = photo(chat.photo) ?: it.profilePhoto?.let { p -> photo(p.small, p.big) }) } ?: base
            is TdApi.ChatTypeSecret -> users[type.userId]?.let { userInfo(it).copy(id = id, title = chat.title, kind = ProfileKind.Secret) } ?: base.copy(kind = ProfileKind.Secret)
            is TdApi.ChatTypeBasicGroup -> {
                val full = groupFull[type.basicGroupId]
                base.copy(kind = ProfileKind.Group, bio = full?.description.orEmpty(), memberCount = groups[type.basicGroupId]?.memberCount, canListMembers = true,
                    administratorCount = full?.members?.count { it.status is TdApi.ChatMemberStatusCreator || it.status is TdApi.ChatMemberStatusAdministrator }?.takeIf { it > 0 },
                    inviteLink = full?.inviteLink?.inviteLink.orEmpty())
            }
            is TdApi.ChatTypeSupergroup -> {
                val group = supers[type.supergroupId]
                val full = superFull[type.supergroupId]
                base.copy(kind = if (type.isChannel) ProfileKind.Channel else ProfileKind.Group,
                    bio = full?.description.orEmpty(), memberCount = full?.memberCount?.takeIf { it > 0 } ?: group?.memberCount,
                    administratorCount = full?.administratorCount?.takeIf { it > 0 }, inviteLink = full?.inviteLink?.inviteLink.orEmpty(),
                    canListMembers = full?.canGetMembers == true,
                    usernames = group?.usernames?.activeUsernames.orEmpty().toList(), isVerified = group?.verificationStatus?.isVerified == true)
            }
            else -> base
        }
    }
    /** Каждый тип считается отдельно: сбой одного запроса не скрывает остальные строки. */
    private suspend fun loadSharedCounts(chatId: Long): SharedCounts = SharedCounts(
        media = sharedCount(chatId, TdApi.SearchMessagesFilterPhotoAndVideo()),
        files = sharedCount(chatId, TdApi.SearchMessagesFilterDocument()),
        links = sharedCount(chatId, TdApi.SearchMessagesFilterUrl()),
        voice = sharedCount(chatId, TdApi.SearchMessagesFilterVoiceNote())
    )
    private suspend fun sharedCount(chatId: Long, filter: TdApi.SearchMessagesFilter): Int? = try {
        request(TdApi.GetChatMessageCount(chatId, null, filter, false)).count
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null
    }
    private fun status(user: TdApi.User): ProfilePresence {
        if (user.type is TdApi.UserTypeBot) return ProfilePresence.Bot
        if (user.type is TdApi.UserTypeDeleted) return ProfilePresence.DeletedAccount
        return when (val status = user.status) {
            is TdApi.UserStatusOnline -> if (status.expires > System.currentTimeMillis() / 1000) ProfilePresence.Online else ProfilePresence.Recently
            is TdApi.UserStatusOffline -> if (status.wasOnline > 0) ProfilePresence.LastSeen(status.wasOnline * 1000L) else ProfilePresence.LongAgo
            is TdApi.UserStatusRecently -> ProfilePresence.Recently
            is TdApi.UserStatusLastWeek -> ProfilePresence.LastWeek
            is TdApi.UserStatusLastMonth -> ProfilePresence.LastMonth
            else -> ProfilePresence.LongAgo
        }
    }
    private suspend fun <T : TdApi.Object> request(function: TdApi.Function<T>): T = suspendCancellableCoroutine { continuation ->
        client().send(function, Client.ResultHandler { result ->
            if (continuation.isActive) {
                if (result is TdApi.Error) continuation.resumeWith(Result.failure(IllegalStateException(result.message)))
                else { @Suppress("UNCHECKED_CAST") continuation.resume(result as T) }
            }
        })
    }
}

private const val TYPING_TIMEOUT_MS = 6_000L
private const val MAX_ADMINS = 20
private const val PREVIEW_MEMBERS = 10

private class Typist(val sender: TdApi.MessageSender) {
    /** Срок набора: новое действие или отмена отменяют прежний таймер. */
    var expiry: Job? = null
}

private fun TdApi.ChatMemberStatus.isStaff(): Boolean = this is TdApi.ChatMemberStatusCreator || this is TdApi.ChatMemberStatusAdministrator

private fun Throwable.profileError(): UiText = when {
    this is UserMessageException -> text
    this is TimeoutCancellationException -> UiText.Res(R.string.err_profile_timeout)
    message?.contains("USERNAME_OCCUPIED") == true -> UiText.Res(R.string.err_username_taken)
    message?.contains("USERNAME_INVALID") == true -> UiText.Res(R.string.err_username_invalid)
    message?.contains("USERNAME_PURCHASE_AVAILABLE") == true -> UiText.Res(R.string.err_username_purchase)
    message?.contains("FLOOD_WAIT") == true || message?.contains("Too Many Requests") == true -> UiText.Res(R.string.err_profile_flood_wait)
    else -> UiText.Res(R.string.err_profile_load_failed)
}
private fun Result<Unit>.mapProfileError(): Result<Unit> = fold({ Result.success(Unit) }, { Result.failure(UserMessageException(it.profileError(), it)) })
