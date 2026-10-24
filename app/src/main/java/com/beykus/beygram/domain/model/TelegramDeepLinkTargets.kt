package com.beykus.beygram.domain.model

/** The chat behind an invite link, fetched without joining it. */
data class ChatInvitePreview(
    val title: String,
    val memberCount: Int,
    val createsJoinRequest: Boolean,
    val isPublic: Boolean
)

/** Result of an explicitly confirmed invite join. */
sealed interface InviteJoinOutcome {
    data class Joined(val chatId: Long) : InviteJoinOutcome
    data object RequestSent : InviteJoinOutcome
}

/** Chat and message resolved from a public or private t.me message link. */
data class MessageLinkTarget(
    val chatId: Long,
    val messageId: Long,
    val forumTopicId: Long = 0L
)
