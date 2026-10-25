package com.beykus.beygram.domain.model

/** Basic information about one active story, enough to draw a progress segment before the content is loaded. */
data class StoryPreview(
    val storyId: Int,
    val dateSeconds: Long,
    val isForCloseFriends: Boolean = false
)

/** Active stories of one chat. Secret chats and blocked senders never reach this model. */
data class ChatStories(
    val chatId: Long,
    val title: String,
    val initials: String,
    val avatarColorArgb: Long,
    val photo: AvatarPhoto? = null,
    val isSelf: Boolean = false,
    /** TDLib story-list order; larger values go first. */
    val order: Long = 0L,
    val stories: List<StoryPreview> = emptyList(),
    val maxReadStoryId: Int = 0,
    val canBeArchived: Boolean = false
) {
    val hasUnread: Boolean get() = stories.any { it.storyId > maxReadStoryId }

    /** The first story the viewer should open: the oldest unseen one, or the first available. */
    val firstUnreadStoryId: Int
        get() = stories.firstOrNull { it.storyId > maxReadStoryId }?.storyId
            ?: stories.firstOrNull()?.storyId
            ?: 0
}

enum class StoryMediaKind { Photo, Video }

/**
 * One story with its downloaded/registered content. [fileId] is the full-size file; [thumbnailFileId]
 * is a small preview that may be downloaded before the user opens the story.
 */
data class StoryMedia(
    val kind: StoryMediaKind,
    val fileId: Int,
    val thumbnailFileId: Int? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationSeconds: Int = 0,
    /** Precise TDLib duration for the viewer timer; avoids truncating fractional video lengths. */
    val durationMillis: Long = durationSeconds * 1_000L,
    val minithumbnail: ByteArray? = null
)

/** Full story information requested on demand; neighbors are prefetched as thumbnails only. */
data class StoryDetail(
    val posterChatId: Long,
    val storyId: Int,
    val dateSeconds: Long,
    val caption: String = "",
    val media: StoryMedia? = null,
    val isBeingPosted: Boolean = false,
    val canBeDeleted: Boolean = false,
    val canBeReplied: Boolean = false,
    val privacy: StoryPrivacy? = null,
    val chosenReaction: ReactionOption? = null,
    val viewCount: Int = 0,
    val reactionCount: Int = 0
)

enum class StoryPrivacy { Everyone, Contacts, CloseFriends, SelectedUsers }

/** One page of the signed-in user's expired stories; [nextFromStoryId] is zero when the end is reached. */
data class StoryArchivePage(
    val stories: List<StoryDetail> = emptyList(),
    val totalCount: Int = 0,
    val nextFromStoryId: Int = 0
)

sealed interface StoryPostPermission {
    data class Allowed(val postedStoryCount: Int = 0) : StoryPostPermission

    data class Denied(val reason: StoryPostDenial) : StoryPostPermission
}

enum class StoryPostDenial {
    PremiumRequired,
    ActiveStoryLimit,
    WeeklyLimit,
    MonthlyLimit,
    BoostRequired,
    LiveStoryActive,
    Unavailable
}

/** Result of the asynchronous PostStory request: TDLib returns a temporary story before upload finishes. */
data class StoryPostTicket(
    val posterChatId: Long,
    val storyId: Int,
    val isPending: Boolean
)

sealed interface StoryPostEvent {
    data class Published(val posterChatId: Long, val storyId: Int, val oldStoryId: Int) : StoryPostEvent

    data class Failed(val posterChatId: Long, val storyId: Int, val reason: StoryPostDenial) : StoryPostEvent
}

/** Active periods accepted by TDLib: 6, 12, 24 and 48 hours. Only 24 hours is available without Premium. */
fun storyActivePeriodChoices(premium: Boolean): List<Int> =
    if (premium) listOf(6 * 3600, 12 * 3600, 24 * 3600, 48 * 3600) else listOf(24 * 3600)

/** Source rectangle for a 9:16 story crop; the caller scales the result to 1080x1920. */
data class StoryCropRect(val left: Int, val top: Int, val width: Int, val height: Int)

fun storyCropRect(width: Int, height: Int): StoryCropRect {
    if (width <= 0 || height <= 0) return StoryCropRect(0, 0, 0, 0)
    val targetRatio = 1080.0 / 1920.0
    val sourceRatio = width.toDouble() / height
    return if (sourceRatio > targetRatio) {
        val cropWidth = (height * targetRatio).toInt().coerceIn(1, width)
        StoryCropRect((width - cropWidth) / 2, 0, cropWidth, height)
    } else {
        val cropHeight = (width / targetRatio).toInt().coerceIn(1, height)
        StoryCropRect(0, (height - cropHeight) / 2, width, cropHeight)
    }
}

/** Story-bar order from TDLib: own stories first, then descending (order, chat id). */
fun orderedStoryBar(stories: Collection<ChatStories>): List<ChatStories> =
    stories.sortedWith(
        compareByDescending<ChatStories> { it.isSelf }
            .thenByDescending { it.order }
            .thenByDescending { it.chatId }
    )
