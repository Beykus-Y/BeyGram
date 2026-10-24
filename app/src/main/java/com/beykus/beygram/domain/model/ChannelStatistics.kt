package com.beykus.beygram.domain.model

/** Compact channel statistics for the owner screen; graphs stay in TDLib's JSON form, if needed later. */
data class ChannelStatisticsSummary(
    val periodStart: Int = 0,
    val periodEnd: Int = 0,
    val memberCount: Int = 0,
    /** Percentage change of member count over the reported period. */
    val memberGrowthPercent: Double = 0.0,
    val meanViewCount: Int = 0,
    val meanShareCount: Int = 0,
    val meanReactionCount: Int = 0,
    val notificationsPercent: Int = 0,
    val topSenders: List<ChannelStatisticsPerson> = emptyList()
)

data class ChannelStatisticsPerson(val userId: Long, val name: String, val value: Int)

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
