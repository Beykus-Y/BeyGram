package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi

/**
 * Whether the signed-in user may write to this chat, using the rules Telegram clients follow: a channel takes posts only
 * from its owner and administrators with the posting right, a group follows its default permissions unless the user is
 * restricted. An unknown status keeps the composer, so a group update that has not arrived yet never hides it.
 */
internal fun TdApi.Chat.canPostMessages(status: TdApi.ChatMemberStatus?): Boolean {
    val isChannel = (type as? TdApi.ChatTypeSupergroup)?.isChannel == true
    return when (status) {
        null, is TdApi.ChatMemberStatusCreator -> true
        is TdApi.ChatMemberStatusAdministrator -> status.rights?.canPostMessages == true || !isChannel
        is TdApi.ChatMemberStatusMember -> !isChannel && permissions?.canSendBasicMessages == true
        is TdApi.ChatMemberStatusRestricted -> !isChannel && status.isMember && status.permissions?.canSendBasicMessages == true
        else -> false
    }
}

/**
 * Polls need the separate canSendPolls right in groups. Channels are governed by the posting right instead, because
 * their administrator rights do not repeat per-content permissions; TDLib additionally rejects non-anonymous channel
 * polls at send time. Private user chats and secret chats are handled by the caller, not by this group-status helper.
 */
internal fun TdApi.Chat.canSendPolls(status: TdApi.ChatMemberStatus?): Boolean {
    val isChannel = (type as? TdApi.ChatTypeSupergroup)?.isChannel == true
    return when (status) {
        null, is TdApi.ChatMemberStatusCreator -> true
        is TdApi.ChatMemberStatusAdministrator -> status.rights?.canPostMessages == true || !isChannel
        is TdApi.ChatMemberStatusMember -> !isChannel && permissions?.canSendPolls == true
        is TdApi.ChatMemberStatusRestricted -> !isChannel && status.isMember && status.permissions?.canSendPolls == true
        else -> false
    }
}
