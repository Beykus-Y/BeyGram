package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.text.DateFormat
import java.util.Date
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
    private fun photo(small: TdApi.File, big: TdApi.File): AvatarPhoto {
        files.register(small.toMediaFileState()); files.register(big.toMediaFileState())
        return AvatarPhoto(small.id, big.id)
    }
    fun putChat(chat: TdApi.Chat) = synchronized(lock) { chats[chat.id] = chat; publish() }
    fun observe(chatId: Long): StateFlow<ProfileState> = synchronized(lock) {
        details.getOrPut(chatId) { MutableStateFlow(ProfileState(info = chatInfo(chatId))) }.asStateFlow()
    }
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
        }
    }
    private suspend fun loadUser(id: Long) {
        val user = request(TdApi.GetUser(id))
        synchronized(lock) { users[id] = user; publish() }
        val full = request(TdApi.GetUserFullInfo(id))
        synchronized(lock) { userFull[id] = full }
    }

    suspend fun edit(edit: ProfileEdit): Result<Unit> = runCatching {
        val current = me.value.info ?: error("Профиль ещё не загружен")
        require(edit.firstName.trim().isNotEmpty()) { "Введите имя" }
        require(edit.firstName.length <= 64 && edit.lastName.length <= 64) { "Имя и фамилия — не более 64 символов" }
        require(edit.bio.codePointCount(0, edit.bio.length) <= bioLimit.value) { "Описание — не более ${bioLimit.value} символов" }
        val username = edit.username.trim().removePrefix("@")
        require(username.isBlank() || username.matches(Regex("[A-Za-z][A-Za-z0-9_]{4,31}")) || username == current.username) { "Имя пользователя: 5–32 латинских символа, цифры и _, начинается с буквы" }
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
    private fun userInfo(user: TdApi.User): ProfileInfo = ProfileInfo(
        id = user.id, title = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Пользователь" },
        kind = if (user.type is TdApi.UserTypeBot) ProfileKind.Bot else ProfileKind.Person,
        photo = user.profilePhoto?.let { photo(it.small, it.big) }, firstName = user.firstName.orEmpty(), lastName = user.lastName.orEmpty(),
        username = user.usernames?.editableUsername.orEmpty(), usernames = user.usernames?.activeUsernames.orEmpty().toList(),
        phone = user.phoneNumber.orEmpty(), bio = userFull[user.id]?.bio?.text.orEmpty(), status = status(user),
        isSelf = user.id == myId, isPremium = user.isPremium, isVerified = user.verificationStatus?.isVerified == true
    )
    private fun chatInfo(id: Long): ProfileInfo? {
        val chat = chats[id] ?: return null
        val base = ProfileInfo(id, chat.title, photo = photo(chat.photo))
        return when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> users[type.userId]?.let { userInfo(it).copy(id = id, title = chat.title, photo = photo(chat.photo) ?: it.profilePhoto?.let { p -> photo(p.small, p.big) }) } ?: base
            is TdApi.ChatTypeSecret -> users[type.userId]?.let { userInfo(it).copy(id = id, title = chat.title, kind = ProfileKind.Secret) } ?: base.copy(kind = ProfileKind.Secret)
            is TdApi.ChatTypeBasicGroup -> base.copy(kind = ProfileKind.Group, bio = groupFull[type.basicGroupId]?.description.orEmpty(), memberCount = groups[type.basicGroupId]?.memberCount)
            is TdApi.ChatTypeSupergroup -> {
                val group = supers[type.supergroupId]
                base.copy(kind = if (type.isChannel) ProfileKind.Channel else ProfileKind.Group,
                    bio = superFull[type.supergroupId]?.description.orEmpty(), memberCount = superFull[type.supergroupId]?.memberCount?.takeIf { it > 0 } ?: group?.memberCount,
                    usernames = group?.usernames?.activeUsernames.orEmpty().toList(), isVerified = group?.verificationStatus?.isVerified == true)
            }
            else -> base
        }
    }
    private fun status(user: TdApi.User): String {
        if (user.type is TdApi.UserTypeBot) return "Бот"
        if (user.type is TdApi.UserTypeDeleted) return "Удалённый аккаунт"
        return when (val status = user.status) {
            is TdApi.UserStatusOnline -> if (status.expires > System.currentTimeMillis() / 1000) "В сети" else "Был(а) недавно"
            is TdApi.UserStatusOffline -> if (status.wasOnline > 0) "Был(а) ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(status.wasOnline * 1000L))}" else "Был(а) давно"
            is TdApi.UserStatusRecently -> "Был(а) недавно"
            is TdApi.UserStatusLastWeek -> "Был(а) на этой неделе"
            is TdApi.UserStatusLastMonth -> "Был(а) в этом месяце"
            else -> "Был(а) давно"
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

private fun Throwable.profileError(): String = when {
    this is TimeoutCancellationException -> "Сервер не ответил вовремя. Обновите сведения и проверьте, сохранились ли изменения."
    message?.contains("USERNAME_OCCUPIED") == true -> "Это имя пользователя уже занято"
    message?.contains("USERNAME_INVALID") == true -> "Недопустимое имя пользователя"
    message?.contains("USERNAME_PURCHASE_AVAILABLE") == true -> "Это имя пользователя доступно только для покупки"
    message?.contains("FLOOD_WAIT") == true || message?.contains("Too Many Requests") == true -> "Слишком много изменений. Попробуйте позже"
    else -> message ?: "Не удалось загрузить сведения"
}
private fun Result<Unit>.mapProfileError(): Result<Unit> = fold({ Result.success(Unit) }, { Result.failure(IllegalStateException(it.profileError())) })
