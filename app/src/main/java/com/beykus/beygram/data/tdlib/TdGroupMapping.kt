package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GroupAdminRights
import com.beykus.beygram.domain.model.GroupAdministratorRights
import com.beykus.beygram.domain.model.GroupInviteLink
import com.beykus.beygram.domain.model.GroupMemberStatus
import com.beykus.beygram.domain.model.ManagedChatKind
import com.beykus.beygram.domain.model.groupAdminRights
import org.drinkless.tdlib.TdApi

/** Статус участника из TDLib; [TdApi.ChatMemberStatusLeft] и неизвестные значения считаются вышедшими. */
internal fun TdApi.ChatMemberStatus.toGroupMemberStatus(): GroupMemberStatus = when (this) {
    is TdApi.ChatMemberStatusCreator -> GroupMemberStatus.Owner
    is TdApi.ChatMemberStatusAdministrator -> GroupMemberStatus.Administrator
    is TdApi.ChatMemberStatusMember -> GroupMemberStatus.Member
    is TdApi.ChatMemberStatusRestricted -> GroupMemberStatus.Restricted
    is TdApi.ChatMemberStatusBanned -> GroupMemberStatus.Banned
    else -> GroupMemberStatus.Left
}

internal fun TdApi.ChatAdministratorRights.toGroupAdministratorRights(): GroupAdministratorRights = GroupAdministratorRights(
    canManageChat = canManageChat,
    canChangeInfo = canChangeInfo,
    canPostMessages = canPostMessages,
    canEditMessages = canEditMessages,
    canDeleteMessages = canDeleteMessages,
    canInviteUsers = canInviteUsers,
    canRestrictMembers = canRestrictMembers,
    canPinMessages = canPinMessages,
    canManageTopics = canManageTopics,
    canPromoteMembers = canPromoteMembers,
    canManageVideoChats = canManageVideoChats,
    canPostStories = canPostStories,
    canEditStories = canEditStories,
    canDeleteStories = canDeleteStories,
    canManageDirectMessages = canManageDirectMessages,
    canManageTags = canManageTags,
    isAnonymous = isAnonymous
)

internal fun GroupAdministratorRights.toTdAdministratorRights(): TdApi.ChatAdministratorRights = TdApi.ChatAdministratorRights(
    canManageChat,
    canChangeInfo,
    canPostMessages,
    canEditMessages,
    canDeleteMessages,
    canInviteUsers,
    canRestrictMembers,
    canPinMessages,
    canManageTopics,
    canPromoteMembers,
    canManageVideoChats,
    canPostStories,
    canEditStories,
    canDeleteStories,
    canManageDirectMessages,
    canManageTags,
    isAnonymous
)

internal fun TdApi.ChatMemberStatus.administratorRights(): GroupAdministratorRights? =
    (this as? TdApi.ChatMemberStatusAdministrator)?.rights?.toGroupAdministratorRights()

/** Административные возможности текущего пользователя по его статусу в чате. */
internal fun TdApi.ChatMemberStatus.toGroupAdminRights(kind: ManagedChatKind): GroupAdminRights =
    groupAdminRights(kind, toGroupMemberStatus(), administratorRights())

internal fun TdApi.ChatInviteLink.toGroupInviteLink(): GroupInviteLink = GroupInviteLink(
    link = inviteLink,
    name = name.orEmpty(),
    creatorUserId = creatorUserId,
    createdSeconds = date,
    expiresAtSeconds = expirationDate,
    memberLimit = memberLimit,
    memberCount = memberCount,
    pendingJoinRequestCount = pendingJoinRequestCount,
    requiresApproval = createsJoinRequest,
    isPrimary = isPrimary,
    isRevoked = isRevoked,
    subscriptionPeriodSeconds = subscriptionPricing?.period ?: 0,
    subscriptionStarCount = subscriptionPricing?.starCount ?: 0L
)
