package com.beykus.beygram.domain.model

/** TDLib's position and pinned flag belong to a *specific* chat list, not to a chat globally. */
data class FolderChatPosition(val order: Long, val pinned: Boolean)

/** User-created server folder. IDs and their order originate exclusively from TDLib. */
data class TelegramChatFolder(
    val id: Int,
    val title: String,
    val iconName: String = "",
    /** TDLib count, not an estimate from only the chats loaded into memory. */
    val unreadChats: Int? = null
)

/**
 * updateChatFolders is the authoritative source of folder membership and tab order.
 * An uninitialized state must not be mistaken for an account with zero folders.
 */
data class TelegramChatFolders(
    val initialized: Boolean = false,
    val folders: List<TelegramChatFolder> = emptyList(),
    val mainChatListPosition: Int = 0
)

data class TelegramFolderLoadState(
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: Boolean = false
)

/** Special Main list gets its TDLib-provided position among server-defined folders. */
sealed interface TelegramFolderTab {
    data object Main : TelegramFolderTab
    data class Folder(val id: Int) : TelegramFolderTab
}

fun orderedTelegramFolderTabs(state: TelegramChatFolders): List<TelegramFolderTab> {
    val folders: MutableList<TelegramFolderTab> =
        state.folders.map<TelegramChatFolder, TelegramFolderTab> { TelegramFolderTab.Folder(it.id) }.toMutableList()
    val mainIndex = state.mainChatListPosition.coerceIn(0, folders.size)
    return folders.apply { add(mainIndex, TelegramFolderTab.Main) }
}

/** A removed/renamed folder must never leave the UI stuck on an invalid selection. */
fun validTelegramFolderSelection(state: TelegramChatFolders, folderId: Int?): Int? =
    folderId?.takeIf { id -> state.initialized && state.folders.any { it.id == id } }

/**
 * Folder ordering comes from TDLib ChatListFolder positions. Main/archive order and
 * pinned state remain independent; included chats do not have to appear in Main.
 */
fun orderedTelegramFolderChats(chats: Collection<ChatPreview>, folderId: Int): List<ChatPreview> =
    chats.mapNotNull { chat ->
        val position = chat.folderPositions[folderId] ?: return@mapNotNull null
        if (position.order <= 0L) return@mapNotNull null
        chat.copy(isPinned = position.pinned, order = position.order)
    }.sortedWith(compareByDescending<ChatPreview> { it.isPinned }
        .thenByDescending { it.order }
        .thenByDescending { it.id })
