package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

data class AvatarPhoto(val smallFileId: Int, val bigFileId: Int)

enum class ProfileKind { Person, Bot, Group, Channel, Secret }

/** Присутствие пользователя из TDLib; текст подбирает интерфейс по ключам. */
sealed interface ProfilePresence {
    data object Bot : ProfilePresence
    data object DeletedAccount : ProfilePresence
    data object Online : ProfilePresence
    /** Был(а) в сети в [at]; время уже отформатировано по локали. */
    data class LastSeen(val at: String) : ProfilePresence
    data object Recently : ProfilePresence
    data object LastWeek : ProfilePresence
    data object LastMonth : ProfilePresence
    data object LongAgo : ProfilePresence
}

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
    val status: ProfilePresence? = null,
    val memberCount: Int? = null,
    /** Участники, онлайн прямо сейчас; TDLib присылает число только для открытого чата. */
    val onlineMemberCount: Int? = null,
    val isSelf: Boolean = false,
    val isPremium: Boolean = false,
    val isVerified: Boolean = false
) {
    val initials: String get() = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
}

data class ProfileState(val info: ProfileInfo? = null, val isLoading: Boolean = false, val error: UiText? = null)

data class ProfileEdit(val firstName: String, val lastName: String, val username: String, val bio: String)
