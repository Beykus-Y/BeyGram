package com.beykus.beygram.domain.model

/** A Telegram CreateNewBasicGroupChat call can complete even after its UI disappears.
 * Its in-flight state therefore belongs to the repository, not a Compose scope.
 */
sealed interface GroupCreationState {
    data object Idle : GroupCreationState
    data class Pending(val title: String) : GroupCreationState
    data class Created(val chatId: Long, val failedToAddMembers: Int) : GroupCreationState
    data object NeedsVerification : GroupCreationState
    data object Failed : GroupCreationState
}

/** In-flight, completed and uncertain operations must never be submitted twice. */
fun mayBeginGroupCreation(state: GroupCreationState): Boolean =
    state == GroupCreationState.Idle || state == GroupCreationState.Failed
