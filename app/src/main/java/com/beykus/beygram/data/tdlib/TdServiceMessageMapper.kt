package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.R
import org.drinkless.tdlib.TdApi

/**
 * Converts TDLib service content to text for both the chat timeline and chat-list preview.
 * Unsupported regular content is deliberately not identified as a service event.
 */
internal class TdServiceMessageMapper(private val context: Context) {
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
