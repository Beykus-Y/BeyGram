package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.CancellationException
import org.drinkless.tdlib.TdApi

/**
 * Управление группой и каналом поверх TDLib. Слой не держит состояния: каждое действие читает
 * актуальные права с сервера, а интерфейс получает неизменяемые снимки. Ошибки TDLib не
 * превращаются здесь в текст — это делает [groupResult] в репозитории.
 */
internal class TdGroupAdministration(
    private val requests: TdRequests,
    private val profiles: TdProfiles
) {
    suspend fun info(chatId: Long): GroupManagementInfo {
        val chat = requests(TdApi.GetChat(chatId))
        return when (val type = chat.type) {
            is TdApi.ChatTypeBasicGroup -> basicInfo(chat, type.basicGroupId)
            is TdApi.ChatTypeSupergroup -> superInfo(chat, type)
            else -> throw UserMessageException(UiText.Res(R.string.err_group_not_manageable))
        }
    }

    private suspend fun basicInfo(chat: TdApi.Chat, basicGroupId: Long): GroupManagementInfo {
        val group = requests(TdApi.GetBasicGroup(basicGroupId))
        val full = requests(TdApi.GetBasicGroupFullInfo(basicGroupId))
        return GroupManagementInfo(
            chatId = chat.id,
            kind = ManagedChatKind.BasicGroup,
            title = chat.title,
            description = full.description.orEmpty(),
            username = "",
            // Фото из полной информации приходит раньше, чем обновление чата.
            photo = profiles.avatar(full.photo) ?: profiles.photo(chat.photo),
            memberCount = group.memberCount,
            pendingJoinRequestCount = chat.pendingJoinRequests?.totalCount ?: 0,
            primaryInviteLink = full.inviteLink?.inviteLink.orEmpty(),
            rights = group.status.toGroupAdminRights(ManagedChatKind.BasicGroup)
        )
    }

    private suspend fun superInfo(chat: TdApi.Chat, type: TdApi.ChatTypeSupergroup): GroupManagementInfo {
        val kind = if (type.isChannel) ManagedChatKind.Channel else ManagedChatKind.Supergroup
        val group = requests(TdApi.GetSupergroup(type.supergroupId))
        val full = requests(TdApi.GetSupergroupFullInfo(type.supergroupId))
        return GroupManagementInfo(
            chatId = chat.id,
            kind = kind,
            title = chat.title,
            description = full.description.orEmpty(),
            username = group.usernames?.editableUsername.orEmpty(),
            photo = profiles.avatar(full.photo) ?: profiles.photo(chat.photo),
            slowModeSeconds = full.slowModeDelay,
            joinByRequest = group.joinByRequest,
            allHistoryAvailable = full.isAllHistoryAvailable,
            memberCount = full.memberCount.takeIf { it > 0 } ?: group.memberCount,
            pendingJoinRequestCount = chat.pendingJoinRequests?.totalCount ?: 0,
            primaryInviteLink = full.inviteLink?.inviteLink.orEmpty(),
            rights = group.status.toGroupAdminRights(kind)
        )
    }

    suspend fun members(chatId: Long, filter: GroupMemberFilter, query: String, offset: Int, limit: Int): GroupMemberPage {
        require(offset >= 0 && limit in 1..100)
        val search = query.trim()
        if (search.isNotEmpty()) {
            // Поиск TDLib не поддерживает страницы: он отдаёт лучшие совпадения одним списком.
            val found = requests(TdApi.SearchChatMembers(chatId, search, limit.coerceAtMost(50), filter.toChatMembersFilter()))
            return GroupMemberPage(found.totalCount, mapMembers(found.members.orEmpty()), found.members.orEmpty().size)
        }
        return when (val type = requests(TdApi.GetChat(chatId)).type) {
            is TdApi.ChatTypeSupergroup -> {
                val page = requests(TdApi.GetSupergroupMembers(type.supergroupId, filter.toSupergroupMembersFilter(), offset, limit))
                GroupMemberPage(page.totalCount, mapMembers(page.members.orEmpty()), page.members.orEmpty().size)
            }
            is TdApi.ChatTypeBasicGroup -> {
                // В обычной группе TDLib не отдаёт ни список заблокированных, ни ограниченных.
                if (filter == GroupMemberFilter.Banned) return GroupMemberPage(0, emptyList(), 0)
                val full = requests(TdApi.GetBasicGroupFullInfo(type.basicGroupId))
                val matching = full.members.orEmpty().filterNotNull()
                    .filter { it.status.toGroupMemberStatus().matches(filter) }
                GroupMemberPage(matching.size, mapMembers(matching.drop(offset).take(limit).toTypedArray()), matching.drop(offset).take(limit).size)
            }
            else -> throw UserMessageException(UiText.Res(R.string.err_group_not_manageable))
        }
    }

    private suspend fun mapMembers(raw: Array<out TdApi.ChatMember?>): List<GroupMemberEntry> {
        val members = raw.filterNotNull()
        val ids = members.mapNotNull { it.userId() }.distinct()
        val users = profiles.resolveUsers(ids)
        return members.mapNotNull { member ->
            val userId = member.userId() ?: return@mapNotNull null
            val user = users[userId] ?: return@mapNotNull null
            val status = member.status.toGroupMemberStatus()
            GroupMemberEntry(
                userId = userId,
                name = user.displayName(),
                status = status,
                isMember = member.isMember(),
                online = user.isOnline(),
                isBot = user.type is TdApi.UserTypeBot,
                tag = member.tag.orEmpty(),
                photo = user.profilePhoto?.let { profiles.photo(it.small, it.big) },
                avatarColorArgb = userId.stableColor(),
                isAnonymous = member.isAnonymous(),
                rights = member.status.administratorRights(),
                canBeEdited = (member.status as? TdApi.ChatMemberStatusAdministrator)?.canBeEdited == true,
                restrictedUntilSeconds = (member.status as? TdApi.ChatMemberStatusRestricted)?.restrictedUntilDate ?: 0,
                bannedUntilSeconds = (member.status as? TdApi.ChatMemberStatusBanned)?.bannedUntilDate ?: 0,
                isCurrentUser = profiles.isSelf(userId)
            )
        }
    }

    suspend fun addMembers(chatId: Long, userIds: List<Long>): GroupMemberAddition {
        val ids = userIds.distinct().filter { it > 0L }
        require(ids.isNotEmpty()) { "No users to add" }
        val isBasicGroup = requests(TdApi.GetChat(chatId)).type is TdApi.ChatTypeBasicGroup
        val failed = mutableListOf<TdApi.FailedToAddMember>()
        val added = mutableListOf<Long>()
        if (isBasicGroup) {
            // TDLib принимает AddChatMembers только с одним пользователем для basic group.
            for (id in ids) {
                val result = requests(TdApi.AddChatMember(chatId, id, 0))
                val failures = result.failedToAddMembers.orEmpty().filterNotNull()
                if (failures.isEmpty()) added += id else failed += failures
            }
        } else {
            val result = requests(TdApi.AddChatMembers(chatId, ids.toLongArray()))
            failed += result.failedToAddMembers.orEmpty().filterNotNull()
            added += ids.filterNot { id -> failed.any { it.userId == id } }
        }
        val failedIds = failed.map { it.userId }
        return GroupMemberAddition(
            addedUserIds = added,
            failedUserIds = failedIds,
            premiumRequiredUserIds = failed
                .filter { it.premiumWouldAllowInvite || it.premiumRequiredToSendMessages }
                .map { it.userId }
        )
    }

    suspend fun banMember(chatId: Long, userId: Long) {
        requests(TdApi.BanChatMember(chatId, TdApi.MessageSenderUser(userId), 0, false))
    }

    /** Снятие бана не возвращает участника: статус Left убирает ограничение, не приглашая его обратно. */
    suspend fun unbanMember(chatId: Long, userId: Long) {
        requests(TdApi.SetChatMemberStatus(chatId, TdApi.MessageSenderUser(userId), TdApi.ChatMemberStatusLeft()))
    }

    suspend fun restrictMember(chatId: Long, userId: Long) {
        requests(
            TdApi.SetChatMemberStatus(
                chatId,
                TdApi.MessageSenderUser(userId),
                TdApi.ChatMemberStatusRestricted(true, 0, readOnlyPermissions())
            )
        )
    }

    suspend fun unrestrictMember(chatId: Long, userId: Long, keepMembership: Boolean) {
        // Ограниченный, но уже вышедший пользователь должен остаться вне чата: Left снимает
        // ограничение, не добавляя его заново.
        val status = if (keepMembership) TdApi.ChatMemberStatusMember(0) else TdApi.ChatMemberStatusLeft()
        requests(TdApi.SetChatMemberStatus(chatId, TdApi.MessageSenderUser(userId), status))
    }

    suspend fun setAdministratorRights(chatId: Long, userId: Long, rights: GroupAdministratorRights) {
        requests(
            TdApi.SetChatMemberStatus(
                chatId,
                TdApi.MessageSenderUser(userId),
                TdApi.ChatMemberStatusAdministrator(false, rights.toTdAdministratorRights())
            )
        )
    }

    suspend fun demoteMember(chatId: Long, userId: Long) {
        requests(TdApi.SetChatMemberStatus(chatId, TdApi.MessageSenderUser(userId), TdApi.ChatMemberStatusMember(0)))
    }

    suspend fun inviteLinks(chatId: Long): List<GroupInviteLink> {
        // TDLib принимает только реальный id создателя; 0 не означает «все». Свои ссылки доступны
        // любому администратору с can_invite_users, чужие — только владельцу, поэтому список
        // создателей берём из owner-only счётчиков, а при отказе показываем хотя бы свои ссылки.
        val myId = profiles.myUserId()
        val creators = mutableListOf(myId)
        try {
            requests(TdApi.GetChatInviteLinkCounts(chatId)).inviteLinkCounts.orEmpty().filterNotNull()
                .mapNotNull { it.userId.takeIf { id -> id > 0L } }
                .forEach { if (it != myId) creators += it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Не-владельцу TDLib не отдаёт счётчики: остаются собственные ссылки.
        }
        val links = mutableListOf<TdApi.ChatInviteLink>()
        for (creatorId in creators.distinct()) {
            try {
                var offsetDate = 0
                var offsetLink = ""
                var pages = 0
                var fetched = 0
                while (pages++ < MAX_INVITE_PAGES) {
                    val page = requests(TdApi.GetChatInviteLinks(chatId, creatorId, false, offsetDate, offsetLink, PAGE_SIZE))
                    val batch = page.inviteLinks.orEmpty().filterNotNull()
                    links += batch
                    fetched += batch.size
                    if (batch.size < PAGE_SIZE || fetched >= page.totalCount) break
                    val last = batch.last()
                    // Защита от сервера, который повторяет ту же страницу.
                    if (last.date == offsetDate && last.inviteLink == offsetLink) break
                    offsetDate = last.date
                    offsetLink = last.inviteLink
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Чужая устаревшая запись не должна скрывать доступные ссылки; свои ошибки показываем.
                if (creatorId == myId) throw error
            }
        }
        return links.distinctBy { it.inviteLink }
            .map { link ->
                link.toGroupInviteLink().copy(creatorIsCurrentUser = profiles.isSelf(link.creatorUserId))
            }
            .sortedWith(compareByDescending<GroupInviteLink> { it.isPrimary }.thenByDescending { it.createdSeconds })
    }

    suspend fun createInviteLink(chatId: Long, draft: GroupInviteLinkDraft): GroupInviteLink {
        val normalized = requireValidInviteDraft(draft)
        val link = requests(
            TdApi.CreateChatInviteLink(
                chatId,
                normalized.name,
                normalized.expiresAtSeconds,
                normalized.memberLimit,
                normalized.requiresApproval
            )
        )
        return link.toGroupInviteLink()
    }

    suspend fun editInviteLink(chatId: Long, link: String, draft: GroupInviteLinkDraft): GroupInviteLink {
        // Платная подписка меняется отдельным TDLib-запросом и не принимает срок/лимит/заявки.
        if (requests(TdApi.GetChatInviteLink(chatId, link)).subscriptionPricing != null) {
            throw UserMessageException(UiText.Res(R.string.err_group_invite_subscription))
        }
        val normalized = requireValidInviteDraft(draft)
        val updated = requests(
            TdApi.EditChatInviteLink(
                chatId,
                link,
                normalized.name,
                normalized.expiresAtSeconds,
                normalized.memberLimit,
                normalized.requiresApproval
            )
        )
        return updated.toGroupInviteLink()
    }

    suspend fun revokeInviteLink(chatId: Long, link: String) {
        requests(TdApi.RevokeChatInviteLink(chatId, link))
    }

    suspend fun joinRequests(chatId: Long): List<GroupJoinRequest> {
        val requestsList = mutableListOf<TdApi.ChatJoinRequest>()
        var offset: TdApi.ChatJoinRequest? = null
        var total = 0
        var pages = 0
        while (pages++ < MAX_JOIN_REQUEST_PAGES) {
            val page = requests(TdApi.GetChatJoinRequests(chatId, "", "", offset, PAGE_SIZE))
            total = page.totalCount
            val batch = page.requests.orEmpty().filterNotNull()
            requestsList += batch
            if (batch.isEmpty() || requestsList.size >= total) break
            val last = batch.last()
            if (offset?.userId == last.userId && offset?.date == last.date) break
            offset = last
        }
        val users = profiles.resolveUsers(requestsList.map { it.userId }.distinct())
        return requestsList.distinctBy { it.userId }.map { request ->
            val user = users[request.userId]
            GroupJoinRequest(
                userId = request.userId,
                name = user?.displayName().orEmpty(),
                bio = request.bio.orEmpty(),
                dateSeconds = request.date,
                photo = user?.profilePhoto?.let { profiles.photo(it.small, it.big) },
                avatarColorArgb = request.userId.stableColor()
            )
        }
    }

    suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean) {
        requests(TdApi.ProcessChatJoinRequest(chatId, userId, approve))
    }

    suspend fun saveSettings(chatId: Long, edit: GroupSettingsEdit) {
        groupSettingsProblem(edit)?.let { throw UserMessageException(it.toUiText()) }
        edit.title?.let { requests(TdApi.SetChatTitle(chatId, it.trim())) }
        edit.description?.let { requests(TdApi.SetChatDescription(chatId, it)) }
        edit.username?.let { username ->
            val supergroup = supergroupId(chatId)
            requests(TdApi.SetSupergroupUsername(supergroup, username.trim().removePrefix("@")))
        }
        edit.slowModeSeconds?.let { requests(TdApi.SetChatSlowModeDelay(chatId, it)) }
        val joinByRequest = edit.joinByRequest
        if (joinByRequest != null) {
            val supergroup = supergroupId(chatId)
            requests(TdApi.ToggleSupergroupJoinByRequest(supergroup, joinByRequest, 0L, true))
        }
        val allHistoryAvailable = edit.allHistoryAvailable
        if (allHistoryAvailable != null) {
            val supergroup = supergroupId(chatId)
            requests(TdApi.ToggleSupergroupIsAllHistoryAvailable(supergroup, allHistoryAvailable))
        }
    }

    suspend fun setPhoto(chatId: Long, media: OutgoingMedia) {
        requests(
            TdApi.SetChatPhoto(chatId, TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(media.path))),
            PHOTO_UPLOAD_TIMEOUT_MS
        )
    }

    suspend fun removePhoto(chatId: Long) {
        requests(TdApi.SetChatPhoto(chatId, null))
    }

    private suspend fun supergroupId(chatId: Long): Long {
        val type = requests(TdApi.GetChat(chatId)).type as? TdApi.ChatTypeSupergroup
            ?: throw UserMessageException(UiText.Res(R.string.err_group_not_manageable))
        return type.supergroupId
    }

    private fun requireValidInviteDraft(draft: GroupInviteLinkDraft): GroupInviteLinkDraft {
        val normalized = draft.normalized()
        groupInviteLinkProblem(normalized)?.let { throw UserMessageException(it.toUiText()) }
        return normalized
    }
}

private const val PAGE_SIZE = 100
private const val MAX_INVITE_PAGES = 5
private const val MAX_JOIN_REQUEST_PAGES = 2
private const val PHOTO_UPLOAD_TIMEOUT_MS = 60_000L

private fun TdApi.ChatMember.userId(): Long? = (memberId as? TdApi.MessageSenderUser)?.userId

private fun TdApi.User.displayName(): String =
    listOf(firstName, lastName).filter { !it.isNullOrBlank() }.joinToString(" ")

private fun TdApi.User.isOnline(): Boolean =
    this.type !is TdApi.UserTypeBot &&
        (status as? TdApi.UserStatusOnline)?.let { it.expires > System.currentTimeMillis() / 1000 } == true

private fun TdApi.ChatMember.isAnonymous(): Boolean = when (val status = this.status) {
    is TdApi.ChatMemberStatusCreator -> status.isAnonymous
    is TdApi.ChatMemberStatusAdministrator -> status.rights.isAnonymous
    else -> false
}

private fun TdApi.ChatMember.isMember(): Boolean = when (val status = this.status) {
    is TdApi.ChatMemberStatusCreator -> status.isMember
    is TdApi.ChatMemberStatusRestricted -> status.isMember
    is TdApi.ChatMemberStatusMember, is TdApi.ChatMemberStatusAdministrator -> true
    else -> false
}

private fun GroupMemberStatus.matches(filter: GroupMemberFilter): Boolean = when (filter) {
    GroupMemberFilter.Recent -> this != GroupMemberStatus.Banned && this != GroupMemberStatus.Left
    GroupMemberFilter.Administrators -> this == GroupMemberStatus.Owner || this == GroupMemberStatus.Administrator
    GroupMemberFilter.Restricted -> this == GroupMemberStatus.Restricted
    GroupMemberFilter.Banned -> this == GroupMemberStatus.Banned
}

private fun GroupMemberFilter.toSupergroupMembersFilter(): TdApi.SupergroupMembersFilter = when (this) {
    GroupMemberFilter.Recent -> TdApi.SupergroupMembersFilterRecent()
    GroupMemberFilter.Administrators -> TdApi.SupergroupMembersFilterAdministrators()
    GroupMemberFilter.Restricted -> TdApi.SupergroupMembersFilterRestricted("")
    GroupMemberFilter.Banned -> TdApi.SupergroupMembersFilterBanned("")
}

private fun GroupMemberFilter.toChatMembersFilter(): TdApi.ChatMembersFilter = when (this) {
    GroupMemberFilter.Recent -> TdApi.ChatMembersFilterMembers()
    GroupMemberFilter.Administrators -> TdApi.ChatMembersFilterAdministrators()
    GroupMemberFilter.Restricted -> TdApi.ChatMembersFilterRestricted()
    GroupMemberFilter.Banned -> TdApi.ChatMembersFilterBanned()
}

/** Ограничение оставляет только чтение: ни отправки, ни реакций, ни приглашений. */
private fun readOnlyPermissions(): TdApi.ChatPermissions = TdApi.ChatPermissions(
    false, false, false, false, false, false, false, false,
    false, false, false, false, false, false, false, false
)

private fun GroupSettingsProblem.toUiText(): UiText = UiText.Res(
    when (this) {
        GroupSettingsProblem.EmptyTitle -> R.string.err_group_title_required
        GroupSettingsProblem.TitleTooLong -> R.string.err_group_title_too_long
        GroupSettingsProblem.DescriptionTooLong -> R.string.err_group_description_too_long
        GroupSettingsProblem.UsernameInvalid -> R.string.err_username_invalid
    }
)

private fun GroupInviteLinkProblem.toUiText(): UiText = UiText.Res(
    when (this) {
        GroupInviteLinkProblem.NameTooLong -> R.string.err_group_invite_name_too_long
        GroupInviteLinkProblem.MemberLimitOutOfRange -> R.string.err_group_invite_limit
        GroupInviteLinkProblem.LimitWithApproval -> R.string.err_group_invite_limit_approval
    }
)
