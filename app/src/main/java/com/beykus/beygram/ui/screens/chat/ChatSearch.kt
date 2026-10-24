package com.beykus.beygram.ui.screens.chat

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.domain.model.ChatSearchFilter
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.model.exclusiveEndOfDayEpochSeconds
import com.beykus.beygram.domain.model.searchBoundaryDate
import com.beykus.beygram.domain.model.startOfDayEpochSeconds
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** A sender that can be chosen in in-chat search: only users, because TDLib's sender filter is a user here. */
internal data class ChatSearchSender(val userId: Long, val name: String)

/** Distinct known senders from the loaded history, enough for a picker without another TDLib request. */
internal fun chatSearchSenders(messages: List<ChatMessage>): List<ChatSearchSender> =
    messages.asSequence()
        .filter { it.senderId > 0L }
        .distinctBy { it.senderId }
        .map { ChatSearchSender(it.senderId, it.senderName.ifBlank { it.senderId.toString() }) }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
        .toList()

/** Primitive-only saver: filters survive process recreation together with the search query. */
internal val ChatSearchFilterSaver = listSaver<ChatSearchFilter, Any>(
    save = { listOf(it.senderId ?: 0L, it.kind?.name.orEmpty(), it.fromDateSeconds ?: 0L, it.toDateSeconds ?: 0L) },
    restore = { saved ->
        ChatSearchFilter(
            senderId = (saved.getOrNull(0) as? Long)?.takeIf { it > 0L },
            kind = (saved.getOrNull(1) as? String)?.takeIf { it.isNotEmpty() }
                ?.let { name -> SharedMediaKind.entries.firstOrNull { it.name == name } },
            fromDateSeconds = (saved.getOrNull(2) as? Long)?.takeIf { it > 0L },
            toDateSeconds = (saved.getOrNull(3) as? Long)?.takeIf { it > 0L }
        )
    }
)

/** Строка поиска вместо заголовка чата: «назад» закрывает поиск, поле вводит запрос. */
@Composable
internal fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ChatSearchFilter,
    senders: List<ChatSearchSender>,
    onFilterChange: (ChatSearchFilter) -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    var filterDialogOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.search_close_description), tint = palette.textPrimary) }
            Box(Modifier.weight(1f)) { SearchField(query, onQueryChange, placeholder = stringResource(R.string.search_placeholder)) }
            IconButton(onClick = { filterDialogOpen = true }) {
                Icon(
                    Icons.Rounded.Tune,
                    stringResource(R.string.search_filters_description),
                    tint = if (filter.isActive) palette.accent else palette.textSecondary
                )
            }
        }
        if (filter.isActive) {
            ActiveChatSearchFilters(filter, senders, onReset = { onFilterChange(ChatSearchFilter()) })
        }
        HorizontalDivider(color = palette.divider)
    }
    if (filterDialogOpen) {
        ChatSearchFilterDialog(
            filter = filter,
            senders = senders,
            onDismiss = { filterDialogOpen = false },
            onApply = { changed -> onFilterChange(changed); filterDialogOpen = false }
        )
    }
}

/** Compact summary under the field: every active criterion stays visible without reopening the dialog. */
@Composable
private fun ActiveChatSearchFilters(filter: ChatSearchFilter, senders: List<ChatSearchSender>, onReset: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        filter.senderId?.let { id ->
            val name = senders.firstOrNull { it.userId == id }?.name ?: id.toString()
            ChatSearchFilterPill(stringResource(R.string.search_filter_sender_value, name))
        }
        filter.kind?.let { kind -> ChatSearchFilterPill(stringResource(chatSearchKindLabel(kind))) }
        if (filter.hasDateRange) ChatSearchFilterPill(filterDateLabel(filter))
        TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(stringResource(R.string.search_filter_reset), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ChatSearchFilterPill(text: String) {
    val palette = beyGramPalette()
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(palette.accentSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = palette.accentInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChatSearchFilterDialog(
    filter: ChatSearchFilter,
    senders: List<ChatSearchSender>,
    onDismiss: () -> Unit,
    onApply: (ChatSearchFilter) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    var draft by remember(filter) { mutableStateOf(filter) }
    var senderMenu by remember { mutableStateOf(false) }

    fun openDatePicker(endExclusive: Boolean) {
        val initial = when {
            endExclusive -> draft.toDateSeconds?.let { searchBoundaryDate(it, true, zone) }
            else -> draft.fromDateSeconds?.let { searchBoundaryDate(it, false, zone) }
        } ?: LocalDate.now(zone)
        DatePickerDialog(context, { _, year, month, day ->
            val picked = LocalDate.of(year, month + 1, day)
            draft = if (endExclusive) draft.copy(toDateSeconds = exclusiveEndOfDayEpochSeconds(picked, zone))
            else draft.copy(fromDateSeconds = startOfDayEpochSeconds(picked, zone))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    val senderLabel = draft.senderId?.let { id -> senders.firstOrNull { it.userId == id }?.name ?: id.toString() }
        ?: stringResource(R.string.search_filter_any_sender)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_filters_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SearchFilterSection(stringResource(R.string.search_filter_sender))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { senderMenu = true },
                        enabled = senders.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(senderLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = palette.textSecondary)
                    }
                    DropdownMenu(expanded = senderMenu, onDismissRequest = { senderMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.search_filter_any_sender)) },
                            onClick = { draft = draft.copy(senderId = null); senderMenu = false }
                        )
                        senders.forEach { sender ->
                            DropdownMenuItem(
                                text = { Text(sender.name) },
                                onClick = { draft = draft.copy(senderId = sender.userId); senderMenu = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SearchFilterSection(stringResource(R.string.search_filter_type))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatSearchFilterChip(
                        label = stringResource(R.string.search_filter_all_types),
                        selected = draft.kind == null,
                        onClick = { draft = draft.copy(kind = null) }
                    )
                    CHAT_SEARCH_KINDS.forEach { kind ->
                        ChatSearchFilterChip(
                            label = stringResource(chatSearchKindLabel(kind)),
                            selected = draft.kind == kind,
                            onClick = { draft = draft.copy(kind = kind) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SearchFilterSection(stringResource(R.string.search_filter_period))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatSearchDateButton(
                        label = stringResource(R.string.search_filter_from),
                        value = draft.fromDateSeconds?.let { formatSearchFilterDate(it, endExclusive = false) }
                            ?: stringResource(R.string.search_filter_any_date),
                        onClick = { openDatePicker(endExclusive = false) },
                        modifier = Modifier.weight(1f)
                    )
                    ChatSearchDateButton(
                        label = stringResource(R.string.search_filter_to),
                        value = draft.toDateSeconds?.let { formatSearchFilterDate(it, endExclusive = true) }
                            ?: stringResource(R.string.search_filter_any_date),
                        onClick = { openDatePicker(endExclusive = true) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!draft.hasValidDateRange) {
                    Text(
                        stringResource(R.string.search_filter_date_invalid),
                        color = palette.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                TextButton(
                    onClick = { draft = ChatSearchFilter() },
                    enabled = draft.isActive,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.search_filter_reset))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(draft) },
                enabled = draft.hasValidDateRange
            ) { Text(stringResource(R.string.search_filter_apply), color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SearchFilterSection(title: String) {
    Text(
        title,
        color = beyGramPalette().textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ChatSearchFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else palette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) palette.onAccent else palette.textPrimary, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ChatSearchDateButton(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    val palette = beyGramPalette()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = palette.textSecondary, fontSize = 11.sp)
            Text(value, color = palette.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Результаты поиска поверх переписки: совпадения подсвечены, страницы догружаются по кнопке. */
@Composable
internal fun ChatSearchResults(
    query: String,
    filter: ChatSearchFilter,
    results: List<ChatMessage>,
    totalCount: Int?,
    busy: Boolean,
    error: String?,
    hasMore: Boolean,
    onOpen: (ChatMessage) -> Unit,
    onMore: () -> Unit,
    onResetFilters: () -> Unit
) {
    val palette = beyGramPalette()
    val needle = query.trim()
    val criteriaActive = needle.isNotEmpty() || filter.isActive
    Box(Modifier.fillMaxSize().background(palette.screen).imePadding()) {
        when {
            !criteriaActive -> SearchNote(stringResource(R.string.search_type_to_search), palette.textSecondary)
            busy && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            results.isEmpty() -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            stringResource(
                                when {
                                    hasMore -> R.string.search_no_matches_page
                                    filter.isActive -> R.string.search_no_filter_results
                                    else -> R.string.search_nothing_found
                                }
                            ),
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (filter.isActive) {
                            TextButton(onClick = onResetFilters) { Text(stringResource(R.string.search_filter_reset)) }
                        }
                    }
                }
                if (hasMore) SearchMoreButton(busy, onMore)
                error?.let { message ->
                    Text(message, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).errorAnnouncement())
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                item(key = "search_count") {
                    val shownCount = totalCount ?: results.size
                    Text(
                        stringResource(if (totalCount != null) R.string.search_results_count else R.string.search_results_loaded, shownCount),
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
                items(results, key = { it.id }) { message -> SearchResultRow(message, needle) { onOpen(message) } }
                if (hasMore) item(key = "search_more") { SearchMoreButton(busy, onMore) }
                error?.let { message ->
                    item(key = "search_error") { Text(message, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).errorAnnouncement()) }
                }
            }
        }
    }
}

@Composable
private fun SearchMoreButton(busy: Boolean, onMore: () -> Unit) {
    TextButton(onClick = onMore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) stringResource(R.string.search_loading_more) else stringResource(R.string.search_show_more))
    }
}

@Composable
private fun SearchResultRow(message: ChatMessage, query: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (message.isOutgoing) stringResource(R.string.search_you) else message.senderName, color = palette.accentInk, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(formatSearchDate(message.dateSeconds), color = palette.textSecondary, fontSize = 12.sp)
        }
        Text(
            highlightMatches(snippet(message.text.ifBlank { message.summary() }, query), query, palette.accentInk),
            color = palette.textPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchNote(text: String, color: Color, announce: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, textAlign = TextAlign.Center, modifier = if (announce) Modifier.errorAnnouncement() else Modifier)
    }
}

internal fun formatSearchDate(seconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(seconds * 1000))

/** A date-range boundary is stored as an exclusive next-day start; show the last day it actually includes. */
private fun formatSearchFilterDate(seconds: Long, endExclusive: Boolean): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date((seconds - if (endExclusive) 1L else 0L) * 1000L))

@Composable
private fun filterDateLabel(filter: ChatSearchFilter): String {
    val from = filter.fromDateSeconds
    val to = filter.toDateSeconds
    return when {
        from != null && to != null -> stringResource(
            R.string.search_filter_date_range,
            formatSearchFilterDate(from, endExclusive = false),
            formatSearchFilterDate(to, endExclusive = true)
        )
        from != null -> stringResource(R.string.search_filter_date_from, formatSearchFilterDate(from, endExclusive = false))
        to != null -> stringResource(R.string.search_filter_date_to, formatSearchFilterDate(to, endExclusive = true))
        else -> ""
    }
}

private val CHAT_SEARCH_KINDS = listOf(SharedMediaKind.Media, SharedMediaKind.Files, SharedMediaKind.Links, SharedMediaKind.Voice)

private fun chatSearchKindLabel(kind: SharedMediaKind): Int = when (kind) {
    SharedMediaKind.Media -> R.string.search_filter_media
    SharedMediaKind.Files -> R.string.search_filter_files
    SharedMediaKind.Links -> R.string.search_filter_links
    SharedMediaKind.Voice -> R.string.search_filter_voice
    SharedMediaKind.Music -> R.string.search_filter_music
}

private const val SNIPPET_LEAD = 40

/** Кусок текста вокруг первого совпадения: иначе в длинном сообщении оно оказалось бы за краем строки. */
private fun snippet(text: String, query: String): String {
    val flat = text.replace('\n', ' ')
    val at = flat.indexOf(query, ignoreCase = true)
    if (at <= SNIPPET_LEAD) return flat
    var start = at - SNIPPET_LEAD
    if (Character.isLowSurrogate(flat[start])) start--
    return "…" + flat.substring(start)
}

/** Совпадения с запросом выделяются цветом акцента и полужирным начертанием. */
private fun highlightMatches(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isEmpty()) return@buildAnnotatedString
    var from = text.indexOf(query, ignoreCase = true)
    while (from >= 0) {
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), from, from + query.length)
        from = text.indexOf(query, from + query.length, ignoreCase = true)
    }
}
