package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class GifTab { Recent, Saved }

/**
 * GIF picker: GIFs recently seen in the account's chats, saved GIFs, and a text search over the same message history.
 * Only thumbnails are downloaded; tapping adds the GIF to the pending attachments by TDLib file id, where the usual
 * caption limit, reply, topic, scheduling and silent-send options apply.
 */
@Composable
internal fun GifPickerPanel(
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onPick: (AnimationItem) -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(GifTab.Recent) }
    val clearSearch: (@Composable () -> Unit)? = if (query.isNotEmpty()) {
        {
            IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.components_clear_search), modifier = Modifier.size(18.dp))
            }
        }
    } else null

    var recent by remember { mutableStateOf(AnimationPage()) }
    var recentLoading by remember { mutableStateOf(true) }
    var recentError by remember { mutableStateOf<String?>(null) }
    var recentRetry by remember { mutableIntStateOf(0) }

    var saved by remember { mutableStateOf<List<AnimationItem>>(emptyList()) }
    var savedLoading by remember { mutableStateOf(true) }
    var savedError by remember { mutableStateOf<String?>(null) }
    var savedRetry by remember { mutableIntStateOf(0) }

    var search by remember { mutableStateOf(AnimationPage()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchRetry by remember { mutableIntStateOf(0) }

    LaunchedEffect(recentRetry) {
        recentLoading = true
        recentError = null
        conversation.recentAnimations()
            .onSuccess { recent = it }
            .onFailure { recentError = it.userMessage(context, R.string.gif_picker_load_failed) }
        recentLoading = false
    }
    LaunchedEffect(savedRetry) {
        savedLoading = true
        savedError = null
        conversation.savedAnimations()
            .onSuccess { saved = it }
            .onFailure { savedError = it.userMessage(context, R.string.gif_picker_load_failed) }
        savedLoading = false
    }
    LaunchedEffect(query, searchRetry) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            search = AnimationPage()
            searchError = null
            searchLoading = false
            return@LaunchedEffect
        }
        delay(350)
        searchLoading = true
        searchError = null
        conversation.searchAnimations(needle)
            .onSuccess { search = it }
            .onFailure { searchError = it.userMessage(context, R.string.gif_picker_load_failed); search = AnimationPage() }
        searchLoading = false
    }

    fun loadMoreRecent() {
        val offset = recent.nextOffset.takeIf { it.isNotBlank() } ?: return
        if (recentLoading) return
        scope.launch {
            recentLoading = true
            recentError = null
            conversation.recentAnimations(offset)
                .onSuccess { page ->
                    recent = AnimationPage(mergeAnimationPages(recent.animations, page), page.nextOffset)
                }
                .onFailure { recentError = it.userMessage(context, R.string.gif_picker_load_failed) }
            recentLoading = false
        }
    }

    fun loadMoreSearch() {
        val needle = query.trim()
        val offset = search.nextOffset.takeIf { it.isNotBlank() } ?: return
        if (needle.isEmpty() || searchLoading) return
        scope.launch {
            searchLoading = true
            searchError = null
            conversation.searchAnimations(needle, offset)
                .onSuccess { page ->
                    if (query.trim() == needle) search = AnimationPage(mergeAnimationPages(search.animations, page), page.nextOffset)
                }
                .onFailure { if (query.trim() == needle) searchError = it.userMessage(context, R.string.gif_picker_load_failed) }
            searchLoading = false
        }
    }

    Column(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 340.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gif_picker_title), color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.gif_picker_close), tint = palette.textSecondary)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text(stringResource(R.string.gif_picker_search), fontSize = 14.sp) },
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
        if (query.isBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GifTabButton(stringResource(R.string.gif_picker_recent), tab == GifTab.Recent) { tab = GifTab.Recent }
                GifTabButton(stringResource(R.string.gif_picker_saved), tab == GifTab.Saved) { tab = GifTab.Saved }
            }
        } else {
            Text(stringResource(R.string.gif_picker_search_note), color = palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                query.isNotBlank() -> when {
                    searchLoading && search.animations.isEmpty() -> GifCenter { CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp) }
                    searchError != null && search.animations.isEmpty() -> GifError(searchError.orEmpty(), onRetry = { searchRetry++ })
                    search.animations.isEmpty() && !searchLoading -> GifEmpty(stringResource(R.string.gif_picker_empty_search))
                    else -> GifGrid(
                        animations = search.animations,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onPick = onPick,
                        footer = if (search.nextOffset.isNotBlank()) { { GifMore(searchLoading) { loadMoreSearch() } } } else null
                    )
                }
                tab == GifTab.Recent -> when {
                    recentLoading && recent.animations.isEmpty() -> GifCenter { CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp) }
                    recentError != null && recent.animations.isEmpty() -> GifError(recentError.orEmpty(), onRetry = { recentRetry++ })
                    recent.animations.isEmpty() -> GifEmpty(stringResource(R.string.gif_picker_empty_recent))
                    else -> GifGrid(
                        animations = recent.animations,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        onPick = onPick,
                        footer = if (recent.nextOffset.isNotBlank()) { { GifMore(recentLoading) { loadMoreRecent() } } } else null
                    )
                }
                else -> when {
                    savedLoading && saved.isEmpty() -> GifCenter { CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp) }
                    savedError != null && saved.isEmpty() -> GifError(savedError.orEmpty(), onRetry = { savedRetry++ })
                    saved.isEmpty() -> GifEmpty(stringResource(R.string.gif_picker_empty_saved))
                    else -> GifGrid(animations = saved, observeFile = observeFile, downloadFile = downloadFile, onPick = onPick)
                }
            }
        }
    }
}

@Composable
private fun GifTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun GifGrid(
    animations: List<AnimationItem>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onPick: (AnimationItem) -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(124.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(animations, key = { it.key }) { item ->
            GifCell(item, observeFile, downloadFile) { onPick(item) }
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { footer() }
        }
    }
}

@Composable
private fun GifCell(
    item: AnimationItem,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val name = item.fileName.ifBlank { stringResource(R.string.media_kind_animation) }
    val description = stringResource(R.string.gif_picker_item_description, name)
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))
            .background(palette.surface2)
            .combinedClickable(role = Role.Button, onClickLabel = stringResource(R.string.gif_picker_add), onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        GifArtwork(item, Modifier.fillMaxSize(), observeFile, downloadFile)
        Text(
            stringResource(R.string.media_kind_animation),
            color = palette.onAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                .clip(RoundedCornerShape(6.dp)).background(palette.accent.copy(alpha = 0.85f)).padding(horizontal = 5.dp, vertical = 2.dp)
        )
        if (item.fileName.isNotBlank()) {
            Text(item.fileName, color = palette.textPrimary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(palette.surface.copy(alpha = 0.82f)).padding(horizontal = 6.dp, vertical = 3.dp))
        }
    }
}

/** Only the small thumbnail is downloaded; an animated preview is intentionally not started, so reduced-motion users see a still. */
@Composable
private fun GifArtwork(
    item: AnimationItem,
    modifier: Modifier,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    val placeholder = remember { MutableStateFlow(MediaFileState(0)) }
    val flow = remember(item.thumbnailFileId) { item.thumbnailFileId?.let(observeFile) ?: placeholder }
    val thumbnail by flow.collectAsStateWithLifecycle()
    LaunchedEffect(item.thumbnailFileId) { item.thumbnailFileId?.let(downloadFile) }
    val path = thumbnail.localPath
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            path != null -> LocalMediaImage(path, Modifier.fillMaxSize(), fit = true, maxDimension = 320)
            item.minithumbnail != null -> MinithumbnailImage(item.minithumbnail, Modifier.fillMaxSize())
            thumbnail.isDownloading -> CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
            else -> Icon(Icons.Rounded.Animation, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun GifCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun GifEmpty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = beyGramPalette().textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GifError(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).errorAnnouncement(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(text, color = beyGramPalette().danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@Composable
private fun GifMore(loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = beyGramPalette().accent, strokeWidth = 2.dp)
        else TextButton(onClick = onClick) { Text(stringResource(R.string.sticker_picker_load_more)) }
    }
}
