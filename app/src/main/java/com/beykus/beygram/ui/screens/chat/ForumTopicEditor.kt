package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForumTopicCreationColors
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** TDLib's createForumTopic and editForumTopic both accept at most 128 code points in a name. */
internal const val ForumTopicNameLimit = 128

/** A topic needs a name that is not blank and has at most [ForumTopicNameLimit] code points; an emoji counts as one. */
internal fun isForumTopicNameValid(name: String): Boolean =
    name.isNotBlank() && name.codePointCount(0, name.length) <= ForumTopicNameLimit

/**
 * The custom emoji to send with an edit. Null keeps the icon, so a rename never touches it. The General topic has no
 * editable icon, and choosing the icon the topic already has is not a change either.
 */
internal fun forumTopicIconUpdate(topic: ForumTopic, selectedEmojiId: Long): Long? {
    if (topic.isGeneral) return null
    val current = topic.icon?.customEmojiId ?: 0L
    return if (selectedEmojiId == current) null else selectedEmojiId
}

/**
 * The icon grid in order: "no icon" first, then the topic's own custom emoji when Telegram's defaults do not include it,
 * so an existing topic always shows the icon it has now.
 */
internal fun forumTopicIconOptions(current: Long, defaults: List<Long>): List<Long> =
    (listOf(0L) + listOfNotNull(current.takeIf { it != 0L }) + defaults).distinct()

/** The emoji of one installed set in the premium grid: the covers show until the set's own emoji have arrived. */
internal sealed interface CustomEmojiSetState {
    data object Loading : CustomEmojiSetState
    data object Failed : CustomEmojiSetState
    data class Loaded(val ids: List<Long>) : CustomEmojiSetState
}

/**
 * Asks for a set's emoji only when the editor asks for that set, which the grid does once the set's title is on screen.
 * At most [maxConcurrent] sets load at once. A loaded set is never asked again; a failed one is asked again the next time
 * its title comes into view.
 */
internal class CustomEmojiSetLoader(
    private val scope: CoroutineScope,
    private val maxConcurrent: Int = 2,
    initialLoaded: Map<Long, List<Long>> = emptyMap(),
    private val onLoaded: (Long, List<Long>) -> Unit = { _, _ -> },
    private val load: suspend (Long) -> Result<List<Long>>
) {
    private val permits = Semaphore(maxConcurrent)
    private val mutableStates = MutableStateFlow<Map<Long, CustomEmojiSetState>>(
        initialLoaded.mapValues { CustomEmojiSetState.Loaded(it.value) }
    )
    val states: StateFlow<Map<Long, CustomEmojiSetState>> = mutableStates.asStateFlow()

    /** Starts loading [setId] unless it is loading or loaded already. The outcome arrives through [states]. */
    fun request(setId: Long) {
        val current = mutableStates.value[setId]
        if (current == CustomEmojiSetState.Loading || current is CustomEmojiSetState.Loaded) return
        mutableStates.update { it + (setId to CustomEmojiSetState.Loading) }
        scope.launch {
            val outcome = try {
                permits.withPermit { load(setId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure<List<Long>>(error)
            }
            val next: CustomEmojiSetState = outcome.fold(
                onSuccess = {
                    onLoaded(setId, it)
                    CustomEmojiSetState.Loaded(it)
                },
                onFailure = { CustomEmojiSetState.Failed }
            )
            mutableStates.update { it + (setId to next) }
        }
    }
}

/** A row of the premium emoji grid: a set's title, or one custom emoji of a set, numbered from 1 within that set. */
internal sealed interface PremiumEmojiRow {
    /** Stable across loads, so a row keeps its place and its state when a set's covers give way to its emoji. */
    val key: String

    data class Header(val setId: Long, val title: String) : PremiumEmojiRow {
        override val key: String get() = "set-$setId"
    }

    data class Emoji(val setId: Long, val customEmojiId: Long, val number: Int) : PremiumEmojiRow {
        override val key: String get() = "emoji-$setId-$customEmojiId"
    }
}

/**
 * The grid rows of the installed sets in the order TDLib gave them. A set shows its covers until its own emoji have
 * loaded, and a set that failed keeps its covers, so a section is not empty while it is on its way.
 */
internal fun premiumEmojiRows(sets: List<CustomEmojiSetBrief>, states: Map<Long, CustomEmojiSetState>): List<PremiumEmojiRow> =
    buildList<PremiumEmojiRow> {
        sets.distinctBy { it.id }.forEach { set ->
            add(PremiumEmojiRow.Header(set.id, set.title))
            val emoji = (states[set.id] as? CustomEmojiSetState.Loaded)?.ids ?: set.covers
            emoji.distinct().forEachIndexed { index, customEmojiId ->
                add(PremiumEmojiRow.Emoji(set.id, customEmojiId, index + 1))
            }
        }
    }

/** The installed sets of a Premium account: still loading, failed, or the sets in TDLib's order. */
private sealed interface PremiumSetsState {
    data object Loading : PremiumSetsState
    data object Failed : PremiumSetsState
    data class Loaded(val sets: List<CustomEmojiSetBrief>) : PremiumSetsState
}

/** The default icons load when the editor opens; the grid shows its own state until they arrive. */
private sealed interface ForumIconsState {
    data object Loading : ForumIconsState
    data object Failed : ForumIconsState
    data class Loaded(val ids: List<Long>) : ForumIconsState
}

/**
 * The create and edit sheet of a forum topic: a live preview, the name and, for a new topic or any topic but General,
 * the colour and the icon. The input stays with the caller, so a failed save keeps what the user typed. [onSave] only
 * asks for the save; the caller reports the outcome. The sheet stays open, and cannot be dismissed, while [submitting].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ForumTopicEditorSheet(
    /** The topic being edited; null when a new topic is created. */
    topic: ForumTopic?,
    name: String,
    onNameChange: (String) -> Unit,
    colorArgb: Int,
    onColorChange: (Int) -> Unit,
    iconEmojiId: Long,
    onIconChange: (Long) -> Unit,
    loadDefaultIcons: suspend () -> Result<List<Long>>,
    /** Premium accounts can take the icon from their installed custom emoji sets; other accounts see a hint instead. */
    premium: Boolean,
    loadCustomEmojiSets: suspend () -> Result<List<CustomEmojiSetBrief>>,
    loadCustomEmojiSetIds: suspend (Long) -> Result<List<Long>>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    busy: Boolean,
    submitting: Boolean,
    error: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val creating = topic == null
    // The General topic keeps its server icon and colour; only its name can change.
    val iconEditable = topic?.isGeneral != true
    val nameLength = name.codePointCount(0, name.length)
    // A blank name is an error only after the user has typed, so a new topic does not open in the error state.
    var nameTouched by remember { mutableStateOf(false) }
    val nameError = nameLength > ForumTopicNameLimit || (nameTouched && name.isBlank())
    val busyNow = busy || submitting
    val canSave = !busyNow && isForumTopicNameValid(name)
    // The swipe veto below runs outside composition, so it reads the latest value through this delegate.
    val currentSubmitting by rememberUpdatedState(submitting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // A swipe must not hide the editor while the server still owns the save.
        confirmValueChange = { next -> next != SheetValue.Hidden || !currentSubmitting }
    )
    var iconsState by remember { mutableStateOf<ForumIconsState>(ForumIconsState.Loading) }
    var iconsAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(iconEditable, iconsAttempt) {
        if (!iconEditable) return@LaunchedEffect
        iconsState = ForumIconsState.Loading
        iconsState = loadDefaultIcons().fold(
            onSuccess = { ForumIconsState.Loaded(it) },
            onFailure = { ForumIconsState.Failed }
        )
    }
    // A new topic takes the colour picked here; an existing topic keeps the colour it was created with.
    val tileColor = if (topic == null) colorArgb else (topic.icon?.colorArgb ?: 0)
    val base = topic ?: ForumTopic(id = 0L, title = "")
    val previewTopic = if (iconEditable) base.withIcon(tileColor, iconEmojiId) else base
    val loadedIcons = (iconsState as? ForumIconsState.Loaded)?.ids ?: emptyList()
    val iconOptions = forumTopicIconOptions(current = topic?.icon?.customEmojiId ?: 0L, defaults = loadedIcons)
    val shownName = name.trim()
    val namePlaceholder = stringResource(R.string.forum_editor_name_placeholder)
    val loadingLabel = stringResource(R.string.forum_editor_icons_loading)

    BeyGramSheet(
        onDismissRequest = { if (!currentSubmitting) onDismiss() },
        sheetState = sheetState,
        title = stringResource(if (creating) R.string.forum_create else R.string.forum_edit)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ForumTopicIcon(
                    topic = previewTopic,
                    size = 64.dp,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = shownName.ifEmpty { namePlaceholder },
                    color = if (shownName.isEmpty()) palette.textSecondary else palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { nameTouched = true; onNameChange(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.forum_topic_name)) },
                isError = nameError,
                supportingText = {
                    Text(stringResource(R.string.forum_topic_name_counter, nameLength, ForumTopicNameLimit))
                }
            )
            if (iconEditable && creating) {
                ForumTopicEditorLabel(stringResource(R.string.forum_editor_color))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ForumTopicCreationColors.forEachIndexed { index, rgb ->
                        ForumTopicColorSwatch(
                            rgb = rgb,
                            selected = rgb == colorArgb,
                            description = stringResource(R.string.forum_editor_color_n, index + 1),
                            onSelect = { onColorChange(rgb) }
                        )
                    }
                }
            }
            if (iconEditable) {
                ForumTopicEditorLabel(stringResource(R.string.forum_editor_icon))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconOptions.forEachIndexed { index, emojiId ->
                        ForumTopicIconCell(
                            topic = base.withIcon(tileColor, emojiId),
                            selected = emojiId == iconEmojiId,
                            description = if (emojiId == 0L) {
                                stringResource(R.string.forum_editor_no_icon)
                            } else {
                                stringResource(R.string.forum_editor_icon_n, index)
                            },
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            observeCustomEmoji = observeCustomEmoji,
                            onSelect = { onIconChange(emojiId) }
                        )
                    }
                }
                when (iconsState) {
                    ForumIconsState.Loading -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(20.dp)
                            .semantics { contentDescription = loadingLabel },
                        color = palette.accent,
                        strokeWidth = 2.dp
                    )
                    ForumIconsState.Failed -> Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.forum_editor_icons_failed),
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                        TextButton(onClick = { iconsAttempt++ }) {
                            Text(
                                stringResource(R.string.common_retry),
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is ForumIconsState.Loaded -> Unit
                }
                if (premium) {
                    PremiumEmojiSection(
                        base = base,
                        tileColor = tileColor,
                        selectedEmojiId = iconEmojiId,
                        onPick = onIconChange,
                        loadSets = loadCustomEmojiSets,
                        loadSetIds = loadCustomEmojiSetIds,
                        observeFile = observeFile,
                        downloadFile = downloadFile,
                        observeCustomEmoji = observeCustomEmoji
                    )
                } else {
                    Text(
                        stringResource(R.string.forum_editor_premium_emoji_hint),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.accent.copy(alpha = 0.55f),
                    disabledContentColor = palette.onAccent.copy(alpha = 0.75f)
                )
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = palette.onAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold)
            }
            if (error != null) {
                Text(
                    error,
                    color = palette.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).errorAnnouncement()
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = !busyNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.common_cancel),
                    color = if (busyNow) palette.textSecondary else palette.accent
                )
            }
        }
    }
}

/** The same topic with [customEmojiId] as its icon in [colorArgb]; the tile then draws it exactly as a list row would. */
private fun ForumTopic.withIcon(colorArgb: Int, customEmojiId: Long): ForumTopic =
    copy(icon = com.beykus.beygram.domain.model.ForumTopicIcon(colorArgb = colorArgb, customEmojiId = customEmojiId))

@Composable
private fun ForumTopicEditorLabel(text: String) {
    Text(
        text,
        color = beyGramPalette().textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp).semantics { heading() }
    )
}

/** A round colour choice: a 48dp target around a 38dp disc, with a check and an accent ring when it is chosen. */
@Composable
private fun ForumTopicColorSwatch(
    rgb: Int,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit
) {
    val palette = beyGramPalette()
    val fill = forumTopicIconColor(rgb) ?: palette.accentSoft
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (selected) Modifier.border(2.dp, palette.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = forumTopicIconInk(fill),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** One icon choice, drawn as the topic list draws its tile, with an accent ring when it is the chosen icon. */
@Composable
private fun ForumTopicIconCell(
    topic: ForumTopic,
    selected: Boolean,
    description: String,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onSelect: () -> Unit
) {
    val palette = beyGramPalette()
    val ringShape = styledShape(15.dp)
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        ForumTopicIcon(
            topic = topic,
            size = 40.dp,
            observeFile = observeFile,
            downloadFile = downloadFile,
            observeCustomEmoji = observeCustomEmoji
        )
        if (selected) {
            Box(Modifier.size(46.dp).border(2.dp, palette.accent, ringShape))
        }
    }
}

/**
 * The emoji of the account's installed sets, each set under its title, for a Premium account. The list of sets loads once
 * the section is shown; a set's own emoji load only when its title scrolls into view.
 */
@Composable
private fun PremiumEmojiSection(
    base: ForumTopic,
    tileColor: Int,
    selectedEmojiId: Long,
    onPick: (Long) -> Unit,
    loadSets: suspend () -> Result<List<CustomEmojiSetBrief>>,
    loadSetIds: suspend (Long) -> Result<List<Long>>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>
) {
    val palette = beyGramPalette()
    val latestLoadSets by rememberUpdatedState(loadSets)
    val latestLoadSetIds by rememberUpdatedState(loadSetIds)
    var setsState by remember { mutableStateOf<PremiumSetsState>(PremiumSetsState.Loading) }
    var setsAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(setsAttempt) {
        setsState = PremiumSetsState.Loading
        setsState = latestLoadSets().fold(
            onSuccess = { PremiumSetsState.Loaded(it) },
            onFailure = { PremiumSetsState.Failed }
        )
    }
    val scope = rememberCoroutineScope()
    val setLoader = remember(scope) { CustomEmojiSetLoader(scope) { setId -> latestLoadSetIds(setId) } }
    val setStates by setLoader.states.collectAsStateWithLifecycle()
    val loadingLabel = stringResource(R.string.forum_editor_premium_loading)
    when (val state = setsState) {
        PremiumSetsState.Loading -> CircularProgressIndicator(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(20.dp)
                .semantics { contentDescription = loadingLabel },
            color = palette.accent,
            strokeWidth = 2.dp
        )
        PremiumSetsState.Failed -> Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.forum_editor_premium_failed),
                color = palette.textSecondary,
                fontSize = 13.sp
            )
            TextButton(onClick = { setsAttempt++ }) {
                Text(
                    stringResource(R.string.common_retry),
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        is PremiumSetsState.Loaded -> {
            val rows = remember(state.sets, setStates) { premiumEmojiRows(state.sets, setStates) }
            if (rows.isNotEmpty()) {
                PremiumEmojiGrid(
                    rows = rows,
                    base = base,
                    tileColor = tileColor,
                    selectedEmojiId = selectedEmojiId,
                    onRequestSet = { setId -> setLoader.request(setId) },
                    onPick = onPick,
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji
                )
            }
        }
    }
}

/**
 * The premium emoji as one lazy grid, so only the rows on screen are composed. A set's title is a full-width row and its
 * emoji follow it; composing the title is what asks for that set's emoji.
 */
@Composable
private fun PremiumEmojiGrid(
    rows: List<PremiumEmojiRow>,
    base: ForumTopic,
    tileColor: Int,
    selectedEmojiId: Long,
    onRequestSet: (Long) -> Unit,
    onPick: (Long) -> Unit,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = rows.size,
            key = { index -> rows[index].key },
            span = { index -> GridItemSpan(if (rows[index] is PremiumEmojiRow.Header) maxLineSpan else 1) },
            contentType = { index -> if (rows[index] is PremiumEmojiRow.Header) "set" else "emoji" }
        ) { index ->
            when (val row = rows[index]) {
                is PremiumEmojiRow.Header -> {
                    LaunchedEffect(row.setId) { onRequestSet(row.setId) }
                    ForumTopicEditorLabel(row.title)
                }
                is PremiumEmojiRow.Emoji -> ForumTopicIconCell(
                    topic = base.withIcon(tileColor, row.customEmojiId),
                    selected = row.customEmojiId == selectedEmojiId,
                    description = stringResource(R.string.forum_editor_premium_icon_n, row.number),
                    observeFile = observeFile,
                    downloadFile = downloadFile,
                    observeCustomEmoji = observeCustomEmoji,
                    onSelect = { onPick(row.customEmojiId) }
                )
            }
        }
    }
}
