package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

data class AvatarPhoto(val smallFileId: Int, val bigFileId: Int, val photoId: Long = 0L)

enum class ProfileKind { Person, Bot, Group, Channel, Secret }

/** Присутствие пользователя из TDLib; текст подбирает интерфейс по ключам. */
sealed interface ProfilePresence {
    data object Bot : ProfilePresence
    data object DeletedAccount : ProfilePresence
    data object Online : ProfilePresence
    /** Был(а) в сети в [wasOnlineMillis]; текст строит интерфейс по настройке и текущему времени, поэтому здесь только момент. */
    data class LastSeen(val wasOnlineMillis: Long) : ProfilePresence
    data object Recently : ProfilePresence
    data object LastWeek : ProfilePresence
    data object LastMonth : ProfilePresence
    data object LongAgo : ProfilePresence
}

/** День рождения из TDLib; год неизвестен, если аккаунт его не указал. */
data class ProfileBirthday(val day: Int, val month: Int, val year: Int?)

enum class ProfileMemberRole { Owner, Admin, Member }

/**
 * Участник группы или канала. [tag] — тег участника; у владельца и администратора без тега здесь их звание.
 * [online] известен только для тех, кого TDLib уже прислал; у ботов он всегда false.
 */
data class ProfileMember(
    val userId: Long,
    val name: String,
    val role: ProfileMemberRole,
    val online: Boolean,
    val isBot: Boolean,
    val tag: String,
    val photo: AvatarPhoto?,
    val avatarColorArgb: Long
) {
    val initials: String get() = initialsOf(name)
}

/**
 * Страница участников. [fetched] — сколько записей TDLib вернул до фильтрации: именно он двигает смещение следующей страницы,
 * потому что часть записей (чаты, недоступные пользователи) в [members] не попадает.
 */
data class ChatMembersPage(val total: Int, val members: List<ProfileMember>, val fetched: Int)

/** Владелец или администратор группы/канала; роль берётся из статуса участника в TDLib. */
data class ProfileAdmin(val userId: Long, val name: String, val isOwner: Boolean, val customTitle: String)

/** Число сообщений с медиа, файлами, ссылками, голосовыми и музыкой в чате; null, если TDLib не ответил по этому типу. */
data class SharedCounts(val media: Int?, val files: Int?, val links: Int?, val voice: Int?, val music: Int? = null)

data class ProfileInfo(
    val id: Long,
    val title: String,
    val kind: ProfileKind = ProfileKind.Person,
    val photo: AvatarPhoto? = null,
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val usernames: List<String> = emptyList(),
    val phone: String = "",
    val bio: String = "",
    val birthday: ProfileBirthday? = null,
    /** Адрес бизнес-аккаунта из TDLib; у обычных аккаунтов пусто. */
    val address: String = "",
    /** Число общих групп с собеседником; null, если их нет или это свой профиль. */
    val commonGroupCount: Int? = null,
    val status: ProfilePresence? = null,
    val memberCount: Int? = null,
    /** Участники, онлайн прямо сейчас; TDLib присылает число только для открытого чата. */
    val onlineMemberCount: Int? = null,
    val administratorCount: Int? = null,
    /** Основная ссылка-приглашение; TDLib отдаёт её не всем, поэтому может быть пусто. */
    val inviteLink: String = "",
    val sharedCounts: SharedCounts? = null,
    val administrators: List<ProfileAdmin> = emptyList(),
    /** Первые участники для предпросмотра; полный список открывается отдельным экраном. */
    val memberPreview: List<ProfileMember> = emptyList(),
    /** TDLib разрешает получать список участников: у каналов это часто запрещено. */
    val canListMembers: Boolean = false,
    val isSelf: Boolean = false,
    val isPremium: Boolean = false,
    val isVerified: Boolean = false
) {
    val initials: String get() = initialsOf(title)
}

data class ProfileState(val info: ProfileInfo? = null, val isLoading: Boolean = false, val error: UiText? = null)

data class ProfileEdit(val firstName: String, val lastName: String, val username: String, val bio: String)

/** Инициалы для аватара без фото: первые буквы первых двух слов. Общие для контактов, звонков и профилей. */
internal fun initialsOf(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { it.first().uppercaseChar().toString() }
