package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.R
import org.drinkless.tdlib.TdApi

/**
 * Converts TDLib service content to text for both the chat timeline and chat-list preview.
 * Unsupported regular content is deliberately not identified as a service event.
 */
/** Resolve the affected member, not the admin who performed the action. */
internal fun serviceAffectedUserId(content: TdApi.MessageContent, senderId: TdApi.MessageSender?): Long {
    val actor = (senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
    return when (content) {
        is TdApi.MessageChatJoinByLink, is TdApi.MessageChatJoinByRequest -> actor
        is TdApi.MessageChatAddMembers -> content.memberUserIds.singleOrNull() ?: 0L
        is TdApi.MessageChatDeleteMember -> content.userId
        else -> 0L
    }
}

internal data class TdServiceEvent(val text: String, val userId: Long = 0L)

/** The event actor or affected member is preserved separately so the UI can open their profile. */
internal class TdServiceMessageMapper(private val context: Context) {
    fun event(message: TdApi.Message, nameForUser: (Long) -> String): TdServiceEvent? {
        val base = text(message.content) ?: return null
        val userId = serviceAffectedUserId(message.content, message.senderId)
        if (userId <= 0L) return TdServiceEvent(base)
        val name = nameForUser(userId).ifBlank {
            context.getString(R.string.service_unknown_member, userId.toString())
        }
        val label = when (message.content) {
            is TdApi.MessageChatJoinByLink -> context.getString(R.string.service_join_link_named, name)
            is TdApi.MessageChatJoinByRequest -> context.getString(R.string.service_join_request_named, name)
            is TdApi.MessageChatAddMembers -> context.getString(R.string.service_members_added_named, name)
            is TdApi.MessageChatDeleteMember -> context.getString(R.string.service_member_removed_named, name)
            else -> base
        }
        return TdServiceEvent(label, userId)
    }

    fun text(content: TdApi.MessageContent): String? = when (content) {
        is TdApi.MessageBasicGroupChatCreate -> context.getString(R.string.service_group_created)
        is TdApi.MessageSupergroupChatCreate -> context.getString(R.string.service_supergroup_created)
        is TdApi.MessageChatChangeTitle -> context.getString(R.string.service_title_changed, content.title)
        is TdApi.MessageChatChangePhoto -> context.getString(R.string.service_photo_changed)
        is TdApi.MessageChatDeletePhoto -> context.getString(R.string.service_photo_deleted)
        is TdApi.MessageChatAddMembers -> context.getString(R.string.service_members_added)
        is TdApi.MessageChatDeleteMember -> context.getString(R.string.service_member_removed)
        is TdApi.MessageChatJoinByLink -> context.getString(R.string.service_join_link)
        is TdApi.MessageChatJoinByRequest -> context.getString(R.string.service_join_request)
        is TdApi.MessagePinMessage -> context.getString(R.string.service_message_pinned)
        is TdApi.MessageForumTopicCreated -> context.getString(R.string.service_topic_created, content.name)
        is TdApi.MessageForumTopicEdited -> context.getString(R.string.service_topic_edited)
        is TdApi.MessageForumTopicIsClosedToggled -> context.getString(R.string.service_topic_closed_changed)
        is TdApi.MessageForumTopicIsHiddenToggled -> context.getString(R.string.service_topic_hidden_changed)
        is TdApi.MessageVideoChatStarted -> context.getString(R.string.service_videochat_started)
        is TdApi.MessageVideoChatEnded -> context.getString(R.string.service_videochat_ended)
        is TdApi.MessageChatSetMessageAutoDeleteTime -> context.getString(R.string.service_autodelete_changed)
        is TdApi.MessageChatSetBackground -> context.getString(R.string.service_background_changed)
        else -> null
    }
}
