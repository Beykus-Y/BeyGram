package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ChatStories
import com.beykus.beygram.domain.model.InteractionEffect
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.StealthBlockedException
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.StoryDetail
import com.beykus.beygram.domain.model.StoryArchivePage
import com.beykus.beygram.domain.model.StoryMedia
import com.beykus.beygram.domain.model.StoryMediaKind
import com.beykus.beygram.domain.model.StoryPostDenial
import com.beykus.beygram.domain.model.StoryPostEvent
import com.beykus.beygram.domain.model.StoryPostPermission
import com.beykus.beygram.domain.model.StoryPostTicket
import com.beykus.beygram.domain.model.StoryPostTarget
import com.beykus.beygram.domain.model.StoryPreview
import com.beykus.beygram.domain.model.StoryPrivacy
import com.beykus.beygram.domain.model.StorySubmissionState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.initialsOf
import com.beykus.beygram.domain.model.matchesPendingStoryEvent
import com.beykus.beygram.domain.model.orderedStoryBar
import com.beykus.beygram.domain.model.orderedStoryPostTargets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

/**
 * Owns the account's active-story list and the requests for one story at a time.
 *
 * The chat list only receives metadata. A full [TdApi.Story] is requested by the viewer for the current
 * story and, as a thumbnail-only prefetch, for its neighbors. Secret chats and blocked senders are
 * filtered here, before any title, photo or content reaches the UI.
 */
internal class TdStories(
    private val client: () -> Client,
    private val profiles: TdProfiles,
    private val mediaFiles: MediaFileStore,
    private val accountId: String,
    private val uploadsRoot: File,
    /** Stealth mode: false means this account must not open the stories posted by [posterChatId]. */
    private val canViewStory: (posterChatId: Long) -> Boolean
) {
    private val requests = TdRequests(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val active = mutableMapOf<Long, TdApi.ChatActiveStories>()
    /** Metadata of the last successfully published entries, kept for a viewer opened during a list refresh. */
    private val lastKnownActive = mutableMapOf<Long, ChatStories>()
    /** Full details already shown or prefetched by the viewer; they make the viewer usable offline. */
    private val storyCache = object : LinkedHashMap<Pair<Long, Int>, StoryDetail>(16, 0.75f, true) {}
    private val hiddenStories = mutableSetOf<Pair<Long, Int>>()
    private val readOverrides = mutableMapOf<Long, Int>()
    /** Stories whose OpenStory was sent. Only these get a CloseStory, so a refused open leaves nothing behind. */
    private val openedStories = mutableSetOf<Pair<Long, Int>>()
    private val requestedChats = mutableSetOf<Long>()
    private val blockedChats = mutableSetOf<Long>()
    private val unblockedChats = mutableSetOf<Long>()
    private val uploadJobs = mutableMapOf<Pair<Long, Int>, Job>()
    private val abandonedUploads = mutableSetOf<Pair<Long, Int>>()
    private val abandonedSubmissions = mutableSetOf<String>()
    private val submissionStates = ConcurrentHashMap<String, MutableStateFlow<StorySubmissionState>>()
    private val submissionTickets = ConcurrentHashMap<Pair<Long, Int>, String>()
    private val submissionRecoveryJobs = mutableMapOf<String, Job>()
    private val persistedSubmissions = mutableMapOf<String, PersistedSubmission>()
    private val submissionLedger = File(
        uploadsRoot,
        "story-submissions-${accountId.filter { it.isLetterOrDigit() }.ifBlank { "default" }}.tsv"
    )
    private var selfId = 0L
    private var selfChatId = 0L
    private var generation = 0
    private var profileJob: Job? = null
    private var activeLoadInFlight = false
    private var activeStoriesExhausted = false

    private val _items = MutableStateFlow<List<ChatStories>>(emptyList())
    val items: StateFlow<List<ChatStories>> = _items.asStateFlow()

    private val _postEvents = MutableSharedFlow<StoryPostEvent>(replay = 16, extraBufferCapacity = 8)
    val postEvents: SharedFlow<StoryPostEvent> = _postEvents.asSharedFlow()

    init {
        loadSubmissionLedger()
    }

    fun authorized() {
        val token = synchronized(lock) {
            generation += 1
            activeLoadInFlight = false
            activeStoriesExhausted = false
            generation
        }
        profileJob?.cancel()
        profileJob = scope.launch {
            profiles.me.collect { publish() }
        }
        scope.launch { initialize(token) }
    }

    fun signedOut() {
        synchronized(lock) {
            generation++
            active.clear()
            lastKnownActive.clear()
            storyCache.clear()
            hiddenStories.clear()
            readOverrides.clear()
            openedStories.clear()
            requestedChats.clear()
            blockedChats.clear()
            unblockedChats.clear()
            uploadJobs.values.forEach { it.cancel() }
            uploadJobs.clear()
            abandonedUploads.clear()
            selfId = 0L
            selfChatId = 0L
            activeLoadInFlight = false
            activeStoriesExhausted = false
        }
        profileJob?.cancel()
        profileJob = null
        _items.value = emptyList()
    }

    /** Re-requests the main story list when the chats screen becomes visible or the connection returns. */
    fun refreshActive() {
        synchronized(lock) { activeStoriesExhausted = false }
        scope.launch { loadActivePages(MAX_ACTIVE_STORY_PAGES) }
    }

    /** Loads the next page when the story bar reaches its end. */
    fun loadMoreActive() {
        scope.launch { loadActivePages(1) }
    }

    /**
     * TDLib's LoadActiveStories returns one page through updates and 404 once the list is complete.
     * A page can be added by another active story, so an explicit refresh starts from the current
     * position again; concurrent calls share one loader.
     */
    private suspend fun loadActivePages(maxPages: Int, token: Int? = null) {
        synchronized(lock) {
            if (token != null && token != generation) return
            if (activeLoadInFlight || activeStoriesExhausted) return
            activeLoadInFlight = true
        }
        try {
            var pages = 0
            while (pages < maxPages) {
                if (token != null && synchronized(lock) { token != generation }) return
                val loaded = try {
                    requests(TdApi.LoadActiveStories(TdApi.StoryListMain()))
                    true
                } catch (timeout: TimeoutCancellationException) {
                    break
                } catch (error: TdRequestError) {
                    if (error.code == 404) {
                        synchronized(lock) { activeStoriesExhausted = true }
                        false
                    } else {
                        throw error
                    }
                }
                if (!loaded) break
                pages++
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A transient request error is retried by refreshActive() when chats are shown again.
        } finally {
            synchronized(lock) { activeLoadInFlight = false }
        }
    }

    fun handle(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateChatActiveStories -> applyActive(update.activeStories)
            is TdApi.UpdateChatBlockList -> {
                var changed = false
                synchronized(lock) {
                    when {
                        update.blockList is TdApi.BlockListMain -> {
                            changed = blockedChats.add(update.chatId) or unblockedChats.remove(update.chatId)
                        }
                        update.blockList == null -> {
                            changed = blockedChats.remove(update.chatId) or unblockedChats.add(update.chatId)
                        }
                        // The stories block list only hides my stories from this chat; it does not
                        // change whether the chat's own stories may be shown to me.
                        else -> Unit
                    }
                }
                if (changed) publish()
            }
            is TdApi.UpdateChatPhoto, is TdApi.UpdateChatTitle -> publish()
            is TdApi.UpdateStoryDeleted -> {
                synchronized(lock) {
                    hiddenStories += update.storyPosterChatId to update.storyId
                    forgetStory(update.storyPosterChatId, update.storyId)
                }
                publish()
            }
            is TdApi.UpdateStoryPostSucceeded -> {
                synchronized(lock) {
                    hiddenStories.remove(update.story.posterChatId to update.oldStoryId)
                    hiddenStories.remove(update.story.posterChatId to update.story.id)
                }
                val submissionId = submissionTickets.remove(update.story.posterChatId to update.oldStoryId)
                    ?: submissionTickets.remove(update.story.posterChatId to update.story.id)
                if (submissionId != null) {
                    updateSubmission(
                        submissionId,
                        StorySubmissionState.Published(
                            StoryPostTicket(
                                update.story.posterChatId,
                                update.story.id,
                                false,
                                update.story.content.uploadFileIds()
                            )
                        )
                    )
                }
                _postEvents.tryEmit(
                    StoryPostEvent.Published(
                        update.story.posterChatId,
                        update.story.id,
                        update.oldStoryId,
                        update.story.content.uploadFileIds()
                    )
                )
                publish()
            }
            is TdApi.UpdateStoryPostFailed -> {
                val submissionId = submissionTickets.remove(update.story.posterChatId to update.story.id)
                if (submissionId != null) {
                    updateSubmission(
                        submissionId,
                        StorySubmissionState.Failed(reason = update.errorType.toDenial())
                    )
                }
                _postEvents.tryEmit(
                    StoryPostEvent.Failed(
                        posterChatId = update.story.posterChatId,
                        storyId = update.story.id,
                        reason = update.errorType.toDenial()
                    )
                )
            }
            else -> Unit
        }
    }

    private suspend fun initialize(token: Int) {
        val me = runCatching { requests(TdApi.GetMe()) }.getOrNull()
        synchronized(lock) {
            if (token != generation) return
            selfId = me?.id ?: 0L
        }
        loadActivePages(MAX_ACTIVE_STORY_PAGES, token)
        if (me != null) {
            val self = runCatching { requests(TdApi.CreatePrivateChat(me.id, false)) }.getOrNull()
            if (self != null) {
                profiles.putChat(self)
                synchronized(lock) {
                    if (token != generation) return
                    selfChatId = self.id
                    requestedChats += self.id
                }
                val own = runCatching { requests(TdApi.GetChatActiveStories(self.id)) }.getOrNull()
                if (own != null) applyActive(own, token)
            }
        }
        publish()
    }

    private fun applyActive(activeStories: TdApi.ChatActiveStories, token: Int = synchronized(lock) { generation }) {
        val chatId = activeStories.chatId
        synchronized(lock) {
            if (token != generation) return
            val shown = activeStories.stories.orEmpty().isNotEmpty() &&
                activeStories.list is TdApi.StoryListMain && activeStories.order > 0L
            if (shown) {
                // The server watermark is authoritative again after an update.
                active[chatId] = activeStories
                readOverrides.remove(chatId)
            } else {
                active.remove(chatId)
                readOverrides.remove(chatId)
            }
        }
        ensureChat(chatId)
        publish()
    }

    private fun ensureChat(chatId: Long) {
        if (chatId == 0L || profiles.chat(chatId) != null) return
        synchronized(lock) { if (!requestedChats.add(chatId)) return }
        scope.launch {
            val chat = runCatching { requests(TdApi.GetChat(chatId)) }.getOrNull()
            synchronized(lock) { requestedChats.remove(chatId) }
            if (chat != null) {
                profiles.putChat(chat)
                publish()
            }
        }
    }

    private fun publish() {
        val (snapshot, hidden, overrides) = synchronized(lock) {
            Triple(active.values.toList(), hiddenStories.toSet(), readOverrides.toMap())
        }
        val blocked = synchronized(lock) { blockedChats.toSet() }
        val unblocked = synchronized(lock) { unblockedChats.toSet() }
        val self = synchronized(lock) { selfId }
        val me = profiles.me.value.info
        val entries = mutableListOf<ChatStories>()
        for (raw in snapshot) {
            if (raw.list !is TdApi.StoryListMain || raw.order <= 0L) continue
            val chat = profiles.chat(raw.chatId)
            if (chat == null) {
                ensureChat(raw.chatId)
                continue
            }
            if (chat.type is TdApi.ChatTypeSecret) continue
            val isSelf = (self > 0L && raw.chatId == self) || (me != null && raw.chatId == me.id)
            if (!isSelf && (raw.chatId in blocked || (raw.chatId !in unblocked && chat.blockList is TdApi.BlockListMain))) continue
            val stories = raw.stories.orEmpty()
                .filterNot { it.isLive }
                .filterNot { (raw.chatId to it.storyId) in hidden }
                .map { StoryPreview(it.storyId, it.date.toLong(), it.isForCloseFriends) }
            if (stories.isEmpty()) continue
            val title = when {
                isSelf && !me?.title.isNullOrBlank() -> me?.title.orEmpty()
                chat.title.isNotBlank() -> chat.title
                else -> ""
            }
            if (title.isBlank()) continue
            entries += ChatStories(
                chatId = raw.chatId,
                title = title,
                initials = initialsOf(title),
                avatarColorArgb = if (isSelf) (me?.id ?: self).stableColor() else raw.chatId.stableColor(),
                photo = if (isSelf) me?.photo else chat.photo?.let { profiles.photo(it) },
                isSelf = isSelf,
                order = raw.order,
                stories = stories,
                maxReadStoryId = maxOf(raw.maxReadStoryId, overrides[raw.chatId] ?: 0),
                canBeArchived = raw.canBeArchived
            )
        }
        val ordered = orderedStoryBar(entries)
        _items.value = ordered
        synchronized(lock) {
            ordered.forEach { lastKnownActive[it.chatId] = it }
        }
    }

    /** Last published metadata for a chat; used when the viewer opens while the list is being refreshed. */
    fun cachedActiveStory(chatId: Long): ChatStories? = synchronized(lock) { lastKnownActive[chatId] }

    /** Last successfully loaded detail; lets the viewer show an already downloaded story offline. */
    fun cachedStory(posterChatId: Long, storyId: Int): StoryDetail? =
        synchronized(lock) { storyCache[posterChatId to storyId] }

    /**
     * Direct check for the viewer. A successful empty set means the server confirmed that the chat
     * has no active stories; a failure is an unknown state and must not close the viewer.
     */
    suspend fun activeStoryIds(posterChatId: Long): Result<Set<Int>> = storyRequest {
        val activeStories = requests(TdApi.GetChatActiveStories(posterChatId))
        // Any returned story keeps the viewer open; only a truly empty answer is evidence of removal.
        activeStories.stories.orEmpty()
            .filterNot { it.isLive }
            .map { it.storyId }
            .toSet()
    }

    private fun cacheStory(detail: StoryDetail) {
        synchronized(lock) {
            storyCache[detail.posterChatId to detail.storyId] = detail
            while (storyCache.size > MAX_CACHED_STORY_DETAILS) {
                val iterator = storyCache.entries.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun forgetStory(posterChatId: Long, storyId: Int) {
        storyCache.remove(posterChatId to storyId)
        val entry = lastKnownActive[posterChatId] ?: return
        val remaining = entry.stories.filterNot { it.storyId == storyId }
        if (remaining.isEmpty()) {
            lastKnownActive.remove(posterChatId)
        } else {
            lastKnownActive[posterChatId] = entry.copy(stories = remaining)
        }
    }

    suspend fun story(posterChatId: Long, storyId: Int): Result<StoryDetail> = storyRequest {
        requests(TdApi.GetStory(posterChatId, storyId, false)).toDetail().also(::cacheStory)
    }

    suspend fun openStory(posterChatId: Long, storyId: Int): Result<Unit> {
        // Checked before anything is sent: a refused story gets no OpenStory, so no read mark either.
        if (!canViewStory(posterChatId)) return Result.failure(StealthBlockedException(InteractionEffect.StoryView))
        return storyRequest {
            // Tracked before the request leaves, so a cancelled await still gets its CloseStory later.
            synchronized(lock) { openedStories.add(posterChatId to storyId) }
            requests(TdApi.OpenStory(posterChatId, storyId))
            synchronized(lock) {
                readOverrides[posterChatId] = maxOf(readOverrides[posterChatId] ?: 0, storyId)
            }
            publish()
        }
    }

    suspend fun closeStory(posterChatId: Long, storyId: Int): Result<Unit> = storyRequest {
        // Only a story whose OpenStory was sent is closed. The pair is forgotten whether or not the close succeeds.
        val wasOpened = synchronized(lock) { openedStories.remove(posterChatId to storyId) }
        if (wasOpened) requests(TdApi.CloseStory(posterChatId, storyId))
        Unit
    }

    suspend fun availableReactions(): Result<List<ReactionOption>> = storyRequest {
        val available = requests(TdApi.GetStoryAvailableReactions(PICKER_ROW_SIZE))
        pickerOptions(available, null, emptyList(), profiles.me.value.info?.isPremium == true)
    }

    suspend fun setReaction(posterChatId: Long, storyId: Int, reaction: ReactionOption?): Result<Unit> = storyRequest {
        requests(TdApi.SetStoryReaction(posterChatId, storyId, reaction?.let(::reactionTypeOf), true))
    }

    suspend fun replyToStory(posterChatId: Long, storyId: Int, text: String): Result<Unit> = storyRequest {
        val body = text.trim()
        if (body.isEmpty()) throw UserMessageException(UiText.Res(R.string.stories_reply_empty))
        val chat = requests(TdApi.GetChat(posterChatId))
        if (chat.type !is TdApi.ChatTypePrivate) {
            throw UserMessageException(UiText.Res(R.string.stories_reply_unavailable))
        }
        requests(TdApi.SendMessage().apply {
            this.chatId = posterChatId
            this.replyTo = TdApi.InputMessageReplyToStory(posterChatId, storyId)
            this.inputMessageContent = TdApi.InputMessageText().apply {
                this.text = TdApi.FormattedText(body, emptyArray())
                this.clearDraft = false
            }
        })
        Unit
    }

    suspend fun deleteStory(posterChatId: Long, storyId: Int): Result<Unit> = storyRequest {
        requests(TdApi.DeleteStory(posterChatId, storyId))
        synchronized(lock) {
            hiddenStories += posterChatId to storyId
            forgetStory(posterChatId, storyId)
        }
        publish()
    }

    suspend fun archive(fromStoryId: Int = 0): Result<StoryArchivePage> = storyRequest {
        val chatId = ensureSelfChatId()
        val page = requests(TdApi.GetChatArchivedStories(chatId, fromStoryId.coerceAtLeast(0), ARCHIVE_PAGE_SIZE))
        val stories = page.stories.orEmpty()
            .filterNot { it.isBeingPosted }
            .filterNot { fromStoryId > 0 && it.id == fromStoryId }
            .map { it.toDetail() }
            .sortedByDescending { it.dateSeconds }
        StoryArchivePage(
            stories = stories,
            totalCount = page.totalCount.coerceAtLeast(stories.size),
            nextFromStoryId = stories.lastOrNull()?.storyId ?: 0
        )
    }

    suspend fun captionLimit(): Result<Int> = storyRequest {
        val option = requests(TdApi.GetOption("story_caption_length_max")) as? TdApi.OptionValueInteger
        option?.value?.toInt()?.coerceIn(1, MAX_CAPTION_LENGTH) ?: DEFAULT_CAPTION_LENGTH
    }

    suspend fun postPermission(chatId: Long): Result<StoryPostPermission> = storyRequest {
        val targetChatId = if (chatId != 0L) chatId else ensureSelfChatId()
        requests(TdApi.CanPostStory(targetChatId)).toPermission()
    }

    suspend fun postTargets(): Result<List<StoryPostTarget>> = storyRequest {
        val selfChatId = ensureSelfChatId()
        val me = profiles.me.value.info
        val selfTitle = me?.title?.takeIf { it.isNotBlank() }
            ?: profiles.chat(selfChatId)?.title.orEmpty()
        val targets = mutableListOf(
            StoryPostTarget(
                chatId = selfChatId,
                title = selfTitle,
                isSelf = true,
                photo = me?.photo,
                avatarColorArgb = (me?.id ?: selfChatId).stableColor()
            )
        )
        val response = requests(TdApi.GetChatsToPostStories())
        response.chatIds?.toList().orEmpty().forEach { chatId ->
            if (chatId == selfChatId) return@forEach
            val chat = profiles.chat(chatId)
                ?: runCatching { requests(TdApi.GetChat(chatId)) }.getOrNull()?.also { profiles.putChat(it) }
                ?: return@forEach
            if (chat.type is TdApi.ChatTypeSecret || chat.title.isBlank()) return@forEach
            targets += StoryPostTarget(
                chatId = chatId,
                title = chat.title,
                photo = chat.photo?.let { profiles.photo(it) },
                avatarColorArgb = chatId.stableColor()
            )
        }
        orderedStoryPostTargets(targets.distinctBy { it.chatId })
    }

    suspend fun editCaption(posterChatId: Long, storyId: Int, caption: String): Result<Unit> = storyRequest {
        requests(TdApi.EditStory(
            posterChatId,
            storyId,
            null,
            null,
            TdApi.FormattedText(caption.trim(), emptyArray())
        ))
        Unit
    }

    suspend fun editPrivacy(storyId: Int, privacy: StoryPrivacy, selectedUserIds: List<Long>): Result<Unit> = storyRequest {
        if (privacy == StoryPrivacy.SelectedUsers && selectedUserIds.none { it > 0L }) {
            throw UserMessageException(UiText.Res(R.string.stories_selected_empty))
        }
        requests(TdApi.SetStoryPrivacySettings(storyId, privacy.toTdSettings(selectedUserIds)))
        Unit
    }

    suspend fun setPostedToChatPage(posterChatId: Long, storyId: Int, posted: Boolean): Result<Unit> = storyRequest {
        requests(TdApi.ToggleStoryIsPostedToChatPage(posterChatId, storyId, posted))
        Unit
    }

    /** State of one publish attempt, shared with a recreated publisher screen. */
    fun submissionState(submissionId: String): StateFlow<StorySubmissionState> {
        submissionStates[submissionId]?.let { return it }
        val persisted = synchronized(lock) { persistedSubmissions[submissionId] }
        val persistedTicket = persisted?.ticket
        val initial = when {
            persistedTicket != null && persisted?.kind == "published" ->
                StorySubmissionState.Published(persistedTicket)
            persistedTicket != null -> StorySubmissionState.Pending(persistedTicket)
            persisted != null -> StorySubmissionState.Submitting
            else -> StorySubmissionState.Idle
        }
        val flow = submissionStates.computeIfAbsent(submissionId) { MutableStateFlow(initial) }
        if (persisted != null &&
            flow.value !is StorySubmissionState.Published &&
            flow.value !is StorySubmissionState.Failed
        ) {
            startSubmissionRecovery(submissionId)
        }
        return flow
    }

    private fun updateSubmission(submissionId: String, state: StorySubmissionState) {
        submissionStates.computeIfAbsent(submissionId) { MutableStateFlow(StorySubmissionState.Idle) }.value = state
        val previous = synchronized(lock) { persistedSubmissions[submissionId] }
        when (state) {
            is StorySubmissionState.Pending -> {
                submissionTickets[state.ticket.posterChatId to state.ticket.storyId] = submissionId
                persistSubmission(
                    submissionId,
                    PersistedSubmission(
                        kind = "pending",
                        targetChatId = state.ticket.posterChatId,
                        ticket = state.ticket,
                        createdAt = System.currentTimeMillis(),
                        caption = previous?.caption.orEmpty(),
                        mediaKind = previous?.mediaKind.orEmpty(),
                        mediaSize = previous?.mediaSize ?: 0L,
                        mediaWidth = previous?.mediaWidth ?: 0,
                        mediaHeight = previous?.mediaHeight ?: 0
                    )
                )
            }
            is StorySubmissionState.Published -> {
                submissionTickets.entries.removeIf { it.value == submissionId }
                persistSubmission(
                    submissionId,
                    PersistedSubmission(
                        kind = "published",
                        targetChatId = state.ticket.posterChatId,
                        ticket = state.ticket.copy(isPending = false),
                        createdAt = System.currentTimeMillis(),
                        caption = previous?.caption.orEmpty(),
                        mediaKind = previous?.mediaKind.orEmpty(),
                        mediaSize = previous?.mediaSize ?: 0L,
                        mediaWidth = previous?.mediaWidth ?: 0,
                        mediaHeight = previous?.mediaHeight ?: 0
                    )
                )
            }
            is StorySubmissionState.Failed -> {
                submissionTickets.entries.removeIf { it.value == submissionId }
                removePersistedSubmission(submissionId)
            }
            else -> Unit
        }
    }

    private fun persistSubmitting(
        submissionId: String,
        targetChatId: Long,
        media: OutgoingMedia,
        caption: String
    ) {
        persistSubmission(
            submissionId,
            PersistedSubmission(
                kind = "submitting",
                targetChatId = targetChatId,
                ticket = null,
                createdAt = System.currentTimeMillis(),
                caption = caption.trim(),
                mediaKind = media.kind.name,
                mediaSize = media.size.takeIf { it > 0L } ?: runCatching { File(media.path).length() }.getOrDefault(0L),
                mediaWidth = media.width,
                mediaHeight = media.height
            )
        )
    }

    private fun persistSubmission(submissionId: String, submission: PersistedSubmission) {
        synchronized(lock) {
            persistedSubmissions[submissionId] = submission
            writeSubmissionLedgerLocked()
        }
    }

    private fun removePersistedSubmission(submissionId: String) {
        synchronized(lock) {
            if (persistedSubmissions.remove(submissionId) != null) writeSubmissionLedgerLocked()
        }
    }

    /** The user left the publisher before PostStory returned a ticket. */
    fun abandonSubmission(submissionId: String) {
        synchronized(lock) {
            abandonedSubmissions += submissionId
            if (persistedSubmissions.remove(submissionId) != null) writeSubmissionLedgerLocked()
        }
    }

    private fun takeAbandonedSubmission(submissionId: String): Boolean =
        synchronized(lock) { abandonedSubmissions.remove(submissionId) }

    private fun markSubmissionIndeterminate(submissionId: String) {
        val previous = synchronized(lock) { persistedSubmissions[submissionId] }
        persistSubmission(
            submissionId,
            previous?.copy(kind = "indeterminate")
                ?: PersistedSubmission("indeterminate", 0L, null, System.currentTimeMillis())
        )
        startSubmissionRecovery(submissionId)
    }

    private fun loadSubmissionLedger() {
        val loaded = runCatching {
            if (!submissionLedger.isFile) return@runCatching
            submissionLedger.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 7 || parts[0].isBlank()) return@forEach
                val kind = parts[1]
                val targetChatId = parts[2].toLongOrNull() ?: return@forEach
                val createdAt = parts[6].toLongOrNull() ?: System.currentTimeMillis()
                val caption = parts.getOrNull(7).orEmpty().let { encoded ->
                    runCatching {
                        String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
                    }.getOrDefault(encoded)
                }
                val mediaKind = parts.getOrNull(8).orEmpty()
                val mediaSize = parts.getOrNull(9)?.toLongOrNull() ?: 0L
                val mediaWidth = parts.getOrNull(10)?.toIntOrNull() ?: 0
                val mediaHeight = parts.getOrNull(11)?.toIntOrNull() ?: 0
                val ticket = if (kind == "pending" || kind == "published") {
                    val posterChatId = parts[3].toLongOrNull() ?: return@forEach
                    val storyId = parts[4].toIntOrNull() ?: return@forEach
                    val fileIds = parts[5].split(',').mapNotNull { it.toIntOrNull() }
                    StoryPostTicket(posterChatId, storyId, kind == "pending", fileIds)
                } else {
                    null
                }
                persistedSubmissions[parts[0]] = PersistedSubmission(
                    kind = kind,
                    targetChatId = targetChatId,
                    ticket = ticket,
                    createdAt = createdAt,
                    caption = caption,
                    mediaKind = mediaKind,
                    mediaSize = mediaSize,
                    mediaWidth = mediaWidth,
                    mediaHeight = mediaHeight
                )
            }
        }
        if (loaded.isFailure) synchronized(lock) { persistedSubmissions.clear() }
    }

    private fun writeSubmissionLedgerLocked() {
        runCatching {
            submissionLedger.parentFile?.mkdirs()
            val text = persistedSubmissions.entries.joinToString("\n") { (id, submission) ->
                val ticket = submission.ticket
                listOf(
                    id,
                    submission.kind,
                    submission.targetChatId,
                    ticket?.posterChatId ?: 0L,
                    ticket?.storyId ?: 0,
                    (ticket?.fileIds ?: emptyList()).joinToString(","),
                    submission.createdAt,
                    Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(submission.caption.toByteArray(Charsets.UTF_8)),
                    submission.mediaKind,
                    submission.mediaSize,
                    submission.mediaWidth,
                    submission.mediaHeight
                ).joinToString("\t")
            }
            val temporary = File(submissionLedger.absolutePath + ".tmp")
            temporary.writeText(text)
            if (!temporary.renameTo(submissionLedger)) {
                submissionLedger.writeText(text)
                temporary.delete()
            }
        }
    }

    private fun startSubmissionRecovery(submissionId: String) {
        synchronized(lock) {
            if (submissionRecoveryJobs[submissionId]?.isActive == true) return
            val job = scope.launch { recoverSubmission(submissionId) }
            submissionRecoveryJobs[submissionId] = job
            job.invokeOnCompletion {
                synchronized(lock) {
                    if (submissionRecoveryJobs[submissionId] === job) submissionRecoveryJobs.remove(submissionId)
                }
            }
        }
    }

    /**
     * After process death TDLib may still finish a persisted upload. Reconnect to the pending story
     * instead of treating the saved submission id as idle and allowing a duplicate PostStory.
     */
    private suspend fun recoverSubmission(submissionId: String) {
        val initial = synchronized(lock) { persistedSubmissions[submissionId] } ?: return
        val resolvedTicket = initial.ticket ?: run {
            val targetChatId = initial.targetChatId.takeIf { it != 0L } ?: ensureSelfChatId()
            var discovered = findBeingPostedStory(targetChatId, initial)
            val lookupWindow = if (initial.kind == "indeterminate") {
                INDETERMINATE_LOOKUP_MS
            } else {
                SUBMISSION_LOOKUP_MS
            }
            val deadline = System.currentTimeMillis() + lookupWindow
            while (discovered == null && System.currentTimeMillis() < deadline) {
                delay(2_000)
                discovered = findBeingPostedStory(targetChatId, initial)
            }
            val found = discovered
            if (found == null) {
                val completed = findRecentStory(targetChatId, initial.createdAt, initial)
                if (completed != null) {
                    updateSubmission(submissionId, StorySubmissionState.Published(completed))
                } else {
                    updateSubmission(submissionId, StorySubmissionState.Failed())
                }
                return
            }
            updateSubmission(submissionId, StorySubmissionState.Pending(found))
            found
        }
        val immediate = postEvents.replayCache.firstOrNull {
            matchesPendingStoryEvent(it, resolvedTicket.posterChatId, resolvedTicket.storyId)
        }
        if (immediate != null) {
            applyRecoveredEvent(submissionId, resolvedTicket, immediate)
            return
        }
        val terminal = CompletableDeferred<StoryPostEvent>()
        val eventJob = scope.launch {
            val event = postEvents.first {
                matchesPendingStoryEvent(it, resolvedTicket.posterChatId, resolvedTicket.storyId)
            }
            terminal.complete(event)
        }
        val deadline = System.currentTimeMillis() + SUBMISSION_RECOVERY_MS
        submissionTickets[resolvedTicket.posterChatId to resolvedTicket.storyId] = submissionId
        var misses = 0
        try {
            while (System.currentTimeMillis() < deadline) {
                if (terminal.isCompleted) {
                    applyRecoveredEvent(submissionId, resolvedTicket, terminal.await())
                    return
                }
                val story = runCatching {
                    requests(TdApi.GetStory(resolvedTicket.posterChatId, resolvedTicket.storyId, false))
                }.getOrNull()
                if (story != null) {
                    misses = 0
                    if (!story.isBeingPosted) {
                        updateSubmission(
                            submissionId,
                            StorySubmissionState.Published(
                                resolvedTicket.copy(
                                    storyId = story.id,
                                    isPending = false,
                                    fileIds = story.content.uploadFileIds()
                                )
                            )
                        )
                        return
                    }
                } else {
                    val active = runCatching {
                        requests(TdApi.GetChatActiveStories(resolvedTicket.posterChatId))
                    }.getOrNull()
                    val stillListed = active?.stories?.any { it.storyId == resolvedTicket.storyId } == true
                    if (active != null && !stillListed) {
                        misses++
                        if (misses >= 3) {
                            val recovered = findRecentStory(
                                resolvedTicket.posterChatId,
                                initial.createdAt,
                                initial
                            )
                            if (recovered != null) {
                                updateSubmission(
                                    submissionId,
                                    StorySubmissionState.Published(recovered)
                                )
                            } else {
                                updateSubmission(submissionId, StorySubmissionState.Failed())
                            }
                            return
                        }
                    } else {
                        misses = 0
                    }
                }
                delay(2_000)
            }
            updateSubmission(submissionId, StorySubmissionState.Failed())
        } finally {
            eventJob.cancel()
        }
    }

    private fun applyRecoveredEvent(
        submissionId: String,
        ticket: StoryPostTicket,
        event: StoryPostEvent
    ) {
        when (event) {
            is StoryPostEvent.Published -> updateSubmission(
                submissionId,
                StorySubmissionState.Published(
                    ticket.copy(
                        storyId = event.storyId,
                        isPending = false,
                        fileIds = event.fileIds
                    )
                )
            )
            is StoryPostEvent.Failed -> updateSubmission(
                submissionId,
                StorySubmissionState.Failed(reason = event.reason)
            )
        }
    }

    private suspend fun findBeingPostedStory(
        chatId: Long,
        fingerprint: PersistedSubmission
    ): StoryPostTicket? {
        if (fingerprint.mediaKind.isBlank()) return null
        if (fingerprint.caption.isEmpty() && (fingerprint.mediaWidth <= 0 || fingerprint.mediaHeight <= 0)) {
            return null
        }
        val active = runCatching { requests(TdApi.GetChatActiveStories(chatId)) }.getOrNull() ?: return null
        return active.stories?.toList().orEmpty()
            .filter { it.date.toLong() * 1_000L >= fingerprint.createdAt - PENDING_STORY_WINDOW_MS }
            .sortedByDescending { it.storyId }
            .take(20)
            .firstNotNullOfOrNull { info ->
                val story = runCatching { requests(TdApi.GetStory(chatId, info.storyId, false)) }.getOrNull()
                    ?: return@firstNotNullOfOrNull null
                if (!story.isBeingPosted || !story.matchesPendingFingerprint(fingerprint)) {
                    return@firstNotNullOfOrNull null
                }
                StoryPostTicket(chatId, story.id, true, story.content.uploadFileIds())
            }
    }

    private fun TdApi.Story.matchesPendingFingerprint(fingerprint: PersistedSubmission): Boolean {
        if (content.mediaKindKey() != fingerprint.mediaKind) return false
        if (caption?.text.orEmpty().trim() != fingerprint.caption) return false
        val dimensions = content.mediaDimensions()
        if (fingerprint.mediaWidth > 0 && dimensions != null && dimensions.first != fingerprint.mediaWidth) return false
        if (fingerprint.mediaHeight > 0 && dimensions != null && dimensions.second != fingerprint.mediaHeight) return false
        val size = content.primaryFileSize()
        if (fingerprint.mediaSize > 0L && size != null && size > 0L && size != fingerprint.mediaSize) return false
        return true
    }

    private suspend fun findRecentStory(chatId: Long, createdAt: Long, fingerprint: PersistedSubmission): StoryPostTicket? {
        if (fingerprint.mediaKind.isBlank() || fingerprint.mediaSize <= 0L) return null
        val active = runCatching { requests(TdApi.GetChatActiveStories(chatId)) }.getOrNull() ?: return null
        return active.stories?.toList().orEmpty()
            .filter { it.date.toLong() * 1_000L >= createdAt - RECENT_STORY_WINDOW_MS }
            .sortedByDescending { it.storyId }
            .take(20)
            .firstNotNullOfOrNull { info ->
                val story = runCatching { requests(TdApi.GetStory(chatId, info.storyId, false)) }.getOrNull()
                    ?: return@firstNotNullOfOrNull null
                if (story.isBeingPosted) return@firstNotNullOfOrNull null
                if (story.caption?.text.orEmpty().trim() != fingerprint.caption) return@firstNotNullOfOrNull null
                if (story.content.mediaKindKey() != fingerprint.mediaKind) return@firstNotNullOfOrNull null
                if (story.content.primaryFileSize() != fingerprint.mediaSize) return@firstNotNullOfOrNull null
                StoryPostTicket(chatId, story.id, false, story.content.uploadFileIds())
            }
    }

    private fun TdApi.StoryContent.mediaKindKey(): String? = when (this) {
        is TdApi.StoryContentPhoto -> MediaKind.Photo.name
        is TdApi.StoryContentVideo -> MediaKind.Video.name
        else -> null
    }

    private fun TdApi.StoryContent.primaryFileSize(): Long? = when (this) {
        is TdApi.StoryContentPhoto -> photo.sizes.orEmpty()
            .maxByOrNull { it.width.toLong() * it.height }
            ?.photo
            ?.let { it.size.takeIf { size -> size > 0L } ?: it.expectedSize }
        is TdApi.StoryContentVideo -> video.video.let { it.size.takeIf { size -> size > 0L } ?: it.expectedSize }
        else -> null
    }

    private fun TdApi.StoryContent.mediaDimensions(): Pair<Int, Int>? = when (this) {
        is TdApi.StoryContentPhoto -> photo.sizes.orEmpty()
            .maxByOrNull { it.width.toLong() * it.height }
            ?.let { it.width to it.height }
        is TdApi.StoryContentVideo -> video.width to video.height
        else -> null
    }

    suspend fun publishStory(
        submissionId: String,
        targetChatId: Long,
        media: OutgoingMedia,
        caption: String,
        privacy: StoryPrivacy,
        selectedUserIds: List<Long>,
        activePeriodSeconds: Int,
        protectContent: Boolean,
        keepInProfile: Boolean
    ): Result<StoryPostTicket> {
        // Submission is process-owned: leaving the publisher must not orphan an already submitted
        // PostStory request. The repository also tracks/handles its terminal update.
        persistSubmitting(submissionId, targetChatId, media, caption)
        updateSubmission(submissionId, StorySubmissionState.Submitting)
        val operation = scope.async {
            publishStoryRequest(
                submissionId, targetChatId, media, caption, privacy, selectedUserIds, activePeriodSeconds,
                protectContent, keepInProfile
            )
        }
        return operation.await()
    }

    private suspend fun publishStoryRequest(
        submissionId: String,
        targetChatId: Long,
        media: OutgoingMedia,
        caption: String,
        privacy: StoryPrivacy,
        selectedUserIds: List<Long>,
        activePeriodSeconds: Int,
        protectContent: Boolean,
        keepInProfile: Boolean
    ): Result<StoryPostTicket> {
        val result = storyRequest(POST_TIMEOUT_MS) {
            if (media.kind != MediaKind.Photo && media.kind != MediaKind.Video) {
                throw UserMessageException(UiText.Res(R.string.stories_media_invalid))
            }
            if (media.kind == MediaKind.Photo && (media.width != 1_080 || media.height != 1_920)) {
                throw UserMessageException(UiText.Res(R.string.stories_media_prepare_failed))
            }
            if (media.kind == MediaKind.Video && (media.width != 720 || media.height != 1_280)) {
                throw UserMessageException(UiText.Res(R.string.stories_video_prepare_failed))
            }
            if (media.kind == MediaKind.Video && media.durationSeconds !in 1..60) {
                throw UserMessageException(UiText.Res(R.string.stories_video_prepare_failed))
            }
            if (activePeriodSeconds !in STORY_ACTIVE_PERIODS) {
                throw UserMessageException(UiText.Res(R.string.stories_period_invalid))
            }
            if (privacy == StoryPrivacy.SelectedUsers && selectedUserIds.none { it > 0L }) {
                throw UserMessageException(UiText.Res(R.string.stories_selected_empty))
            }
            val chatId = targetChatId.takeIf { it != 0L } ?: ensureSelfChatId()
            val content = when (media.kind) {
                MediaKind.Photo -> TdApi.InputStoryContentPhoto(TdApi.InputFileLocal(media.path), intArrayOf())
                else -> TdApi.InputStoryContentVideo(
                    TdApi.InputFileLocal(media.path),
                    intArrayOf(),
                    media.durationSeconds.toDouble(),
                    0.0,
                    false
                )
            }
            val trimmed = caption.trim().takeIf { it.isNotEmpty() }
            val story = requests(TdApi.PostStory(
                chatId,
                content,
                null,
                trimmed?.let { TdApi.FormattedText(it, emptyArray()) },
                privacy.toTdSettings(selectedUserIds),
                intArrayOf(),
                activePeriodSeconds,
                null,
                keepInProfile,
                protectContent
            ), POST_TIMEOUT_MS)
            val ticket = StoryPostTicket(chatId, story.id, story.isBeingPosted, story.content.uploadFileIds())
            if (ticket.isPending) {
                updateSubmission(submissionId, StorySubmissionState.Pending(ticket))
                trackUpload(
                    ticket,
                    media,
                    submissionId,
                    initiallyAbandoned = takeAbandonedSubmission(submissionId)
                )
            } else {
                takeAbandonedSubmission(submissionId)
                markPublishedStoryUpload(media, ticket.fileIds)
                updateSubmission(submissionId, StorySubmissionState.Published(ticket))
            }
            ticket
        }
        if (result.isFailure) {
            val failure = result.exceptionOrNull()
            if (failure is UserMessageException && failure.cause is TimeoutCancellationException) {
                // TDLib keeps processing a request after the local wait times out. Keep the
                // submission indeterminate and reconcile it instead of allowing a duplicate.
                markSubmissionIndeterminate(submissionId)
            } else {
                updateSubmission(
                    submissionId,
                    StorySubmissionState.Failed(error = (failure as? UserMessageException)?.text)
                )
                if (takeAbandonedSubmission(submissionId)) discardStoryUpload(media)
            }
        }
        return result
    }

    /**
     * A pending story may be published after the publisher screen is gone. The repository keeps
     * listening for its terminal update and marks the staged upload evictable on success.
     */
    private fun trackUpload(
        ticket: StoryPostTicket,
        media: OutgoingMedia,
        submissionId: String,
        initiallyAbandoned: Boolean = false
    ) {
        val key = ticket.posterChatId to ticket.storyId
        if (initiallyAbandoned) {
            synchronized(lock) { abandonedUploads += key }
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val event = postEvents.first {
                    matchesPendingStoryEvent(it, ticket.posterChatId, ticket.storyId)
                }
                val abandoned = synchronized(lock) { abandonedUploads.remove(key) != null }
                when (event) {
                    is StoryPostEvent.Published -> {
                        markPublishedStoryUpload(media, event.fileIds)
                        updateSubmission(
                            submissionId,
                            StorySubmissionState.Published(
                                ticket.copy(
                                    storyId = event.storyId,
                                    isPending = false,
                                    fileIds = event.fileIds
                                )
                            )
                        )
                    }
                    is StoryPostEvent.Failed -> {
                        if (abandoned) discardStoryUpload(media)
                        updateSubmission(
                            submissionId,
                            StorySubmissionState.Failed(reason = event.reason)
                        )
                    }
                }
            } finally {
                synchronized(lock) {
                    uploadJobs.remove(key)
                    abandonedUploads.remove(key)
                }
            }
        }
        synchronized(lock) {
            uploadJobs.put(key, job)?.cancel()
        }
        job.start()
    }

    /** The publisher screen closed before the upload finished; cleanup is now the repository's job. */
    fun abandonUpload(posterChatId: Long, storyId: Int) {
        if (storyId == 0) return
        val key = posterChatId to storyId
        synchronized(lock) {
            if (uploadJobs.containsKey(key)) abandonedUploads += key
        }
    }

    private fun discardStoryUpload(media: OutgoingMedia) {
        val source = runCatching { File(media.path).canonicalFile }.getOrNull() ?: return
        val root = runCatching { uploadsRoot.canonicalFile }.getOrNull() ?: return
        if (!source.path.startsWith(root.path + File.separator)) return
        val parent = source.parentFile ?: return
        val folder = if (parent.name == "original" && parent.parentFile != null) parent.parentFile!! else parent
        if (folder.canonicalFile.parentFile == root) runCatching { folder.deleteRecursively() }
    }

    private fun markPublishedStoryUpload(media: OutgoingMedia, fileIds: List<Int>) {
        val source = File(media.path)
        val parent = source.parentFile ?: return
        val folder = if (parent.name == "original" && parent.parentFile != null) parent.parentFile!! else parent
        fileIds.asSequence().filter { it > 0 }.distinct().forEach { fileId ->
            runCatching { markSentUpload(folder, fileId, accountId) }
        }
    }

    private suspend fun ensureSelfChatId(): Long {
        synchronized(lock) { if (selfChatId > 0L) return selfChatId }
        val me = requests(TdApi.GetMe())
        val chat = requests(TdApi.CreatePrivateChat(me.id, false))
        profiles.putChat(chat)
        synchronized(lock) {
            selfId = me.id
            selfChatId = chat.id
            requestedChats += chat.id
            return chat.id
        }
    }

    private fun TdApi.Story.toDetail(): StoryDetail = StoryDetail(
        posterChatId = posterChatId,
        storyId = id,
        dateSeconds = date.toLong(),
        caption = caption?.text.orEmpty(),
        media = content.toStoryMedia(),
        isBeingPosted = isBeingPosted,
        canBeDeleted = canBeDeleted,
        canBeReplied = canBeReplied,
        canBeEdited = canBeEdited,
        canSetPrivacySettings = canSetPrivacySettings,
        canToggleIsPostedToChatPage = canToggleIsPostedToChatPage,
        isPostedToChatPage = isPostedToChatPage,
        privacy = privacySettings.toStoryPrivacy(),
        privacyUserIds = (privacySettings as? TdApi.StoryPrivacySettingsSelectedUsers)?.userIds?.toList().orEmpty(),
        chosenReaction = chosenReactionType?.let(::optionOf),
        viewCount = interactionInfo?.viewCount ?: 0,
        reactionCount = interactionInfo?.reactionCount ?: 0
    )

    private fun TdApi.StoryContent.toStoryMedia(): StoryMedia? = when (this) {
        is TdApi.StoryContentPhoto -> {
            val sizes = photo.sizes.orEmpty().filter { it.photo != null }
            val full = sizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
            val preview = sizes.filter { maxOf(it.width, it.height) >= PREVIEW_MIN_SIDE }
                .minByOrNull { it.width.toLong() * it.height }
                ?: sizes.minByOrNull { it.width.toLong() * it.height }
            val fullId = register(full.photo)
            val previewId = preview?.photo?.let(::register)?.takeUnless { it == fullId }
            StoryMedia(
                kind = StoryMediaKind.Photo,
                fileId = fullId,
                thumbnailFileId = previewId,
                width = full.width,
                height = full.height,
                minithumbnail = photo.minithumbnail?.data
            )
        }
        is TdApi.StoryContentVideo -> {
            val durationMillis = (video.duration * 1_000.0).roundToLong()
            StoryMedia(
                kind = StoryMediaKind.Video,
                fileId = register(video.video),
                thumbnailFileId = video.thumbnail?.file?.let(::register),
                width = video.width,
                height = video.height,
                durationSeconds = video.duration.toInt(),
                durationMillis = durationMillis,
                minithumbnail = video.minithumbnail?.data
            )
        }
        else -> null
    }

    private fun TdApi.StoryContent.uploadFileIds(): List<Int> = when (this) {
        is TdApi.StoryContentPhoto -> listOfNotNull(
            photo.sizes.orEmpty().maxByOrNull { it.width.toLong() * it.height }?.photo?.id
        )
        is TdApi.StoryContentVideo -> listOf(video.video.id)
        else -> emptyList()
    }

    private fun register(file: TdApi.File): Int {
        mediaFiles.register(file.toMediaFileState())
        return file.id
    }

    private fun StoryPrivacy.toTdSettings(selectedUserIds: List<Long>): TdApi.StoryPrivacySettings = when (this) {
        StoryPrivacy.Everyone -> TdApi.StoryPrivacySettingsEveryone(longArrayOf())
        StoryPrivacy.Contacts -> TdApi.StoryPrivacySettingsContacts(longArrayOf())
        StoryPrivacy.CloseFriends -> TdApi.StoryPrivacySettingsCloseFriends()
        StoryPrivacy.SelectedUsers -> TdApi.StoryPrivacySettingsSelectedUsers(
            selectedUserIds.filter { it > 0L }.distinct().toLongArray()
        )
    }

    private fun TdApi.StoryPrivacySettings?.toStoryPrivacy(): StoryPrivacy? = when (this) {
        is TdApi.StoryPrivacySettingsEveryone -> StoryPrivacy.Everyone
        is TdApi.StoryPrivacySettingsContacts -> StoryPrivacy.Contacts
        is TdApi.StoryPrivacySettingsCloseFriends -> StoryPrivacy.CloseFriends
        is TdApi.StoryPrivacySettingsSelectedUsers -> StoryPrivacy.SelectedUsers
        else -> null
    }

    private fun TdApi.CanPostStoryResult.toPermission(): StoryPostPermission = when (this) {
        is TdApi.CanPostStoryResultOk -> StoryPostPermission.Allowed(storyCount)
        is TdApi.CanPostStoryResultPremiumNeeded -> StoryPostPermission.Denied(StoryPostDenial.PremiumRequired)
        is TdApi.CanPostStoryResultActiveStoryLimitExceeded -> StoryPostPermission.Denied(StoryPostDenial.ActiveStoryLimit)
        is TdApi.CanPostStoryResultWeeklyLimitExceeded -> StoryPostPermission.Denied(StoryPostDenial.WeeklyLimit)
        is TdApi.CanPostStoryResultMonthlyLimitExceeded -> StoryPostPermission.Denied(StoryPostDenial.MonthlyLimit)
        is TdApi.CanPostStoryResultBoostNeeded -> StoryPostPermission.Denied(StoryPostDenial.BoostRequired)
        is TdApi.CanPostStoryResultLiveStoryIsActive -> StoryPostPermission.Denied(StoryPostDenial.LiveStoryActive)
        else -> StoryPostPermission.Denied(StoryPostDenial.Unavailable)
    }

    private fun TdApi.CanPostStoryResult?.toDenial(): StoryPostDenial = when (this) {
        is TdApi.CanPostStoryResultPremiumNeeded -> StoryPostDenial.PremiumRequired
        is TdApi.CanPostStoryResultActiveStoryLimitExceeded -> StoryPostDenial.ActiveStoryLimit
        is TdApi.CanPostStoryResultWeeklyLimitExceeded -> StoryPostDenial.WeeklyLimit
        is TdApi.CanPostStoryResultMonthlyLimitExceeded -> StoryPostDenial.MonthlyLimit
        is TdApi.CanPostStoryResultBoostNeeded -> StoryPostDenial.BoostRequired
        is TdApi.CanPostStoryResultLiveStoryIsActive -> StoryPostDenial.LiveStoryActive
        else -> StoryPostDenial.Unavailable
    }

    private suspend fun <T> storyRequest(timeoutMillis: Long = DEFAULT_TIMEOUT_MS, block: suspend () -> T): Result<T> =
        try {
            Result.success(withTimeout(timeoutMillis) { block() })
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(UserMessageException(UiText.Res(R.string.err_tdlib_request_timeout), timeout))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: UserMessageException) {
            Result.failure(error)
        } catch (error: Exception) {
            Result.failure(UserMessageException(error.storyErrorText(), error))
        }

    private fun Exception.storyErrorText(): UiText = when {
        message?.contains("FLOOD_WAIT") == true || (this as? TdRequestError)?.code == 429 ->
            UiText.Res(R.string.err_tdlib_flood_wait)
        message?.contains("PREMIUM", ignoreCase = true) == true ->
            UiText.Res(R.string.stories_denied_premium)
        message?.contains("STORY_", ignoreCase = true) == true ->
            UiText.Res(R.string.err_story_action_failed)
        else -> UiText.Res(R.string.err_tdlib_action_failed)
    }

    private data class PersistedSubmission(
        val kind: String,
        val targetChatId: Long,
        val ticket: StoryPostTicket?,
        val createdAt: Long,
        val caption: String = "",
        val mediaKind: String = "",
        val mediaSize: Long = 0L,
        val mediaWidth: Int = 0,
        val mediaHeight: Int = 0
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val POST_TIMEOUT_MS = 60_000L
        const val SUBMISSION_LOOKUP_MS = 60_000L
        const val INDETERMINATE_LOOKUP_MS = 5 * 60_000L
        const val SUBMISSION_RECOVERY_MS = 15 * 60_000L
        const val PENDING_STORY_WINDOW_MS = 15_000L
        const val RECENT_STORY_WINDOW_MS = 2 * 60_000L
        const val ARCHIVE_PAGE_SIZE = 100
        const val MAX_ACTIVE_STORY_PAGES = 5
        const val MAX_CACHED_STORY_DETAILS = 64
        const val PREVIEW_MIN_SIDE = 320
        const val DEFAULT_CAPTION_LENGTH = 1_024
        const val MAX_CAPTION_LENGTH = 4_096
        val STORY_ACTIVE_PERIODS = setOf(6 * 3600, 12 * 3600, 24 * 3600, 48 * 3600)
    }
}
