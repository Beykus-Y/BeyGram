package com.beykus.beygram.domain.model

/** Тип чата, которым управляют: обычная группа, супергруппа или канал. */
enum class ManagedChatKind { BasicGroup, Supergroup, Channel }

/** Роль участника в управляемой группе или канале. */
enum class GroupMemberStatus { Owner, Administrator, Member, Restricted, Banned, Left }

/**
 * Права администратора из TDLib. Для канала публикация сообщений — отдельное право, для группы —
 * право писать задаётся разрешениями чата, поэтому здесь только административные возможности.
 */
data class GroupAdministratorRights(
    val canManageChat: Boolean = false,
    val canChangeInfo: Boolean = false,
    val canPostMessages: Boolean = false,
    val canEditMessages: Boolean = false,
    val canDeleteMessages: Boolean = false,
    val canInviteUsers: Boolean = false,
    val canRestrictMembers: Boolean = false,
    val canPinMessages: Boolean = false,
    val canManageTopics: Boolean = false,
    val canPromoteMembers: Boolean = false,
    val canManageVideoChats: Boolean = false,
    val canPostStories: Boolean = false,
    val canEditStories: Boolean = false,
    val canDeleteStories: Boolean = false,
    val canManageDirectMessages: Boolean = false,
    val canManageTags: Boolean = false,
    val isAnonymous: Boolean = false
)

/**
 * Что подписанный аккаунт может делать в конкретном чате. Набор строится из типа чата и статуса
 * участника, а не угадывается интерфейсом: обычный участник получает пустой набор и не видит
 * административных разделов.
 */
data class GroupAdminRights(
    val kind: ManagedChatKind,
    val isOwner: Boolean = false,
    val isAdministrator: Boolean = false,
    val canAddMembers: Boolean = false,
    val canRemoveMembers: Boolean = false,
    val canRestrictMembers: Boolean = false,
    val canPromoteMembers: Boolean = false,
    val canManageInviteLinks: Boolean = false,
    val canManageJoinRequests: Boolean = false,
    val canChangeInfo: Boolean = false,
    val canChangeUsername: Boolean = false,
    val canManageSlowMode: Boolean = false,
    val canToggleHistory: Boolean = false,
    val canToggleJoinByRequest: Boolean = false
) {
    val canManageMembers: Boolean
        get() = canAddMembers || canRemoveMembers || canRestrictMembers || canPromoteMembers

    /** True, если роли доступен хотя бы один административный раздел. */
    val canManageAnything: Boolean
        get() = canManageMembers || canManageInviteLinks || canManageJoinRequests || canChangeInfo ||
            canChangeUsername || canManageSlowMode || canToggleHistory || canToggleJoinByRequest
}

/** Участник с правами и статусом для экрана управления. */
data class GroupMemberEntry(
    val userId: Long,
    val name: String,
    val status: GroupMemberStatus,
    /** Для Restricted: остался ли пользователь в чате; false — ограничен, но уже не участник. */
    val isMember: Boolean = true,
    val online: Boolean,
    val isBot: Boolean,
    val tag: String,
    val photo: AvatarPhoto?,
    val avatarColorArgb: Long,
    val isAnonymous: Boolean = false,
    /** Заполнено только для администраторов и владельца. */
    val rights: GroupAdministratorRights? = null,
    /** TDLib разрешает текущему пользователю изменить этого администратора. */
    val canBeEdited: Boolean = false,
    val restrictedUntilSeconds: Int = 0,
    val bannedUntilSeconds: Int = 0,
    val isCurrentUser: Boolean = false
) {
    val initials: String get() = initialsOf(name)
}

/** Страница участников. [fetched] — сколько записей вернул TDLib до фильтрации. */
data class GroupMemberPage(val total: Int, val members: List<GroupMemberEntry>, val fetched: Int)

enum class GroupMemberFilter { Recent, Administrators, Restricted, Banned }

/**
 * Действие над участником. [Ban] в обычной группе означает удаление: отдельного списка
 * заблокированных у basic group в TDLib нет.
 */
enum class GroupMemberAction { Promote, EditRights, Demote, Restrict, Unrestrict, Ban, Unban }

/** Настройки, которые администратор меняет в профиле группы или канала. */
data class GroupManagementInfo(
    val chatId: Long,
    val kind: ManagedChatKind,
    val title: String,
    val description: String = "",
    val username: String = "",
    val photo: AvatarPhoto? = null,
    val slowModeSeconds: Int = 0,
    val joinByRequest: Boolean = false,
    val allHistoryAvailable: Boolean = true,
    val memberCount: Int? = null,
    val pendingJoinRequestCount: Int = 0,
    val primaryInviteLink: String = "",
    val rights: GroupAdminRights = GroupAdminRights(kind),
    /** Права текущего администратора, которые он может передать другому; null — владелец или не администратор. */
    val viewerRights: GroupAdministratorRights? = null
) {
    val initials: String get() = initialsOf(title)
}

/** Изменяемые поля; null означает «не менять», чтобы частичные права не затирали чужие настройки. */
data class GroupSettingsEdit(
    val title: String? = null,
    val description: String? = null,
    val username: String? = null,
    val slowModeSeconds: Int? = null,
    val joinByRequest: Boolean? = null,
    val allHistoryAvailable: Boolean? = null
)

/** Причина, по которой настройки нельзя отправить; интерфейс переводит её в строку. */
enum class GroupSettingsProblem { EmptyTitle, TitleTooLong, DescriptionTooLong, UsernameInvalid }

/** Ссылка-приглашение с её сроком, лимитом и заявками. */
data class GroupInviteLink(
    val link: String,
    val name: String = "",
    val creatorUserId: Long = 0L,
    val createdSeconds: Int = 0,
    val expiresAtSeconds: Int = 0,
    val memberLimit: Int = 0,
    val memberCount: Int = 0,
    val pendingJoinRequestCount: Int = 0,
    val requiresApproval: Boolean = false,
    val isPrimary: Boolean = false,
    val isRevoked: Boolean = false,
    /** Платная подписка: срок/лимит/заявки для неё TDLib не разрешает. */
    val subscriptionPeriodSeconds: Int = 0,
    val subscriptionStarCount: Long = 0L,
    /** Ссылку своего аккаунта администратор может менять и отзывать; чужие — только владелец. */
    val creatorIsCurrentUser: Boolean = false
) {
    val isSubscription: Boolean get() = subscriptionStarCount > 0L || subscriptionPeriodSeconds > 0
}

/** Параметры создания или изменения ссылки. [expiresAtSeconds] — абсолютная точка Unix; 0 — бессрочно. */
data class GroupInviteLinkDraft(
    val name: String = "",
    val expiresAtSeconds: Int = 0,
    val memberLimit: Int = 0,
    val requiresApproval: Boolean = false
)

/** Причина, по которой ссылку нельзя создать; интерфейс переводит её в строку. */
enum class GroupInviteLinkProblem { NameTooLong, MemberLimitOutOfRange, LimitWithApproval }

/**
 * Управление конкретной ссылкой. Основную ссылку нельзя редактировать; чужие ссылки, включая
 * основную, меняет и отзывает только владелец чата. Платная подписка редактируется отдельно.
 */
data class GroupInviteLinkPermissions(
    val canEdit: Boolean = false,
    val canRevoke: Boolean = false,
    /** Платную subscription-ссылку можно переименовать, но не менять её срок/лимит/заявки. */
    val canRename: Boolean = false
)

fun groupInviteLinkPermissions(rights: GroupAdminRights, link: GroupInviteLink): GroupInviteLinkPermissions {
    if (!rights.canManageInviteLinks) return GroupInviteLinkPermissions()
    val ownOrOwner = rights.isOwner || link.creatorIsCurrentUser
    return GroupInviteLinkPermissions(
        canEdit = !link.isPrimary && !link.isSubscription && ownOrOwner,
        canRevoke = ownOrOwner,
        canRename = link.isSubscription && ownOrOwner
    )
}

/** Заявка на вступление; [bio] приходит от TDLib пустой, если её нет. */
data class GroupJoinRequest(
    val userId: Long,
    val name: String,
    val bio: String,
    val dateSeconds: Int,
    val photo: AvatarPhoto?,
    val avatarColorArgb: Long
) {
    val initials: String get() = initialsOf(name)
}

/** Страница заявок; [nextOffset] — курсор следующей страницы, null — страниц больше нет. */
data class GroupJoinRequestsPage(
    val total: Int,
    val requests: List<GroupJoinRequest>,
    val nextOffset: String?
)

/** Позиция заявки для TDLib offsetRequest; стабильна между approve/decline. */
data class GroupJoinRequestCursor(val userId: Long, val dateSeconds: Int)

fun GroupJoinRequest.cursor(): String = "$userId:$dateSeconds"

fun parseGroupJoinRequestCursor(value: String?): GroupJoinRequestCursor? {
    if (value.isNullOrBlank()) return null
    val parts = value.split(':', limit = 2)
    if (parts.size != 2) return null
    val userId = parts[0].toLongOrNull()?.takeIf { it > 0L } ?: return null
    val date = parts[1].toIntOrNull() ?: return null
    return GroupJoinRequestCursor(userId, date)
}

/**
 * Курсор после обработки заявки. Если обработан последний загруженный элемент, следующая
 * страница начинается с предыдущего: иначе TDLib искал бы offset среди уже не существующих заявок.
 */
fun nextJoinRequestOffset(
    loaded: List<GroupJoinRequest>,
    processed: GroupJoinRequest,
    currentOffset: String?
): String? {
    if (currentOffset != processed.cursor()) return currentOffset
    return loaded.lastOrNull { it.userId != processed.userId }?.cursor()
}

fun joinRequestsHaveMore(loadedCount: Int, total: Int, nextOffset: String?): Boolean =
    nextOffset != null && loadedCount < total

/** Добавляет страницу без дублей; обработанные заявки не возвращаются из устаревшего ответа. */
fun mergeJoinRequests(
    loaded: List<GroupJoinRequest>,
    incoming: List<GroupJoinRequest>,
    processedUserIds: Set<Long>
): List<GroupJoinRequest> =
    (loaded + incoming)
        .filterNot { it.userId in processedUserIds }
        .distinctBy { it.userId }

fun joinRequestTotalAfterProcessing(total: Int): Int = (total - 1).coerceAtLeast(0)

/** Итог добавления участников: TDLib сообщает, кого добавить не удалось и почему. */
data class GroupMemberAddition(
    val addedUserIds: List<Long> = emptyList(),
    val failedUserIds: List<Long> = emptyList(),
    /** Пользователю нужен Telegram Premium, чтобы его добавили по этому правилу чата. */
    val premiumRequiredUserIds: List<Long> = emptyList()
)

internal const val GROUP_TITLE_MAX_LENGTH = 128
internal const val GROUP_DESCRIPTION_MAX_LENGTH = 255
internal const val GROUP_INVITE_NAME_MAX_LENGTH = 32
internal const val GROUP_INVITE_MEMBER_LIMIT_MAX = 99_999

/** Допустимые значения медленного режима из TDLib. */
val GROUP_SLOW_MODE_VALUES: List<Int> = listOf(0, 5, 10, 30, 60, 300, 900, 3600)

/** Сроки жизни ссылок, которые предлагает интерфейс. */
val GROUP_INVITE_TERM_VALUES: List<Int> = listOf(0, 86_400, 7 * 86_400, 30 * 86_400)

/**
 * Права для роли и типа чата. В обычной группе администраторы по правилам Telegram могут
 * приглашать и удалять участников и менять профиль, но не выдавать админку и не ограничивать
 * переписку. В канале нельзя добавлять подписчиков напрямую и ограничивать их.
 */
fun groupAdminRights(
    kind: ManagedChatKind,
    role: GroupMemberStatus,
    adminRights: GroupAdministratorRights? = null
): GroupAdminRights = when (role) {
    GroupMemberStatus.Owner -> GroupAdminRights(
        kind = kind,
        isOwner = true,
        isAdministrator = true,
        canAddMembers = kind != ManagedChatKind.Channel,
        canRemoveMembers = true,
        canRestrictMembers = kind == ManagedChatKind.Supergroup,
        canPromoteMembers = true,
        canManageInviteLinks = true,
        canManageJoinRequests = true,
        canChangeInfo = true,
        canChangeUsername = kind != ManagedChatKind.BasicGroup,
        canManageSlowMode = kind == ManagedChatKind.Supergroup,
        canToggleHistory = kind == ManagedChatKind.Supergroup,
        canToggleJoinByRequest = kind == ManagedChatKind.Supergroup
    )
    GroupMemberStatus.Administrator -> when (kind) {
        ManagedChatKind.BasicGroup -> GroupAdminRights(
            kind = kind,
            isAdministrator = true,
            canAddMembers = true,
            canRemoveMembers = true,
            canManageInviteLinks = true,
            canManageJoinRequests = true,
            canChangeInfo = true
        )
        ManagedChatKind.Supergroup, ManagedChatKind.Channel -> GroupAdminRights(
            kind = kind,
            isAdministrator = true,
            canAddMembers = kind == ManagedChatKind.Supergroup && adminRights?.canInviteUsers == true,
            canRemoveMembers = adminRights?.canRestrictMembers == true,
            canRestrictMembers = kind == ManagedChatKind.Supergroup && adminRights?.canRestrictMembers == true,
            canPromoteMembers = adminRights?.canPromoteMembers == true,
            canManageInviteLinks = adminRights?.canInviteUsers == true,
            canManageJoinRequests = adminRights?.canInviteUsers == true,
            canChangeInfo = adminRights?.canChangeInfo == true,
            canManageSlowMode = kind == ManagedChatKind.Supergroup && adminRights?.canRestrictMembers == true,
            canToggleHistory = kind == ManagedChatKind.Supergroup && adminRights?.canChangeInfo == true,
            canToggleJoinByRequest = kind == ManagedChatKind.Supergroup && adminRights?.canRestrictMembers == true
        )
    }
    else -> GroupAdminRights(kind)
}

/**
 * Действия, которые текущая роль может выполнить над участником. Владельца и себя не трогаем;
 * администратора меняет только владелец или роль с правом повышения и разрешением TDLib.
 */
fun groupMemberActions(
    rights: GroupAdminRights,
    member: GroupMemberEntry
): Set<GroupMemberAction> = buildSet {
    if (member.isCurrentUser) return@buildSet
    when (member.status) {
        GroupMemberStatus.Owner, GroupMemberStatus.Left -> Unit
        GroupMemberStatus.Administrator -> {
            if ((rights.isOwner || member.canBeEdited) && rights.canPromoteMembers) {
                // В обычной группе права администратора не гранулярные: доступно только снятие.
                if (rights.kind != ManagedChatKind.BasicGroup) add(GroupMemberAction.EditRights)
                add(GroupMemberAction.Demote)
            }
            if (rights.isOwner && rights.canRemoveMembers) add(GroupMemberAction.Ban)
        }
        GroupMemberStatus.Member -> {
            if (rights.canPromoteMembers) add(GroupMemberAction.Promote)
            if (rights.canRestrictMembers) add(GroupMemberAction.Restrict)
            if (rights.canRemoveMembers) add(GroupMemberAction.Ban)
        }
        GroupMemberStatus.Restricted -> {
            if (rights.canPromoteMembers) add(GroupMemberAction.Promote)
            if (rights.canRestrictMembers) add(GroupMemberAction.Unrestrict)
            if (rights.canRemoveMembers) add(GroupMemberAction.Ban)
        }
        GroupMemberStatus.Banned -> if (rights.canRemoveMembers && rights.kind != ManagedChatKind.BasicGroup) {
            add(GroupMemberAction.Unban)
        }
    }
}

/** Права, с которыми участника повышают по умолчанию: как в официальных клиентах. */
fun defaultAdministratorRights(
    kind: ManagedChatKind,
    isAnonymous: Boolean = false
): GroupAdministratorRights = when (kind) {
    ManagedChatKind.Channel -> GroupAdministratorRights(
        canManageChat = true,
        canPostMessages = true,
        canEditMessages = true,
        canDeleteMessages = true,
        canInviteUsers = true,
        canPinMessages = true,
        canManageVideoChats = true,
        isAnonymous = isAnonymous
    )
    ManagedChatKind.BasicGroup, ManagedChatKind.Supergroup -> GroupAdministratorRights(
        canManageChat = true,
        canDeleteMessages = true,
        canInviteUsers = true,
        canRestrictMembers = true,
        canPinMessages = true,
        canManageVideoChats = true,
        isAnonymous = isAnonymous
    )
}

/**
 * TDLib разрешает администратору выдавать только те права, которые есть у него самого.
 * Владелец не ограничен. Возвращает пересечение запрошенных прав с правами действующего админа.
 */
fun constrainAdministratorRights(
    requested: GroupAdministratorRights,
    viewer: GroupAdminRights,
    viewerRights: GroupAdministratorRights?
): GroupAdministratorRights {
    if (viewer.isOwner || viewerRights == null) return requested
    return requested.copy(
        canManageChat = requested.canManageChat && viewerRights.canManageChat,
        canChangeInfo = requested.canChangeInfo && viewerRights.canChangeInfo,
        canPostMessages = requested.canPostMessages && viewerRights.canPostMessages,
        canEditMessages = requested.canEditMessages && viewerRights.canEditMessages,
        canDeleteMessages = requested.canDeleteMessages && viewerRights.canDeleteMessages,
        canInviteUsers = requested.canInviteUsers && viewerRights.canInviteUsers,
        canRestrictMembers = requested.canRestrictMembers && viewerRights.canRestrictMembers,
        canPinMessages = requested.canPinMessages && viewerRights.canPinMessages,
        canManageTopics = requested.canManageTopics && viewerRights.canManageTopics,
        canPromoteMembers = requested.canPromoteMembers && viewerRights.canPromoteMembers,
        canManageVideoChats = requested.canManageVideoChats && viewerRights.canManageVideoChats,
        canPostStories = requested.canPostStories && viewerRights.canPostStories,
        canEditStories = requested.canEditStories && viewerRights.canEditStories,
        canDeleteStories = requested.canDeleteStories && viewerRights.canDeleteStories,
        canManageDirectMessages = requested.canManageDirectMessages && viewerRights.canManageDirectMessages,
        canManageTags = requested.canManageTags && viewerRights.canManageTags,
        isAnonymous = requested.isAnonymous
    )
}

/** Срок действия ссылки: 0 — бессрочно, иначе точка во времени Unix. */
fun inviteExpirationDate(nowSeconds: Long, ttlSeconds: Int?): Int {
    if (ttlSeconds == null || ttlSeconds <= 0) return 0
    return (nowSeconds + ttlSeconds).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/** Остаток срока ссылки в секундах; 0 для бессрочной или уже истёкшей ссылки. */
fun inviteRemainingSeconds(expiresAtSeconds: Int, nowSeconds: Long): Int {
    if (expiresAtSeconds <= 0) return 0
    return (expiresAtSeconds - nowSeconds).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

fun groupSettingsProblem(edit: GroupSettingsEdit): GroupSettingsProblem? {
    edit.title?.let { title ->
        if (title.isBlank()) return GroupSettingsProblem.EmptyTitle
        if (title.codePointCount(0, title.length) > GROUP_TITLE_MAX_LENGTH) return GroupSettingsProblem.TitleTooLong
    }
    edit.description?.let { description ->
        if (description.codePointCount(0, description.length) > GROUP_DESCRIPTION_MAX_LENGTH) return GroupSettingsProblem.DescriptionTooLong
    }
    edit.username?.let { raw ->
        val username = raw.trim().removePrefix("@")
        if (username.isNotEmpty() && !username.matches(Regex("[A-Za-z][A-Za-z0-9_]{4,31}"))) {
            return GroupSettingsProblem.UsernameInvalid
        }
    }
    return null
}

fun groupInviteNameProblem(name: String): GroupInviteLinkProblem? {
    val trimmed = name.trim()
    return if (trimmed.codePointCount(0, trimmed.length) > GROUP_INVITE_NAME_MAX_LENGTH) {
        GroupInviteLinkProblem.NameTooLong
    } else null
}

fun groupInviteLinkProblem(draft: GroupInviteLinkDraft): GroupInviteLinkProblem? {
    groupInviteNameProblem(draft.name)?.let { return it }
    if (draft.memberLimit !in 0..GROUP_INVITE_MEMBER_LIMIT_MAX) return GroupInviteLinkProblem.MemberLimitOutOfRange
    // TDLib отклоняет лимит вместе с заявками: заявка ещё не участник.
    if (draft.requiresApproval && draft.memberLimit != 0) return GroupInviteLinkProblem.LimitWithApproval
    return null
}

/** Нормализует поля перед отправкой: имя без пробелов по краям, @ в username не нужен. */
fun GroupInviteLinkDraft.normalized(): GroupInviteLinkDraft = copy(
    name = name.trim(),
    memberLimit = if (requiresApproval) 0 else memberLimit.coerceIn(0, GROUP_INVITE_MEMBER_LIMIT_MAX)
)
