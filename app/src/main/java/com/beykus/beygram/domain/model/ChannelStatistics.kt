package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

/** Compact channel statistics for the owner screen; graph data is normalized by the TDLib mapper. */
data class ChannelStatisticsSummary(
    val periodStart: Int = 0,
    val periodEnd: Int = 0,
    /** True for broadcast channels, false for supergroups: the two TDLib statistics have different fields. */
    val isChannel: Boolean = false,
    val memberCount: Int = 0,
    /** Percentage change of member count over the reported period. */
    val memberGrowthPercent: Double = 0.0,
    val meanViewCount: Int = 0,
    val meanShareCount: Int = 0,
    val meanReactionCount: Int = 0,
    /** Supergroup-only totals; zero for channels, which report means instead. */
    val messageCount: Int = 0,
    val viewerCount: Int = 0,
    val senderCount: Int = 0,
    val notificationsPercent: Int = 0,
    val topSenders: List<ChannelStatisticsPerson> = emptyList(),
    val charts: List<ChannelStatisticsChart> = emptyList(),
    val topPosts: List<ChannelTopPost> = emptyList()
)

data class ChannelStatisticsPerson(val userId: Long, val name: String, val value: Int)

/** Identifies a chart so the UI can give it a stable localized title. */
enum class ChannelChartId {
    Members,
    Joins,
    Mutes,
    ViewsByHour,
    ViewsBySource,
    JoinsBySource,
    Languages,
    MessageInteractions,
    MessageReactions,
    MessageContent,
    Actions,
    ViewsByWeekday,
    GroupMembers,
    GroupJoins,
    MessagesByHour,
    MessagesByWeekday
}

/** The drawing primitive requested by TDLib; unsupported graph types fall back to [Line]. */
enum class ChannelChartKind { Line, Step, Area, Bar }

data class ChannelStatisticsChart(
    val id: ChannelChartId,
    val kind: ChannelChartKind,
    val series: List<ChannelChartSeries>,
    /** TDLib says bars are always stacked; kept explicit because area graphs may stack too. */
    val stacked: Boolean = false,
    /** TDLib asks clients to show values as percentages for these graphs. */
    val percentage: Boolean = false,
    /** TDLib's y_scaled: each line has its own vertical scale (views vs. shares). */
    val yScaled: Boolean = false
)

data class ChannelChartSeries(
    val name: String,
    val points: List<ChannelChartPoint>,
    /** The mapper merged lower-ranked series into this one; the UI localizes its name. */
    val isOther: Boolean = false
)

/** [x] is either a UNIX timestamp in milliseconds or an ordinal when TDLib returns a category axis. */
data class ChannelChartPoint(val x: Long, val value: Long)

/**
 * A post from TDLib's recent channel interactions. TDLib returns only recent posts and does not
 * include the post text there, so [preview] may be empty when the message lookup failed.
 */
data class ChannelTopPost(
    val messageId: Long,
    val preview: UiText = UiText.Raw(""),
    val date: Int = 0,
    val viewCount: Int = 0,
    val forwardCount: Int = 0,
    val reactionCount: Int = 0
)

/** One admin-log entry, with a user-facing action description supplied by the mapper. */
data class ChannelAdminEvent(
    val id: Long,
    val date: Int,
    val actorId: Long,
    val actorName: String,
    val text: String
)

data class ChannelAdminEventPage(val events: List<ChannelAdminEvent>, val nextFromEventId: Long)

/** Admin-log filters shown in the channel statistics screen. */
enum class ChannelEventFilter { All, Messages, Members, Settings }
