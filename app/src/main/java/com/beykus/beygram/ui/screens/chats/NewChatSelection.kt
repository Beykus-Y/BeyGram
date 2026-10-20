package com.beykus.beygram.ui.screens.chats

import com.beykus.beygram.domain.model.ContactPreview

/**
 * Filtering and multi-selection are shared by the two creation screens.
 * Telegram's existing CreatePrivateChat operation reuses a conversation instead of
 * creating a duplicate when an existing contact is tapped.
 */
internal fun filterNewChatContacts(contacts: List<ContactPreview>, query: String): List<ContactPreview> {
    val needle = query.trim()
    if (needle.isEmpty()) return contacts
    return contacts.filter { contact ->
        contact.title.contains(needle, ignoreCase = true) ||
            contact.subtitle.contains(needle, ignoreCase = true)
    }
}

/** Only server-provided contact IDs can be passed to group creation. */
internal fun selectedNewGroupMembers(selected: Collection<Long>, contacts: List<ContactPreview>): LongArray {
    val allowed = contacts.asSequence().map { it.userId }.filter { it > 0L }.toSet()
    return selected.asSequence().filter { it in allowed }.distinct().take(200).toList().toLongArray()
}

internal fun toggledGroupMembers(current: List<Long>, id: Long, maximum: Int = 200): List<Long> = when {
    id <= 0L -> current
    id in current -> current.filterNot { it == id }
    current.size >= maximum -> current
    else -> current + id
}

internal fun canSubmitNewGroup(title: String, members: Collection<Long>, busy: Boolean): Boolean =
    !busy && title.trim().length in 1..128 && members.isNotEmpty()
