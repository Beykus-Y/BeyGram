package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class StickerTab { Recent, Sets }

/**
 * Sticker picker: recent stickers, installed sets and text search. A set's metadata is fetched only when the user opens
 * it, and each visible cell downloads its own preview; leaving the grid stops those downloads from being requested.
 */
@Composable
internal fun StickerPickerPanel(
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: (StickerItem) -> Unit,
    onOptions: (StickerItem) -> Unit,
    onClose: () -> Unit,
    onOpenGifs: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(StickerTab.Recent) }

    var recent by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
    var recentLoading by remember { mutableStateOf(true) }
    var recentError by remember { mutableStateOf<String?>(null) }
    var recentRetry by remember { mutableIntStateOf(0) }

    var sets by remember { mutableStateOf<List<StickerSetSummary>>(emptyList()) }
    var setsLoading by remember { mutableStateOf(true) }
    var setsError by remember { mutableStateOf<String?>(null) }
    var setsRetry by remember { mutableIntStateOf(0) }

    var premium by remember { mutableStateOf<Boolean?>(null) }

    var viewingSet by remember { mutableStateOf<StickerSetSummary?>(null) }
    var setContent by remember { mutableStateOf<StickerSetContent?>(null) }
    var setLoading by remember { mutableStateOf(false) }
    var setError by remember { mutableStateOf<String?>(null) }
    var setRetry by remember { mutableIntStateOf(0) }

    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchPage by remember { mutableStateOf<StickerSearchPage?>(null) }
    var searchRetry by remember { mutableIntStateOf(0) }
    // A page request from an older query must not overwrite a newer first page or its continuation offset.
    var searchGeneration by remember { mutableIntStateOf(0) }

    var notice by remember { mutableStateOf<String?>(null) }
    val premiumNotice = stringResource(R.string.sticker_picker_premium_locked)
    val clearSearch: (@Composable () -> Unit)? = if (query.isNotEmpty()) {
        {
            IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.components_clear_search), modifier = Modifier.size(18.dp))
            }
        }
    } else null

    LaunchedEffect(recentRetry) {
        recentLoading = true
        recentError = null
        conversation.recentStickers()
            .onSuccess { recent = it }
            .onFailure { recentError = it.userMessage(context, R.string.sticker_picker_load_failed) }
        recentLoading = false
    }
    LaunchedEffect(setsRetry) {
        setsLoading = true
        setsError = null
        conversation.installedStickerSets()
            .onSuccess { sets = it }
            .onFailure { setsError = it.userMessage(context, R.string.sticker_picker_load_failed) }
        setsLoading = false
    }
    LaunchedEffect(Unit) {
        conversation.isPremiumAccount().onSuccess { premium = it }
    }
    LaunchedEffect(query, searchRetry) {
        val needle = query.trim()
        notice = null
        // Drop the old page before the debounce, so its "Load more" cannot start with the new query and old offset.
        searchGeneration++
        searchPage = null
        searchError = null
        searchLoading = false
        if (needle.isEmpty()) return@LaunchedEffect
        delay(350)
        searchLoading = true
        conversation.searchStickers(needle, 0)
            .onSuccess { searchPage = it }
            .onFailure { searchError = it.userMessage(context, R.string.sticker_picker_load_failed) }
        searchLoading = false
    }
    LaunchedEffect(viewingSet, setRetry) {
        val target = viewingSet ?: return@LaunchedEffect
        setLoading = true
        setError = null
        setContent = null
        conversation.stickerSet(target.id)
            .onSuccess { setContent = it }
            .onFailure { setError = it.userMessage(context, R.string.sticker_picker_load_failed) }
        setLoading = false
    }

    fun loadMoreSearch() {
        val needle = query.trim()
        val page = searchPage ?: return
        val offset = page.nextOffset ?: return
        if (needle.isEmpty() || searchLoading) return
        val generation = searchGeneration
        scope.launch {
            searchLoading = true
            searchError = null
            conversation.searchStickers(needle, offset)
                .onSuccess { next ->
                    if (searchGeneration == generation) searchPage = StickerSearchPage(mergeStickerPages(page.stickers, next), next.nextOffset)
                }
                .onFailure { if (searchGeneration == generation) searchError = it.userMessage(context, R.string.sticker_picker_load_failed) }
            if (searchGeneration == generation) searchLoading = false
        }
    }

    fun openSet(summary: StickerSetSummary) {
        if (viewingSet?.id == summary.id) return
        viewingSet = summary
        setRetry = 0
        query = ""
    }

    fun choose(item: StickerItem) {
        if (item.premium && premium == false) {
            notice = premiumNotice
            return
        }
        notice = null
        onSend(item)
    }

    fun chooseOptions(item: StickerItem) {
        if (item.premium && premium == false) {
            notice = premiumNotice
            return
        }
        notice = null
        onOptions(item)
    }

    Column(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 340.dp)) {
        StickerHeader(
            title = when {
                query.isNotBlank() -> stringResource(R.string.sticker_picker_search_results)
                viewingSet != null -> setContent?.title ?: viewingSet?.title.orEmpty()
                else -> stringResource(R.string.sticker_picker_title)
            },
            onBack = if (viewingSet != null && query.isBlank()) ({ viewingSet = null; setContent = null; setError = null }) else null,
            onClose = onClose,
            onOpenGifs = onOpenGifs
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text(stringResource(R.string.sticker_picker_search), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = clearSearch,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.divider,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                cursorColor = palette.accent,
                focusedContainerColor = palette.surface2,
                unfocusedContainerColor = palette.surface2
            )
        )
        if (query.isBlank() && viewingSet == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerTab(stringResource(R.string.sticker_picker_tab_recent), tab == StickerTab.Recent) { tab = StickerTab.Recent }
                PickerTab(stringResource(R.string.sticker_picker_tab_sets), tab == StickerTab.Sets) { tab = StickerTab.Sets }
            }
        }
        notice?.let { message ->
            Text(message, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).errorAnnouncement())
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                viewingSet != null && query.isBlank() -> when {
                    setLoading -> PickerCenter { CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp) }
                    setError != null -> PickerError(setError.orEmpty(), onRetry = { setRetry++ })
                    setContent != null -> StickerGrid(
                        stickers = setContent?.stickers.orEmpty(),
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onClick = ::choose,
                        onLongClick = ::chooseOptions
                    )
                    else -> PickerEmpty(stringResource(R.string.sticker_picker_empty))
                }
                query.isNotBlank() -> when {
                    searchLoading && searchPage == null -> PickerCenter { CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp) }
                    searchError != null && searchPage == null -> PickerError(searchError.orEmpty(), onRetry = { searchRetry++ })
                    searchPage?.stickers.isNullOrEmpty() && !searchLoading -> PickerEmpty(stringResource(R.string.sticker_picker_empty_search))
                    else -> StickerGrid(
                        stickers = searchPage?.stickers.orEmpty(),
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onClick = ::choose,
                        onLongClick = ::chooseOptions,
                        footer = if (searchPage?.nextOffset != null) {
                            { PickerMore(searchLoading) { loadMoreSearch() } }
                        } else null
                    )
                }
                tab == StickerTab.Recent -> when {
                    recentLoading && recent.isEmpty() -> PickerCenter { CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp) }
                    recentError != null && recent.isEmpty() -> PickerError(recentError.orEmpty(), onRetry = { recentRetry++ })
                    recent.isEmpty() -> PickerEmpty(stringResource(R.string.sticker_picker_empty))
                    else -> StickerGrid(
                        stickers = recent,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onClick = ::choose,
                        onLongClick = ::chooseOptions
                    )
                }
                else -> when {
                    setsLoading && sets.isEmpty() -> PickerCenter { CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp) }
                    setsError != null && sets.isEmpty() -> PickerError(setsError.orEmpty(), onRetry = { setsRetry++ })
                    sets.isEmpty() -> PickerEmpty(stringResource(R.string.sticker_picker_empty_sets))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(220.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sets, key = { it.id }) { summary ->
                            StickerSetRow(
                                summary = summary,
                                observeFile = observeFile,
                                downloadFile = downloadFile,
                                onClick = { openSet(summary) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerHeader(title: String, onBack: (() -> Unit)?, onClose: () -> Unit, onOpenGifs: (() -> Unit)?) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(start = 4.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textSecondary)
            }
        } else Spacer(Modifier.width(8.dp))
        Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onOpenGifs != null) {
            TextButton(onClick = onOpenGifs, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Animation, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.composer_type_gif), color = palette.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.sticker_picker_close), tint = palette.textSecondary)
        }
    }
}

@Composable
private fun PickerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else palette.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) palette.onAccent else palette.textPrimary, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun StickerGrid(
    stickers: List<StickerItem>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: (StickerItem) -> Unit,
    onLongClick: (StickerItem) -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(76.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(stickers, key = { it.key }) { item ->
            StickerCell(item, observeFile, downloadFile, onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { footer() }
        }
    }
}

@Composable
private fun StickerCell(
    item: StickerItem,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = beyGramPalette()
    val emoji = item.emoji.ifBlank { stringResource(R.string.media_kind_sticker) }
    val description = stringResource(R.string.sticker_picker_item_description, emoji)
    Box(
        Modifier.size(68.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.common_send),
                onLongClickLabel = stringResource(R.string.sticker_picker_send_options),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        StickerArtwork(item, Modifier.fillMaxSize(), observeFile, downloadFile)
        if (item.premium) {
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.sticker_picker_premium_badge),
                tint = palette.accent, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp))
        }
    }
}

/** Draws one sticker and downloads only its own preview. The caller owns the clickable surface. */
@Composable
private fun StickerArtwork(
    item: StickerItem,
    modifier: Modifier,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    val motion = systemMotionEnabled()
    val file by remember(item.fileId) { observeFile(item.fileId) }.collectAsStateWithLifecycle()
    val thumbnail = rememberOptionalFile(item.thumbnailFileId, observeFile)
    LaunchedEffect(item.fileId, item.thumbnailFileId, item.format) {
        item.thumbnailFileId?.let(downloadFile)
        // WebM stickers cannot be drawn by the picker; their still thumbnail is enough and avoids a video decoder.
        if (item.format != StickerFileFormat.Webm) downloadFile(item.fileId)
    }
    val emoji = item.emoji.ifBlank { stringResource(R.string.media_kind_sticker) }
    // A WebM/TGS thumbnail is not an image an ImageDecoder can open; only static thumbnails are drawn.
    val thumbnailPath = thumbnail.localPath.takeIf { item.thumbnailIsStatic }
    val fullPath = file.localPath
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val fallback: @Composable () -> Unit = {
            if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = 256)
            else Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
        when {
            fullPath != null && item.format == StickerFileFormat.Tgs ->
                TgsSticker(fullPath, Modifier.fillMaxSize(), playing = motion, fallback = fallback)
            fullPath != null && item.format != StickerFileFormat.Webm ->
                LocalMediaImage(fullPath, Modifier.fillMaxSize(), fit = true, maxDimension = 256)
            thumbnailPath != null ->
                LocalMediaImage(thumbnailPath, Modifier.fillMaxSize(), fit = true, maxDimension = 256)
            file.isDownloading || thumbnail.isDownloading ->
                CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
            else -> Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StickerSetRow(
    summary: StickerSetSummary,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.surface2).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cover = summary.cover
        if (cover != null) {
            StickerArtwork(cover, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), observeFile, downloadFile)
        } else Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(palette.accentSoft), contentAlignment = Alignment.Center) {
            Text(summary.title.take(1), color = palette.accentInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(summary.title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                summary.size.takeIf { it > 0 }?.let { stringResource(R.string.sticker_picker_set_size, it) },
                stringResource(R.string.sticker_picker_open_set)
            )
            Text(details.joinToString(" · "), color = palette.textSecondary, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PickerCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PickerEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = beyGramPalette().textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PickerError(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).errorAnnouncement(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(text, color = beyGramPalette().danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@Composable
private fun PickerMore(loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = beyGramPalette().accent, strokeWidth = 2.dp)
        else TextButton(onClick = onClick) { Text(stringResource(R.string.sticker_picker_load_more)) }
    }
}

/** Collects a nullable TDLib file; a missing thumbnail never changes the number of remembered states. */
@Composable
private fun rememberOptionalFile(fileId: Int?, observeFile: (Int) -> StateFlow<MediaFileState>): MediaFileState {
    val placeholder = remember { MutableStateFlow(MediaFileState(0)) }
    val flow = remember(fileId) { fileId?.let(observeFile) ?: placeholder }
    val state by flow.collectAsStateWithLifecycle()
    return state
}
