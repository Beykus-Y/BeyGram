package com.beykus.beygram.share

import com.beykus.beygram.domain.model.ForumTopic

/** Null loader/ordinary chat accepts only topic zero. A failed/empty forum response never falls back to General. */
internal fun isShareTopicValid(topicId: Long, result: Result<List<ForumTopic>?>?): Boolean {
    if (result == null) return topicId == 0L
    if (result.isFailure) return false
    val topics = result.getOrNull() ?: return topicId == 0L
    return topics.any { it.id == topicId && it.canWrite }
}
