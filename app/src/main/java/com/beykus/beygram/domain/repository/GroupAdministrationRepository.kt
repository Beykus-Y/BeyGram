package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.GroupAdministratorRights
import com.beykus.beygram.domain.model.GroupInviteLink
import com.beykus.beygram.domain.model.GroupInviteLinkDraft
import com.beykus.beygram.domain.model.GroupJoinRequestsPage
import com.beykus.beygram.domain.model.GroupManagementInfo
import com.beykus.beygram.domain.model.GroupMemberAddition
import com.beykus.beygram.domain.model.GroupMemberFilter
import com.beykus.beygram.domain.model.GroupMemberPage
import com.beykus.beygram.domain.model.GroupSettingsEdit
import com.beykus.beygram.domain.model.OutgoingMedia

/**
 * Управление группой или каналом. Все изменения уходят в TDLib и проверяются сервером; репозиторий
 * не кэширует права между вызовами, чтобы после смены роли интерфейс не показывал устаревшие действия.
 */
interface GroupAdministrationRepository {
    /** Свежие настройки и права текущего аккаунта в чате. */
    suspend fun loadGroupManagement(chatId: Long): Result<GroupManagementInfo>

    /** Одна страница участников; [query] ищет по имени и username, пустой — обычный список. */
    suspend fun loadGroupMembers(
        chatId: Long,
        filter: GroupMemberFilter,
        query: String = "",
        offset: Int = 0,
        limit: Int = 50
    ): Result<GroupMemberPage>

    suspend fun addGroupMembers(chatId: Long, userIds: List<Long>): Result<GroupMemberAddition>

    /** Бан в TDLib одновременно удаляет участника из группы или канала. */
    suspend fun banGroupMember(chatId: Long, userId: Long): Result<Unit>
    suspend fun unbanGroupMember(chatId: Long, userId: Long): Result<Unit>
    suspend fun restrictGroupMember(chatId: Long, userId: Long): Result<Unit>
    /** [keepMembership] false снимает ограничение с уже вышедшего пользователя, не добавляя его обратно. */
    suspend fun unrestrictGroupMember(chatId: Long, userId: Long, keepMembership: Boolean = true): Result<Unit>

    /** Повышение и изменение прав администратора; сервер проверит can_promote_members. */
    suspend fun setGroupMemberRights(chatId: Long, userId: Long, rights: GroupAdministratorRights): Result<Unit>
    suspend fun demoteGroupMember(chatId: Long, userId: Long): Result<Unit>

    /** Активные ссылки, основная первой. */
    suspend fun loadGroupInviteLinks(chatId: Long): Result<List<GroupInviteLink>>
    suspend fun createGroupInviteLink(chatId: Long, draft: GroupInviteLinkDraft): Result<GroupInviteLink>
    suspend fun editGroupInviteLink(chatId: Long, link: String, draft: GroupInviteLinkDraft): Result<GroupInviteLink>
    /** Переименование платной subscription-ссылки; цена и срок не меняются. */
    suspend fun renameGroupSubscriptionInviteLink(chatId: Long, link: String, name: String): Result<GroupInviteLink>
    suspend fun revokeGroupInviteLink(chatId: Long, link: String): Result<Unit>

    /** Одна страница ожидающих заявок; [offset] — курсор [GroupJoinRequestsPage.nextOffset]. */
    suspend fun loadGroupJoinRequests(chatId: Long, offset: String?, limit: Int = 50): Result<GroupJoinRequestsPage>
    suspend fun processGroupJoinRequest(chatId: Long, userId: Long, approve: Boolean): Result<Unit>

    /** Меняет только заполненные поля [edit], не затирая недоступные роли настройки. */
    suspend fun saveGroupSettings(chatId: Long, edit: GroupSettingsEdit): Result<Unit>
    suspend fun setGroupPhoto(chatId: Long, photo: OutgoingMedia): Result<Unit>
    suspend fun removeGroupPhoto(chatId: Long): Result<Unit>
}
