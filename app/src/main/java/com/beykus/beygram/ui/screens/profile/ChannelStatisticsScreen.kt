package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChannelAdminEvent
import com.beykus.beygram.domain.model.ChannelAdminEventPage
import com.beykus.beygram.domain.model.ChannelEventFilter
import com.beykus.beygram.domain.model.ChannelStatisticsPerson
import com.beykus.beygram.domain.model.ChannelStatisticsSummary
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.ChipSelectionTone
import com.beykus.beygram.ui.design.DesignSystem
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch
import com.beykus.beygram.ui.components.GlassScreenScaffold

/** Two headline figures side by side need this width; below it, or with large fonts, the figures stack. */
private val KpiTwoColumnMinWidth: Dp = 340.dp
private const val KpiTwoColumnMaxFontScale = 1.5f

@Composable
fun ChannelStatisticsScreen(
    title: String,
    @StringRes headerTitle: Int,
    loadStatistics: suspend () -> Result<ChannelStatisticsSummary>,
    loadEvents: suspend (Long, ChannelEventFilter) -> Result<ChannelAdminEventPage>,
    onOpenPost: (Long) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statistics by remember { mutableStateOf<ChannelStatisticsSummary?>(null) }
    var statsLoading by remember { mutableStateOf(true) }
    var statsError by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(ChannelEventFilter.All) }
    var events by remember { mutableStateOf<List<ChannelAdminEvent>>(emptyList()) }
    var nextId by remember { mutableLongStateOf(0L) }
    var eventsLoading by remember { mutableStateOf(true) }
    var eventsError by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    // Retries only the admin log; a statistics retry also reads the log again through [reload].
    var eventReload by remember { mutableIntStateOf(0) }
    // Manual "load more" requests are not keyed by LaunchedEffect; a generation token discards stale pages.
    var eventGeneration by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    LaunchedEffect(reload) {
        statsLoading = true
        statsError = null
        statistics = loadStatistics().getOrElse {
            statsError = it.userMessage(context, R.string.err_statistics_unavailable)
            null
        }
        statsLoading = false
    }
    LaunchedEffect(filter, reload, eventReload) {
        val generation = eventGeneration + 1
        eventGeneration = generation
        eventsLoading = true
        eventsError = null
        loadingMore = false
        events = emptyList()
        nextId = 0L
        loadEvents(0L, filter)
            .onSuccess { page ->
                if (generation == eventGeneration) {
                    events = page.events.distinctBy { it.id }
                    nextId = page.nextFromEventId
                }
            }
            .onFailure {
                if (generation == eventGeneration) {
                    eventsError = it.userMessage(context, R.string.channel_admin_log_failed)
                }
            }
        if (generation == eventGeneration) eventsLoading = false
    }

    fun loadMore() {
        if (loadingMore || nextId == 0L) return
        val requestFilter = filter
        val generation = eventGeneration
        scope.launch {
            loadingMore = true
            val from = nextId
            loadEvents(from, requestFilter)
                .onSuccess { page ->
                    if (generation != eventGeneration) return@onSuccess
                    events = (events + page.events).distinctBy { it.id }
                    nextId = if (page.nextFromEventId == from) 0L else page.nextFromEventId
                    // A successful request clears the error left by an earlier failed page.
                    eventsError = null
                }
                .onFailure {
                    if (generation == eventGeneration) {
                        eventsError = it.userMessage(context, R.string.channel_admin_log_failed)
                    }
                }
            loadingMore = false
        }
    }

    fun retryEvents() {
        // A failed first page is read again from the start; a failed later page only needs the next request.
        if (events.isEmpty()) eventReload++ else loadMore()
    }

    // TDLib returns the log newest first; entries of one day form one group. Computed here, outside the list builder.
    val dayGroups = remember(events) { events.groupBy { formatDayTitle(it.date) }.toList() }

    GlassScreenScaffold(
        modifier = Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        toolbar = {
            ProfileToolbar(stringResource(headerTitle), onBack)
        }
    ) { chrome ->
        // One scroll container: statistics, filters and the log remain reachable with large fonts and in landscape.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top + 4.dp, bottom = 4.dp)
        ) {
            item("statistics") {
                Column(Modifier.fillMaxWidth()) {
                    Text(title, color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    when {
                        statsLoading -> CircularProgressIndicator(
                            Modifier.fillMaxWidth().padding(24.dp),
                            color = palette.accent
                        )
                        statsError != null -> ErrorNotice(
                            message = statsError.orEmpty(),
                            palette = palette,
                            onRetry = { reload++ }
                        )
                        else -> statistics?.let { value ->
                            StatisticsOverview(value, palette, onOpenPost)
                        }
                    }
                }
            }
            item("log_header") {
                Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                    StatisticsSectionHeader(stringResource(R.string.channel_admin_log_title))
                    // The filters are one row of chips; the row scrolls sideways when the labels are long or the font is large.
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChannelEventFilter.entries.forEach { option ->
                            StatisticsFilterChip(
                                title = stringResource(filterLabel(option)),
                                selected = filter == option,
                                onClick = { filter = option }
                            )
                        }
                    }
                }
            }
            if (eventsLoading) {
                item("events_loading") {
                    CircularProgressIndicator(
                        Modifier.fillMaxWidth().padding(16.dp),
                        color = palette.accent
                    )
                }
            } else {
                if (events.isEmpty() && eventsError == null) {
                    item("empty") {
                        Text(
                            stringResource(R.string.channel_admin_log_empty),
                            color = palette.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                }
                dayGroups.forEach { (day, dayEvents) ->
                    item(key = "day_$day") {
                        AdminLogDay(day, dayEvents, palette)
                    }
                }
                if (nextId != 0L) item("more") {
                    TextButton(
                        onClick = ::loadMore,
                        enabled = !loadingMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.channel_admin_log_more))
                    }
                }
            }
            eventsError?.let { message ->
                item("events_error") {
                    ErrorNotice(message = message, palette = palette, onRetry = ::retryEvents)
                }
            }
        }
    }
}

/** Summary of the period: the headline figures, the compact list, the charts, the posts and the senders. */
@Composable
private fun StatisticsOverview(
    value: ChannelStatisticsSummary,
    palette: BeyGramPalette,
    onOpenPost: (Long) -> Unit
) {
    if (value.periodStart > 0 && value.periodEnd > 0) {
        Text(
            stringResource(
                R.string.channel_statistics_period,
                formatDate(value.periodStart),
                formatDate(value.periodEnd)
            ),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    StatisticsSectionHeader(stringResource(R.string.channel_statistics_kpi_title), Modifier.padding(top = 18.dp))
    HeadlineCards(value, palette)
    val detail = statisticsDetailMetrics(value.isChannel)
    if (detail.isNotEmpty()) {
        DetailFigures(value, detail, palette)
    }
    StatisticsChartsSection(value.charts, palette)
    if (value.isChannel) {
        TopPostsSection(value.topPosts, palette, onOpenPost)
    }
    if (value.topSenders.isNotEmpty()) {
        TopSendersSection(value.topSenders, palette)
    }
}

/** One headline figure: its label, its value and one line under the value (a unit, or the change over the period). */
private data class HeadlineCell(val label: String, val value: String, val detail: String?, val trend: Int)

@Composable
private fun HeadlineCards(value: ChannelStatisticsSummary, palette: BeyGramPalette) {
    val design = LocalDesignSystem.current
    val inset = statisticsInset()
    val cells = statisticsHeadlineMetrics(value.isChannel).map { headlineCell(it, value) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).statisticsGroupSurface()) {
        HeadlineGrid(cells, palette, design, inset)
    }
}

@Composable
private fun headlineCell(metric: StatisticsMetric, value: ChannelStatisticsSummary): HeadlineCell {
    val label = stringResource(metricLabel(metric))
    val figure = metricValue(metric, value)
    return when (metric) {
        StatisticsMetric.Members -> HeadlineCell(
            label = label,
            value = figure,
            // The change over the period is the one trend TDLib reports; it is shown only when it is a real number.
            detail = formatSignedPercent(value.memberGrowthPercent)
                ?.let { stringResource(R.string.channel_statistics_kpi_change, it) },
            trend = growthTrend(value.memberGrowthPercent)
        )
        StatisticsMetric.MeanViews, StatisticsMetric.MeanReactions, StatisticsMetric.MeanShares -> HeadlineCell(
            label = label,
            value = figure,
            detail = stringResource(R.string.channel_statistics_kpi_per_post),
            trend = 0
        )
        else -> HeadlineCell(label = label, value = figure, detail = null, trend = 0)
    }
}

/**
 * Headline figures in two columns when there is room for big numbers. Narrow screens and large fonts stack them, so a
 * value is never squeezed into a fragment.
 */
@Composable
private fun HeadlineGrid(
    cells: List<HeadlineCell>,
    palette: BeyGramPalette,
    design: DesignSystem,
    inset: Dp
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= KpiTwoColumnMinWidth && fontScale <= KpiTwoColumnMaxFontScale
        Column(Modifier.fillMaxWidth()) {
            if (twoColumns) {
                cells.chunked(2).forEachIndexed { rowIndex, pair ->
                    if (rowIndex > 0) HorizontalDivider(color = palette.divider)
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEach { cell ->
                            HeadlineFigure(cell, palette, design, inset, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                cells.forEachIndexed { index, cell ->
                    if (index > 0) HorizontalDivider(color = palette.divider)
                    HeadlineFigure(cell, palette, design, inset, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HeadlineFigure(
    cell: HeadlineCell,
    palette: BeyGramPalette,
    design: DesignSystem,
    inset: Dp,
    modifier: Modifier
) {
    val detailColor = when {
        cell.trend > 0 -> palette.successText
        cell.trend < 0 -> palette.danger
        else -> palette.textSecondary
    }
    // One announcement for TalkBack: the label, the value and the unit or change together.
    Column(
        modifier
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = inset, vertical = 12.dp)
    ) {
        Text(cell.label, color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            cell.value,
            color = palette.textPrimary,
            fontSize = 24.sp,
            fontWeight = design.mainScreen.titleWeight,
            modifier = Modifier.padding(top = 2.dp)
        )
        cell.detail?.let { detail ->
            Text(detail, color = detailColor, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Figures that do not fit the headline cards, as a compact label and value list. */
@Composable
private fun DetailFigures(value: ChannelStatisticsSummary, metrics: List<StatisticsMetric>, palette: BeyGramPalette) {
    val inset = statisticsInset()
    StatisticsSectionHeader(stringResource(R.string.channel_statistics_other_metrics), Modifier.padding(top = 18.dp))
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).statisticsGroupSurface()
            .padding(horizontal = inset, vertical = 4.dp)
    ) {
        metrics.forEachIndexed { index, metric ->
            if (index > 0) HorizontalDivider(color = palette.divider)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(metricLabel(metric)),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(metricValue(metric, value), color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TopSendersSection(senders: List<ChannelStatisticsPerson>, palette: BeyGramPalette) {
    val inset = statisticsInset()
    StatisticsSectionHeader(stringResource(R.string.channel_statistics_top_senders), Modifier.padding(top = 18.dp))
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).statisticsGroupSurface()
            .padding(horizontal = inset, vertical = 4.dp)
    ) {
        senders.forEachIndexed { index, person ->
            if (index > 0) HorizontalDivider(color = palette.divider)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.widthIn(min = 24.dp)
                )
                Text(
                    person.name,
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(formatCount(person.value), color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One day of the admin log. Each entry keeps its author, its action and its time together. */
@Composable
private fun AdminLogDay(day: String, dayEvents: List<ChannelAdminEvent>, palette: BeyGramPalette) {
    val inset = statisticsInset()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            day,
            color = palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = inset, bottom = 6.dp).semantics { heading() }
        )
        Column(
            Modifier.fillMaxWidth().statisticsGroupSurface().padding(horizontal = inset, vertical = 4.dp)
        ) {
            dayEvents.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = palette.divider)
                AdminLogRow(event, palette)
            }
        }
    }
}

@Composable
private fun AdminLogRow(event: ChannelAdminEvent, palette: BeyGramPalette) {
    val hasActor = event.actorName.isNotBlank()
    // One entry is one announcement for TalkBack: author, action and time together.
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}) {
        Row(Modifier.fillMaxWidth()) {
            // A missing author is named as missing; no placeholder name is invented for it.
            Text(
                if (hasActor) event.actorName else stringResource(R.string.channel_admin_log_actor_missing),
                color = if (hasActor) palette.textPrimary else palette.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatTime(event.date),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            event.text,
            color = palette.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** A message with a retry action, in the warning colours of the style. The message is announced when it appears. */
@Composable
private fun ErrorNotice(message: String, palette: BeyGramPalette, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 12.dp)
            .background(palette.warningSurface, styledShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            color = palette.warningText,
            textAlign = TextAlign.Center,
            modifier = Modifier.errorAnnouncement()
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.common_retry), color = palette.accentInk)
        }
    }
}

/**
 * One admin-log filter. The selection follows the chip tone of the style in effect; the touch target stays 48dp around
 * the smaller visible chip, and TalkBack announces the selected state.
 */
@Composable
private fun StatisticsFilterChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    val main = LocalDesignSystem.current.mainScreen
    val tone = main.chipSelection
    val shape = styledShape(main.chipRadius)
    val container = when (tone) {
        ChipSelectionTone.Filled -> if (selected) palette.accent else palette.surface2
        ChipSelectionTone.Tonal -> if (selected) palette.accentSoft else palette.surface2
        ChipSelectionTone.Underlined -> Color.Transparent
        ChipSelectionTone.Filter -> if (selected) palette.accentSoft else Color.Transparent
        ChipSelectionTone.Ink -> if (selected) palette.textPrimary else Color.Transparent
    }
    val label = when (tone) {
        ChipSelectionTone.Filled -> if (selected) palette.onAccent else palette.textPrimary
        ChipSelectionTone.Tonal, ChipSelectionTone.Underlined -> if (selected) palette.accentInk else palette.textPrimary
        ChipSelectionTone.Filter -> if (selected) palette.accentInk else palette.textSecondary
        ChipSelectionTone.Ink -> if (selected) palette.screen else palette.textSecondary
    }
    val outlined = tone == ChipSelectionTone.Filter && !selected
    Column(
        Modifier.heightIn(min = 48.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.heightIn(min = 36.dp)
                .clip(shape)
                .then(if (outlined) Modifier.border(1.dp, palette.divider, shape) else Modifier)
                .background(container)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tone == ChipSelectionTone.Filter && selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = label, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                title,
                color = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
        if (tone == ChipSelectionTone.Underlined) {
            // The rule is always laid out, so the row of chips does not shift when the selection moves.
            Box(
                Modifier.padding(top = 2.dp).height(2.dp).width(if (selected) 28.dp else 0.dp)
                    .background(if (selected) palette.accent else Color.Transparent)
            )
        }
    }
}

@Composable
private fun metricValue(metric: StatisticsMetric, value: ChannelStatisticsSummary): String = when (metric) {
    StatisticsMetric.Members -> formatCount(value.memberCount)
    StatisticsMetric.Growth -> formatSignedPercent(value.memberGrowthPercent).orEmpty()
    StatisticsMetric.MeanViews -> formatCount(value.meanViewCount)
    StatisticsMetric.MeanShares -> formatCount(value.meanShareCount)
    StatisticsMetric.MeanReactions -> formatCount(value.meanReactionCount)
    StatisticsMetric.Notifications -> stringResource(R.string.channel_statistics_percent, value.notificationsPercent)
    StatisticsMetric.Messages -> formatCount(value.messageCount)
    StatisticsMetric.Viewers -> formatCount(value.viewerCount)
    StatisticsMetric.Senders -> formatCount(value.senderCount)
}

@StringRes
private fun metricLabel(metric: StatisticsMetric): Int = when (metric) {
    StatisticsMetric.Members -> R.string.channel_statistics_members
    StatisticsMetric.Growth -> R.string.channel_statistics_growth
    StatisticsMetric.MeanViews -> R.string.channel_statistics_views
    StatisticsMetric.MeanShares -> R.string.channel_statistics_shares
    StatisticsMetric.MeanReactions -> R.string.channel_statistics_reactions
    StatisticsMetric.Notifications -> R.string.channel_statistics_notifications
    StatisticsMetric.Messages -> R.string.channel_statistics_messages
    StatisticsMetric.Viewers -> R.string.channel_statistics_viewers
    StatisticsMetric.Senders -> R.string.channel_statistics_senders
}

private fun filterLabel(filter: ChannelEventFilter): Int = when (filter) {
    ChannelEventFilter.All -> R.string.channel_admin_log_all
    ChannelEventFilter.Messages -> R.string.channel_admin_log_messages
    ChannelEventFilter.Members -> R.string.channel_admin_log_members
    ChannelEventFilter.Settings -> R.string.channel_admin_log_settings
}
