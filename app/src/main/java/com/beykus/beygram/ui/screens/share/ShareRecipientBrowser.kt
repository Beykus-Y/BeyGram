package com.beykus.beygram.ui.screens.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.TelegramChatFolders
import com.beykus.beygram.domain.model.TelegramFolderLoadState
import com.beykus.beygram.domain.model.isInFolder
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal data class ShareRecipientDirectory(
    val chats: List<ChatPreview>,
    val folders: TelegramChatFolders = TelegramChatFolders(),
    val folderLists: Map<Int, List<ChatPreview>> = emptyMap(),
    val loadStates: Map<Int, TelegramFolderLoadState> = emptyMap()
)

@Composable
internal fun rememberShareRecipientDirectory(
    chats: List<ChatPreview>,
    repository: TelegramRepository?,
    candidatePolicy: (List<ChatPreview>) -> List<ChatPreview> = { it }
): ShareRecipientDirectory {
    if (repository == null) return ShareRecipientDirectory(candidatePolicy(chats))
    val folders by repository.chatFolders.collectAsStateWithLifecycle()
    val folderLists by repository.folderChats.collectAsStateWithLifecycle()
    val loadStates by repository.folderLoadStates.collectAsStateWithLifecycle()
    val known = remember(chats, folderLists) { (chats + folderLists.values.flatten()).distinctBy { it.id } }
    return ShareRecipientDirectory(candidatePolicy(known), folders, folderLists, loadStates)
}

/** A folder uses server membership; All includes main, archive and cached folder-only chats. */
internal fun folderShareCandidates(directory: ShareRecipientDirectory, folderId: Int?, query: String): List<ChatPreview> {
    val members = folderId?.let { directory.folderLists[it].orEmpty().map { chat -> chat.id }.toSet() }
    val chats = if (folderId == null) directory.chats else directory.chats.filter {
        it.id in members.orEmpty() || it.isInFolder(folderId)
    }
    val candidates = shareTargetCandidates(chats, query)
    if (folderId == null) return candidates
    val positions = directory.folderLists[folderId].orEmpty().mapIndexed { index, chat -> chat.id to index }.toMap()
    return candidates.sortedWith(compareBy<ChatPreview> { positions[it.id] ?: Int.MAX_VALUE })
}

@Composable
internal fun ShareRecipientBrowser(
    directory: ShareRecipientDirectory,
    repository: TelegramRepository?,
    query: String,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onChoose: (ChatPreview) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedIds: Set<Long>? = null
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var pickedFolder by rememberSaveable(repository) { mutableStateOf<Int?>(null) }
    val folderId = pickedFolder?.takeIf { id -> directory.folders.folders.any { it.id == id } }
    LaunchedEffect(directory.folders.initialized, folderId) {
        if (directory.folders.initialized && folderId == null) pickedFolder = null
    }
    val load = folderId?.let { directory.loadStates[it] ?: TelegramFolderLoadState() }
    LaunchedEffect(repository, folderId) {
        if (folderId != null && directory.folderLists[folderId].isNullOrEmpty() &&
            load?.hasMore == true && load.loading != true) repository?.loadMoreFolderChats(folderId)
    }
    val shown = remember(directory, folderId, query) { folderShareCandidates(directory, folderId, query) }

    Column(modifier.fillMaxWidth()) {
        if (directory.folders.folders.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "all") {
                    FolderPill(stringResource(R.string.chat_folders_all), folderId == null, enabled) { pickedFolder = null }
                }
                items(directory.folders.folders, key = { it.id }) { folder ->
                    FolderPill(folder.title, folderId == folder.id, enabled) { pickedFolder = folder.id }
                }
            }
        }
        if (shown.isEmpty() && load?.loading != true) {
            Text(stringResource(R.string.forward_no_results), color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        ShareRecipientGrid(shown, observeFile, downloadFile, onChoose, Modifier.weight(1f), enabled, selectedIds)
        if (folderId != null && load != null) {
            if (load.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp))
            else if (load.hasMore || load.error) TextButton(
                onClick = { scope.launch { repository?.loadMoreFolderChats(folderId) } },
                enabled = enabled
            ) {
                Text(stringResource(if (load.error) R.string.common_retry else R.string.share_load_more_chats))
            }
        }
    }
}

@Composable
private fun FolderPill(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Box(
        Modifier.clip(styledShape(18.dp))
            .background(if (selected) palette.accentSoft else palette.surface)
            .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = if (selected) palette.accentInk else palette.textSecondary)
    }
}
