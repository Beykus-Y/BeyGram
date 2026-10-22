package com.beykus.beygram.data.tdlib

import android.content.Context
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.domain.model.CallStatus
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
internal class TdServiceMessageMapper(private val baseContext: Context) {
    // The application context has no in-app locale below API 33; follow the selected language.
    @Volatile private var localized: Pair<String, Context>? = null
    private val context: Context
        get() {
            val language = AppLocale.selected(baseContext)
            localized?.takeIf { it.first == language }?.let { return it.second }
            return AppLocale.context(baseContext).also { localized = language to it }
        }

    fun event(message: TdApi.Message, nameForUser: (Long) -> String): TdServiceEvent? {
        if (message.content is TdApi.MessageCall) {
            return TdServiceEvent(callText(message.content as TdApi.MessageCall, message.isOutgoing))
        }
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

    internal fun callText(call: TdApi.MessageCall, isOutgoing: Boolean): String {
        val direction = context.getString(if (isOutgoing) R.string.calls_direction_outgoing else R.string.calls_direction_incoming)
        val kind = context.getString(if (call.isVideo) R.string.calls_kind_video else R.string.calls_kind_call)
        return when (call.discardReason.toCallStatus(call.duration, isOutgoing)) {
            CallStatus.Answered -> context.getString(R.string.service_call_answered, direction, kind, callEventDuration(call.duration))
            CallStatus.Missed -> context.getString(R.string.service_call_missed, kind)
            CallStatus.Declined -> context.getString(R.string.service_call_declined, direction, kind)
            CallStatus.NoAnswer -> context.getString(R.string.service_call_no_answer, direction, kind)
        }
    }

    fun text(content: TdApi.MessageContent): String? = when (content) {
        is TdApi.MessageCall -> context.getString(if (content.isVideo) R.string.calls_kind_video else R.string.calls_kind_call)
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

/** Duration of a call, independent of the device clock. */
internal fun callEventDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val secs = (total % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$secs" else "$minutes:$secs"
}
