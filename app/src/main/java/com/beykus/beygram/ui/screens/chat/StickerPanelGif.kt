package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.Animatable
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassSheet
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.beykus.beygram.ui.motion.rememberMotionInstant
import com.beykus.beygram.ui.motion.scrollToItemRespectingMotion
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.StickerShelfVersions
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberAutoplayEligible
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun GifSectionStrip(favorites: Boolean, onRecent: () -> Unit, onFavorites: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionPill(
            label = stringResource(R.string.gif_panel_recent),
            icon = Icons.Rounded.History,
            selected = !favorites,
            onClick = onRecent
        )
        SectionPill(
            label = stringResource(R.string.gif_panel_saved),
            icon = Icons.Rounded.Star,
            selected = favorites,
            onClick = onFavorites
        )
    }
}

@Composable
internal fun GifContent(
    view: StickerPanelView,
    recentState: PickerLoad<AnimationPage>,
    savedState: PickerLoad<List<AnimationItem>>,
    searchState: PickerLoad<AnimationPage>,
    recentLoadingMore: Boolean,
    recentMoreError: String?,
    searchLoadingMore: Boolean,
    searchMoreError: String?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onRetryRecent: () -> Unit,
    onRetrySaved: () -> Unit,
    onRetrySearch: () -> Unit,
    onLoadMoreRecent: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onSend: (AnimationItem) -> Unit,
    onPreview: (AnimationItem) -> Unit
) {
    val emptyRecent = stringResource(R.string.gif_panel_empty_recent)
    val emptySaved = stringResource(R.string.gif_panel_empty_saved)
    val emptySearch = stringResource(R.string.gif_panel_empty_search)
    Column(Modifier.fillMaxSize()) {
        if (view.searchActive && view.query.isNotBlank()) {
            Text(
                stringResource(R.string.gif_panel_search_note),
                color = beyGramPalette().textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                view.query.isNotBlank() -> PickerLoadContent(searchState, onRetrySearch) { page ->
                    if (page.animations.isEmpty()) PanelEmpty(emptySearch)
                    else {
                        val footer: (@Composable () -> Unit)? = when {
                            searchMoreError != null -> {
                                { PaginationError(searchMoreError, onLoadMoreSearch) }
                            }
                            page.nextOffset.isNotBlank() -> {
                                { PickerMore(searchLoadingMore, onLoadMoreSearch) }
                            }
                            else -> null
                        }
                        GifGrid(
                            animations = page.animations,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            onSend = onSend,
                            onPreview = onPreview,
                            footer = footer
                        )
                    }
                }
                view.section == StickerPanelSection.Favorites -> PickerLoadContent(savedState, onRetrySaved) { animations ->
                    if (animations.isEmpty()) PanelEmpty(emptySaved)
                    else GifGrid(animations, observeFile, downloadFile, onSend, onPreview)
                }
                else -> PickerLoadContent(recentState, onRetryRecent) { page ->
                    if (page.animations.isEmpty()) PanelEmpty(emptyRecent)
                    else {
                        val footer: (@Composable () -> Unit)? = when {
                            recentMoreError != null -> {
                                { PaginationError(recentMoreError, onLoadMoreRecent) }
                            }
                            page.nextOffset.isNotBlank() -> {
                                { PickerMore(recentLoadingMore, onLoadMoreRecent) }
                            }
                            else -> null
                        }
                        GifGrid(
                            animations = page.animations,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            onSend = onSend,
                            onPreview = onPreview,
                            footer = footer
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GifGrid(
    animations: List<AnimationItem>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: (AnimationItem) -> Unit,
    onPreview: (AnimationItem) -> Unit,
    footer: (@Composable () -> Unit)? = null
) {
    val gridState = rememberLazyStaggeredGridState()
    // Search pages and recents can repeat an item; a duplicate lazy key would crash the grid.
    val uniqueAnimations = remember(animations) { animations.distinctBy { it.key } }
    val firstKey = uniqueAnimations.firstOrNull()?.key
    var previousFirst by rememberSaveable { mutableStateOf(firstKey) }
    LaunchedEffect(firstKey) {
        if (firstKey != null && previousFirst != firstKey) {
            gridState.scrollToItem(0)
            previousFirst = firstKey
        }
    }
    val scrolling by remember(gridState) { derivedStateOf { gridState.isScrollInProgress } }
    val parentPolicy = LocalEmojiAnimationPolicy.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalEmojiAnimationPolicy provides parentPolicy.copy(
            paused = parentPolicy.paused || scrolling,
            priority = EmojiAnimationPolicy.PRIORITY_GRID
        )
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(132.dp),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(StickerPickerMetrics.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalItemSpacing = 6.dp
        ) {
            staggeredItems(uniqueAnimations, key = { it.key }) { item ->
                GifPanelCell(
                    item = item,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    onSend = { onSend(item) },
                    onPreview = { onPreview(item) }
                )
            }
            if (footer != null) {
                item(span = StaggeredGridItemSpan.FullLine, key = "footer") { footer() }
            }
        }
    }
}

@Composable
internal fun GifPanelCell(
    item: AnimationItem,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onSend: () -> Unit,
    onPreview: () -> Unit
) {
    val palette = beyGramPalette()
    val name = item.fileName.ifBlank { stringResource(R.string.gif_panel_label) }
    val description = stringResource(R.string.gif_panel_item_description, name)
    Box(
        Modifier.fillMaxWidth().aspectRatio(gifPickerAspectRatio(item)).clip(styledShape(12.dp))
            .background(palette.surface2)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.gif_panel_send),
                onLongClickLabel = stringResource(R.string.gif_panel_preview_title),
                onClick = onSend,
                onLongClick = onPreview
            )
            .semantics { contentDescription = description }
    ) {
        // The tile shows only the GIF itself: a repeated GIF badge and duration on every tile covered the artwork. Both
        // remain in the long-press preview and in the accessibility description.
        GifPanelArtwork(item, Modifier.fillMaxSize(), observeFile, downloadFile)
    }
}

/** Only the small thumbnail is downloaded; an animated preview is intentionally not started here. */
@Composable
internal fun GifPanelArtwork(
    item: AnimationItem,
    modifier: Modifier,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val animPolicy = LocalEmojiAnimationPolicy.current
    val thumbnail = rememberPanelFile(item.thumbnailFileId, observeFile)
    // Use a cached full animation only if already present; never download every GIF merely to draw the grid.
    val fullFile = rememberPanelFile(item.fileId, observeFile)
    LaunchedEffect(item.thumbnailFileId) { item.thumbnailFileId?.let(downloadFile) }
    val path = thumbnail.localPath ?: fullFile.localPath
    val canAnimate = !animPolicy.paused
    // A GIF animates only after it has been on screen for the autoplay dwell time; until then its first frame shows.
    val autoplay = rememberAutoplayEligible(enabled = canAnimate)
    val wantsAnimation = canAnimate && autoplay.eligible.value
    val budget = remember(context) { com.beykus.beygram.data.media.GifAnimationBudget.default(context) }
    val slotState = remember(budget) {
        mutableStateOf<com.beykus.beygram.data.media.GifAnimationHandle?>(null)
    }
    androidx.compose.runtime.DisposableEffect(budget, wantsAnimation, animPolicy.priority) {
        if (!wantsAnimation) {
            slotState.value = null
            onDispose {}
        } else {
            val acquired = budget.acquire(animPolicy.priority)
            slotState.value = acquired
            onDispose {
                slotState.value = null
                budget.release(acquired)
            }
        }
    }
    val slotGranted = slotState.value?.granted?.value == true
    Box(modifier.then(autoplay.modifier), contentAlignment = Alignment.Center) {
        when {
            path != null -> LocalMediaImage(
                path, Modifier.fillMaxSize(), fit = true, maxDimension = 320,
                allowVideoFrame = true,
                fallbackVideoPath = if (path != fullFile.localPath) fullFile.localPath else null,
                fallbackMinithumbnail = item.minithumbnail,
                animate = wantsAnimation && slotGranted
            )
            item.minithumbnail != null -> MinithumbnailImage(item.minithumbnail, Modifier.fillMaxSize())
            thumbnail.isDownloading -> CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
            else -> Icon(Icons.Rounded.Animation, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(28.dp))
        }
    }
}

