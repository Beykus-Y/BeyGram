package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChannelAdminEvent
import com.beykus.beygram.domain.model.ChannelAdminEventPage
import com.beykus.beygram.domain.model.ChannelEventFilter
import com.beykus.beygram.domain.model.ChannelStatisticsSummary
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

@Composable
fun ChannelStatisticsScreen(
    title: String,
    loadStatistics: suspend () -> Result<ChannelStatisticsSummary>,
    loadEvents: suspend (Long, ChannelEventFilter) -> Result<ChannelAdminEventPage>,
    onOpenPost: (Long) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
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
    LaunchedEffect(filter, reload) {
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
                }
                .onFailure {
                    if (generation == eventGeneration) {
                        eventsError = it.userMessage(context, R.string.channel_admin_log_failed)
                    }
                }
            loadingMore = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        ProfileToolbar(stringResource(R.string.channel_statistics_title), onBack)
        // One scroll container: statistics, filters and the log remain reachable with large fonts and in landscape.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            item("statistics") {
                Column(Modifier.fillMaxWidth()) {
                    Text(title, color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    when {
                        statsLoading -> CircularProgressIndicator(
                            Modifier.fillMaxWidth().padding(24.dp),
                            color = palette.accent
                        )
                        statsError != null -> Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(statsError.orEmpty(), color = palette.danger, textAlign = TextAlign.Center)
                            TextButton(onClick = { reload++ }) { Text(stringResource(R.string.common_retry)) }
                        }
                        else -> statistics?.let { value ->
                            Text(
                                stringResource(
                                    R.string.channel_statistics_period,
                                    formatDate(value.periodStart),
                                    formatDate(value.periodEnd)
                                ),
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                            StatCard(value, palette, Modifier.padding(top = 8.dp))
                            StatisticsChartsSection(value.charts, palette)
                            if (value.isChannel) {
                                TopPostsSection(value.topPosts, palette, onOpenPost)
                            }
                            if (value.topSenders.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.channel_statistics_top_senders),
                                    color = palette.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 14.dp)
                                )
                                value.topSenders.forEach { person ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(person.name, color = palette.textPrimary, modifier = Modifier.weight(1f))
                                        Text(person.value.toString(), color = palette.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item("log_header") {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        stringResource(R.string.channel_admin_log_title),
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        ChannelEventFilter.entries.forEach { option ->
                            TextButton(onClick = { filter = option }) {
                                Text(
                                    stringResource(filterLabel(option)),
                                    color = if (filter == option) palette.accent else palette.textSecondary,
                                    fontWeight = if (filter == option) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
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
                if (events.isEmpty()) item("empty") {
                    Text(
                        stringResource(R.string.channel_admin_log_empty),
                        color = palette.textSecondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                items(events, key = { it.id }) { event ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(
                            if (event.actorName.isBlank()) event.text
                            else stringResource(R.string.channel_statistics_actor, event.actorName, event.text),
                            color = palette.textPrimary,
                            fontSize = 14.sp
                        )
                        Text(formatDateTime(event.date), color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
                if (nextId != 0L) item("more") {
                    TextButton(
                        onClick = ::loadMore,
                        enabled = !loadingMore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.channel_admin_log_more))
                    }
                }
            }
            eventsError?.let { message ->
                item("events_error") {
                    Text(
                        message,
                        color = palette.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: ChannelStatisticsSummary, palette: BeyGramPalette, modifier: Modifier = Modifier) {
    val rows = if (value.isChannel) listOf(
        R.string.channel_statistics_members to formatCount(value.memberCount),
        R.string.channel_statistics_growth to "${formatDecimal(value.memberGrowthPercent)}%",
        R.string.channel_statistics_views to formatCount(value.meanViewCount),
        R.string.channel_statistics_shares to formatCount(value.meanShareCount),
        R.string.channel_statistics_reactions to formatCount(value.meanReactionCount),
        R.string.channel_statistics_notifications to stringResource(
            R.string.channel_statistics_percent,
            value.notificationsPercent
        )
    ) else listOf(
        R.string.channel_statistics_members to formatCount(value.memberCount),
        R.string.channel_statistics_growth to "${formatDecimal(value.memberGrowthPercent)}%",
        R.string.channel_statistics_messages to formatCount(value.messageCount),
        R.string.channel_statistics_viewers to formatCount(value.viewerCount),
        R.string.channel_statistics_senders to formatCount(value.senderCount)
    )
    Column(
        modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { (label, number) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), color = palette.textSecondary, modifier = Modifier.weight(1f))
                Text(number, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun filterLabel(filter: ChannelEventFilter): Int = when (filter) {
    ChannelEventFilter.All -> R.string.channel_admin_log_all
    ChannelEventFilter.Messages -> R.string.channel_admin_log_messages
    ChannelEventFilter.Members -> R.string.channel_admin_log_members
    ChannelEventFilter.Settings -> R.string.channel_admin_log_settings
}
