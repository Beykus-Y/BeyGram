package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.EmojiPickerSearchResults
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.repository.ConversationRepository

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class DockCustomEmoji(
    val packs: List<CustomEmojiSetBrief>,
    val installedIds: Set<Long>,
    val busyPacks: Set<Long>,
    val failedPacks: Set<Long>,
    val changeInstallation: (Long, Boolean) -> Unit,
    val reorderBusy: Boolean,
    val reorder: (List<Long>) -> Unit,
    val rows: List<PremiumEmojiRow>,
    val recentIds: List<Long>,
    val loadPack: (Long) -> Unit,
    val failed: Boolean,
    val retry: () -> Unit,
    val observeCustomEmoji: ObserveCustomEmoji,
    val observeFile: ObserveMediaFile,
    val downloadFile: DownloadMediaFile,
    val onPick: (CustomEmoji) -> Unit
)

@Composable
internal fun rememberDockCustomEmoji(
    conversation: ConversationRepository,
    observeFile: ObserveMediaFile,
    downloadFile: DownloadMediaFile,
    packsVersion: Long,
    /** Recent custom emoji are owned by the panel, so recovery and explicit clearing update one list. */
    recentIds: List<Long>,
    onPick: (CustomEmoji) -> Boolean,
    onPicked: (Long) -> Unit,
): DockCustomEmoji {
    var installed by remember(conversation) { mutableStateOf<List<CustomEmojiSetBrief>>(emptyList()) }
    var recommended by remember(conversation) { mutableStateOf<List<CustomEmojiSetBrief>>(emptyList()) }
    var busyPacks by remember(conversation) { mutableStateOf<Set<Long>>(emptySet()) }
    var failedPacks by remember(conversation) { mutableStateOf<Set<Long>>(emptySet()) }
    var failed by remember(conversation) { mutableStateOf(false) }
    var attempt by remember(conversation) { mutableIntStateOf(0) }
    LaunchedEffect(conversation, attempt, packsVersion) {
        failed = false
        conversation.installedCustomEmojiSets().fold(
            onSuccess = { values -> installed = values.distinctBy { it.id } },
            onFailure = { failed = true }
        )
    }
    LaunchedEffect(conversation, attempt) {
        conversation.recommendedCustomEmojiSets().onSuccess { values -> recommended = values.distinctBy { it.id } }
    }
    var reorderBusy by remember(conversation) { mutableStateOf(false) }
    val installedIds = remember(installed) { installed.mapTo(HashSet()) { it.id } }
    val packs = remember(installed, recommended, installedIds) { installed + recommended.filterNot { it.id in installedIds } }
    val scope = rememberCoroutineScope()
    fun changeInstallation(setId: Long, install: Boolean) {
        if (setId in busyPacks) return
        busyPacks = busyPacks + setId
        failedPacks = failedPacks - setId
        scope.launch {
            try {
                conversation.changeStickerSet(setId, install)
                    .onSuccess { attempt++ }
                    .onFailure { failedPacks = failedPacks + setId }
            } finally {
                busyPacks = busyPacks - setId
            }
        }
    }
    val loader = remember(scope, conversation) { CustomEmojiSetLoader(scope, load = conversation::customEmojiSetIds) }
    val states by loader.states.collectAsStateWithLifecycle()
    val rows = remember(packs, states) { premiumEmojiRows(packs, states) }
    return DockCustomEmoji(
        packs, installedIds, busyPacks, failedPacks, ::changeInstallation, reorderBusy,
        { order ->
            if (!reorderBusy) {
                reorderBusy = true
                scope.launch {
                    try {
                        conversation.reorderStickerSets(customEmoji = true, orderedIds = order)
                            .onSuccess { attempt++ }
                            .onFailure { failed = true }
                    } finally {
                        reorderBusy = false
                    }
                }
            }
        },
        rows, recentIds, loader::request, failed, { attempt++ },
        conversation::observeCustomEmoji, observeFile, downloadFile
    ) { emoji ->
        if (onPick(emoji)) onPicked(emoji.customEmojiId)
    }
}

@Composable
internal fun DockCustomEmojiCell(
    id: Long,
    dock: DockCustomEmoji,
    modifier: Modifier = Modifier,
    onClick: ((CustomEmoji) -> Unit)? = dock.onPick
) {
    val current by remember(id, dock.observeCustomEmoji) {
        dock.observeCustomEmoji(id)
    }.collectAsStateWithLifecycle()
    val emoji = current?.takeIf { it.customEmojiId == id }
    Box(
        modifier.height(48.dp).then(if (onClick != null && emoji != null) {
            Modifier.clickable(role = Role.Button) { onClick(emoji) }
        } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (emoji != null) CustomEmojiImage(emoji, 28.dp, dock.observeFile, dock.downloadFile) {
            Text(emoji.emoji, fontSize = 24.sp)
        }
    }
}

@Composable
internal fun DockEmojiSearch(
    conversation: ConversationRepository,
    query: String,
    dock: DockCustomEmoji,
    onUnicode: (String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val languages = context.resources.configuration.locales
    val languageCodes = remember(languages) { List(languages.size()) { languages[it].language }.distinct() }
    var results by remember(conversation, query) { mutableStateOf<Result<EmojiPickerSearchResults>?>(null) }
    var attempt by remember(conversation, query) { mutableIntStateOf(0) }
    LaunchedEffect(conversation, query, languageCodes, attempt) {
        results = null
        delay(300)
        results = conversation.searchPickerEmoji(query.trim(), languageCodes)
    }
    val found = results?.getOrNull()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (found != null) {
            items(found.unicode, key = { "unicode-$it" }) { emoji -> EmojiCell(emoji) { onUnicode(emoji) } }
            items(found.custom, key = { "custom-$it" }) { id -> DockCustomEmojiCell(id, dock) }
            for (pack in found.packs) {
                item(key = "pack-${pack.id}", span = { GridItemSpan(maxLineSpan) }) { DockEmojiPackHeader(pack.id, pack.title, dock) }
                items(pack.covers.distinct(), key = { "cover-${pack.id}-$it" }) { id -> DockCustomEmojiCell(id, dock) }
            }
            if (found.unicode.isEmpty() && found.custom.isEmpty() && found.packs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmojiGroupHeader(stringResource(R.string.global_search_empty)) }
            }
        } else if (results == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CircularProgressIndicator(Modifier.size(20.dp))
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.common_retry)) }
            }
        }
    }
}

@Composable
internal fun DockEmojiPackHeader(setId: Long, title: String, dock: DockCustomEmoji) {
    val installed = setId in dock.installedIds
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { EmojiGroupHeader(title) }
        if (installed) {
            PickerPackOrderButtons(
                dock.packs.filter { it.id in dock.installedIds }.map { it.id },
                setId, dock.reorderBusy, dock.reorder
            )
        }
        TextButton(
            enabled = setId !in dock.busyPacks,
            onClick = { dock.changeInstallation(setId, !installed) }
        ) {
            Text(stringResource(when {
                setId in dock.failedPacks -> R.string.common_retry
                installed -> R.string.sticker_set_remove
                else -> R.string.sticker_set_add
            }))
        }
    }
}
