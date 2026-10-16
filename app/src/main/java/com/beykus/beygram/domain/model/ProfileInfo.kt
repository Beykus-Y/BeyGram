package com.beykus.beygram.domain.model

data class AvatarPhoto(val smallFileId: Int, val bigFileId: Int)

enum class ProfileKind { Person, Bot, Group, Channel, Secret }

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
    val status: String = "",
    val memberCount: Int? = null,
    /** Участники, онлайн прямо сейчас; TDLib присылает число только для открытого чата. */
    val onlineMemberCount: Int? = null,
    val isSelf: Boolean = false,
    val isPremium: Boolean = false,
    val isVerified: Boolean = false
) {
    val initials: String get() = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
}

data class ProfileState(val info: ProfileInfo? = null, val isLoading: Boolean = false, val error: String? = null)

data class ProfileEdit(val firstName: String, val lastName: String, val username: String, val bio: String)
