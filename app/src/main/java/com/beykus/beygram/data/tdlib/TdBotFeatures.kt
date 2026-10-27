package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Bot commands, inline mode, Mini App launches and Web App data. TDLib is the only source of commands/results and the
 * only component that turns a bot button into a Web App URL; the UI never opens a button's raw URL.
 */
internal class TdBotFeatures(client: () -> Client) {
    private val request = TdRequests(client)
    private val inlineBots = ConcurrentHashMap<String, CachedInlineBot>()

    suspend fun commandMenu(chatId: Long): Result<BotCommandMenu?> = requestResult {
        // This TDLib build exposes no GetBotCommands/UpdateBotCommands. BotCommandScope/GetCommands only manage the
        // current user's own bot, so another bot's group/channel commands cannot be resolved here; return no menu.
        val type = request(TdApi.GetChat(chatId)).type as? TdApi.ChatTypePrivate ?: return@requestResult null
        val user = request(TdApi.GetUser(type.userId))
        if (user.type !is TdApi.UserTypeBot) return@requestResult null
        val commands = request(TdApi.GetUserFullInfo(type.userId)).botInfo?.commands.orEmpty().mapNotNull { command ->
            val name = command.command.trim().removePrefix("/").trim()
            if (name.isEmpty()) null else BotCommand(name, command.description.trim())
        }
        if (commands.isEmpty()) null else BotCommandMenu(type.userId, commands)
    }

    suspend fun inlineQuery(chatId: Long, username: String, query: String, offset: String): Result<InlineBotResults?> = requestResult {
        val botUserId = inlineBot(username) ?: return@requestResult null
        val answer = request(TdApi.GetInlineQueryResults(botUserId, chatId, null, query, offset))
        val button = answer.button
        val buttonUrl = (button?.type as? TdApi.InlineQueryResultsButtonTypeWebApp)?.url?.let(::normalizedWebAppUrl)
        InlineBotResults(
            queryId = answer.inlineQueryId,
            botUserId = botUserId,
            results = answer.results.orEmpty().mapNotNull { inlineResult(it)?.copy(queryId = answer.inlineQueryId) },
            nextOffset = answer.nextOffset.orEmpty(),
            buttonText = if (buttonUrl == null) "" else button?.text.orEmpty().trim(),
            buttonWebAppUrl = buttonUrl.orEmpty()
        )
    }

    /**
     * Music from the optional music inline bot, alongside the account's own music search. The username comes from the
     * server's application config; the documented fallback is used only when that key is absent. A bot that does not
     * resolve gives null, so the caller shows the source as unavailable and keeps its own results.
     */
    suspend fun musicBotSearch(query: String, offset: String): Result<InlineBotResults?> = requestResult {
        val username = musicSearchUsername()
        val botUserId = inlineBot(username) ?: return@requestResult null
        // The user's own id is the Saved Messages chat, a chat the account always has, so it works as the query context.
        val savedMessagesChatId = request(TdApi.GetMe()).id
        val answer = request(TdApi.GetInlineQueryResults(botUserId, savedMessagesChatId, null, query, offset))
        InlineBotResults(
            queryId = answer.inlineQueryId,
            botUserId = botUserId,
            results = answer.results.orEmpty().mapNotNull { inlineResult(it)?.copy(queryId = answer.inlineQueryId) },
            nextOffset = answer.nextOffset.orEmpty()
        )
    }

    /**
     * Downloads one music bot track so the shared player can play it. TDLib only downloads an inline audio file
     * once it is sent, so the result goes to Saved Messages, is downloaded in full, and that temporary message is
     * removed afterwards. Sending clears the Saved Messages draft, so the draft is put back when nothing else has
     * written one meanwhile. A failed cleanup is ignored so the track still plays.
     */
    suspend fun musicBotTrack(queryId: Long, resultId: String, cacheDir: File): Result<MusicBotTrack?> = requestResult {
        val savedMessagesChatId = request(TdApi.GetMe()).id
        val savedDraft = request(TdApi.GetChat(savedMessagesChatId)).draftMessage
        val sent = request(TdApi.SendInlineQueryResultMessage().apply {
            chatId = savedMessagesChatId
            this.queryId = queryId
            this.resultId = resultId
            hideViaBot = false
        })
        var audioFileId: Int? = null
        try {
            val audio = (sent.content as? TdApi.MessageAudio)?.audio ?: return@requestResult null
            audioFileId = audio.audio.id
            val file = request(TdApi.DownloadFile(audio.audio.id, FILE_PRIORITY_MANUAL, 0L, 0L, true), DOWNLOAD_TIMEOUT_MS)
            if (!file.local.isDownloadingCompleted || file.local.path.isEmpty()) return@requestResult null
            val track = copyOutOfTdlib(File(file.local.path), cacheDir)
            MusicBotTrack(
                path = track.absolutePath,
                title = audio.title.orEmpty(),
                performer = audio.performer.orEmpty(),
                durationSeconds = audio.duration,
                mimeType = audio.mimeType.orEmpty(),
                sizeBytes = file.size.toLong()
            )
        } finally {
            runCatching { deleteTemporaryMessage(savedMessagesChatId, sent, audioFileId) }
            runCatching { restoreSavedMessagesDraft(savedMessagesChatId, savedDraft) }
        }
    }

    /**
     * TDLib deletes a downloaded file together with the message it came from, so the track is copied into the app
     * cache first. Earlier copies are removed so the cache holds only the latest bot track.
     */
    private fun copyOutOfTdlib(source: File, cacheDir: File): File {
        cacheDir.listFiles()?.forEach { it.delete() }
        cacheDir.mkdirs()
        return source.copyTo(File(cacheDir, source.name), overwrite = true)
    }

    /** Uses the temporary id only while TDLib still reports it as pending; once sent, the confirmed id is looked up. */
    private suspend fun deleteTemporaryMessage(chatId: Long, sent: TdApi.Message, audioFileId: Int?) {
        val messageId = if (sent.sendingState == null) {
            sent.id
        } else {
            request(TdApi.GetChatHistory(chatId, 0L, 0, RECENT_HISTORY_LIMIT, false)).messages.orEmpty()
                .firstOrNull { (it.content as? TdApi.MessageAudio)?.audio?.audio?.id == audioFileId }?.id
        } ?: return
        request(TdApi.DeleteMessages(chatId, longArrayOf(messageId), true))
    }

    /** Puts the draft back only if nothing has written one since the send, so newer text is never overwritten. */
    private suspend fun restoreSavedMessagesDraft(chatId: Long, savedDraft: TdApi.DraftMessage?) {
        if (savedDraft == null) return
        if (request(TdApi.GetChat(chatId)).draftMessage != null) return
        request(TdApi.SetChatDraftMessage().apply {
            this.chatId = chatId
            topicId = null
            draftMessage = savedDraft
        })
    }

    private suspend fun musicSearchUsername(): String {
        val configured = try {
            val config = request(TdApi.GetApplicationConfig()) as? TdApi.JsonValueObject
            config?.members.orEmpty()
                .firstOrNull { it.key == MUSIC_SEARCH_CONFIG_KEY }
                ?.value
                ?.let { (it as? TdApi.JsonValueString)?.value }
                .orEmpty()
                .trim()
        } catch (error: TdRequestError) {
            ""
        }
        return configured.ifEmpty { MUSIC_BOT_FALLBACK }
    }

    suspend fun sendInlineResult(
        chatId: Long,
        queryId: Long,
        resultId: String,
        replyToMessageId: Long?,
        forumTopicId: Long,
        options: MessageSendOptions
    ): Result<Unit> = conversationResult {
        if (!options.scheduledTimeIsFuture()) throw UserMessageException(UiText.Res(R.string.err_schedule_time_future))
        val replyTarget = replyToMessageId?.let {
            if (!request(TdApi.GetMessageProperties(chatId, it)).canBeReplied) {
                throw UserMessageException(UiText.Res(R.string.err_message_cannot_reply))
            }
            request(TdApi.GetMessage(chatId, it))
        }
        val function = TdApi.SendInlineQueryResultMessage().apply {
            this.chatId = chatId
            this.queryId = queryId
            this.resultId = resultId
            hideViaBot = false
            this.options = sendOptions(options)
            if (replyTarget != null) {
                topicId = replyTarget.topicId
                replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyTarget.id; pollOptionId = "" }
            } else if (forumTopicId > 0L) {
                topicId = TdApi.MessageTopicForum(forumTopicId.toInt())
            }
        }
        request(function)
        Unit
    }

    /**
     * Opens a Web App through TDLib. [botUserId] may be zero for a reply-keyboard button, in which case the private
     * chat must resolve to a bot. TDLib returns the final URL and its same-origin requirement; only https is accepted.
     * [buttonText] is the text of the button that opened the Web App and is needed later by SendWebAppData.
     */
    suspend fun openWebApp(
        chatId: Long,
        botUserId: Long,
        url: String,
        source: BotWebAppSource,
        forumTopicId: Long,
        replyToMessageId: Long?,
        theme: BotWebAppTheme,
        buttonText: String
    ): Result<BotWebAppLaunch> = requestResult {
        val requestedUrl = normalizedWebAppUrl(url) ?: throw UserMessageException(UiText.Res(R.string.err_webapp_insecure_url))
        val botId = resolveBotUserId(chatId, botUserId)
        val openParameters = webAppParameters(theme)
        if (source == BotWebAppSource.InlineKeyboard) {
            // Inline-keyboard buttons are launched through OpenWebApp, which returns a launch id to close later.
            val info = request(TdApi.OpenWebApp().apply {
                this.chatId = chatId
                this.botUserId = botId
                this.url = requestedUrl
                topicId = if (forumTopicId > 0L) TdApi.MessageTopicForum(forumTopicId.toInt()) else null
                replyTo = replyToMessageId?.takeIf { it > 0L }?.let { messageId ->
                    TdApi.InputMessageReplyToMessage().apply { this.messageId = messageId; pollOptionId = "" }
                }
                parameters = openParameters
            })
            val resolvedUrl = normalizedWebAppUrl(info.url?.url.orEmpty())
                ?: throw UserMessageException(UiText.Res(R.string.err_webapp_insecure_url))
            BotWebAppLaunch(info.launchId, resolvedUrl, info.url?.requireSameOrigin == true, botId, buttonText)
        } else {
            // Reply-keyboard and inline-result buttons are resolved with GetWebAppUrl and have no launch id.
            val info = request(TdApi.GetWebAppUrl(botId, requestedUrl, openParameters))
            val resolvedUrl = normalizedWebAppUrl(info.url.orEmpty())
                ?: throw UserMessageException(UiText.Res(R.string.err_webapp_insecure_url))
            BotWebAppLaunch(0L, resolvedUrl, info.requireSameOrigin, botId, buttonText)
        }
    }

    /**
     * Forwards Mini App data to the bot. TDLib requires the bot id and the text of the button that opened the Web App;
     * the UI has already checked event shape, while this layer re-checks size, JSON and TDLib limits. TDLib documents
     * this flow for keyboard Web Apps; other sources are attempted and any refusal is shown to the user.
     */
    suspend fun sendWebAppData(botUserId: Long, buttonText: String, data: String): Result<Unit> = requestResult {
        if (botUserId <= 0L) throw UserMessageException(UiText.Res(R.string.err_webapp_unavailable))
        if (buttonText.isBlank()) throw UserMessageException(UiText.Res(R.string.err_webapp_data_failed))
        when (validateBotWebAppData(data)) {
            BotWebAppDataError.Empty, BotWebAppDataError.InvalidJson ->
                throw UserMessageException(UiText.Res(R.string.err_webapp_data_invalid))
            BotWebAppDataError.TooLarge ->
                throw UserMessageException(UiText.Res(R.string.err_webapp_data_too_large))
            null -> Unit
        }
        try {
            request(TdApi.SendWebAppData(botUserId, buttonText, data))
        } catch (error: TdRequestError) {
            val tooLarge = error.message?.contains("TOO_LONG") == true
            throw UserMessageException(
                UiText.Res(if (tooLarge) R.string.err_webapp_data_too_large else R.string.err_webapp_data_failed),
                error
            )
        }
        Unit
    }

    suspend fun closeWebApp(launchId: Long): Result<Unit> =
        // The launch id is an opaque signed query id; only zero is the "no launch" sentinel.
        if (launchId == 0L) Result.success(Unit)
        else requestResult { request(TdApi.CloseWebApp(launchId)); Unit }

    /**
     * Null when [username] is not an inline bot. Both outcomes of a completed lookup are cached, so typing more of the
     * same draft does not repeat it. A failed lookup is not an answer: it returns null silently and is retried later.
     */
    private suspend fun inlineBot(username: String): Long? {
        val now = System.currentTimeMillis()
        inlineBots[username]?.takeIf { now - it.resolvedAtMillis < INLINE_BOT_CACHE_MILLIS }?.let { return it.userId }
        val userId = try {
            resolveInlineBot(username)
        } catch (timeout: TimeoutCancellationException) {
            return null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return null
        }
        if (inlineBots.size >= MAX_CACHED_INLINE_BOTS) inlineBots.clear()
        inlineBots[username] = CachedInlineBot(userId, now)
        return userId
    }

    /** Null for a username that is unknown, a user, a channel, or a bot without inline mode. */
    private suspend fun resolveInlineBot(username: String): Long? {
        val chat = try {
            request(TdApi.SearchPublicChat(username))
        } catch (error: TdRequestError) {
            if (error.message?.contains("USERNAME_") == true) return null
            throw error
        }
        val type = chat.type as? TdApi.ChatTypePrivate ?: return null
        val bot = request(TdApi.GetUser(type.userId)).type as? TdApi.UserTypeBot ?: return null
        return type.userId.takeIf { bot.isInline }
    }

    private suspend fun resolveBotUserId(chatId: Long, candidate: Long): Long {
        val id = candidate.takeIf { it > 0L } ?: ((request(TdApi.GetChat(chatId)).type as? TdApi.ChatTypePrivate)?.userId
            ?: throw UserMessageException(UiText.Res(R.string.err_webapp_unavailable)))
        if (request(TdApi.GetUser(id)).type !is TdApi.UserTypeBot) {
            throw UserMessageException(UiText.Res(R.string.err_webapp_unavailable))
        }
        return id
    }

    private fun webAppParameters(theme: BotWebAppTheme) = TdApi.WebAppOpenParameters(
        TdApi.ThemeParameters(
            theme.background,
            theme.surface,
            theme.background,
            theme.background,
            theme.surface,
            theme.divider,
            theme.text,
            theme.accent,
            theme.text,
            theme.textSecondary,
            theme.danger,
            theme.textSecondary,
            theme.accent,
            theme.accent,
            theme.onAccent
        ),
        "BeyGram",
        TdApi.WebAppOpenModeFullSize()
    )

    /** [userId] is null when the username was resolved to something that is not an inline bot. */
    private data class CachedInlineBot(val userId: Long?, val resolvedAtMillis: Long)

    private companion object {
        const val INLINE_BOT_CACHE_MILLIS = 10 * 60 * 1000L
        const val MAX_CACHED_INLINE_BOTS = 32
        const val MUSIC_SEARCH_CONFIG_KEY = "music_search_username"
        const val MUSIC_BOT_FALLBACK = "ugcmusicbot"
        const val FILE_PRIORITY_MANUAL = 32
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L
        const val RECENT_HISTORY_LIMIT = 10
    }
}

private fun inlineResult(result: TdApi.InlineQueryResult): InlineBotResult? = when (result) {
    is TdApi.InlineQueryResultArticle -> InlineBotResult(
        result.id, result.title, result.description, InlineBotResultKind.Article, result.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultPhoto -> InlineBotResult(
        result.id, result.title, result.description, InlineBotResultKind.Photo, previewPhotoFileId(result.photo)
    )
    is TdApi.InlineQueryResultVideo -> InlineBotResult(
        result.id, result.title, result.description, InlineBotResultKind.Video, result.video?.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultAnimation -> InlineBotResult(
        result.id, result.title, "", InlineBotResultKind.Animation, result.animation?.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultAudio -> InlineBotResult(
        result.id, result.audio?.title.orEmpty(), result.audio?.performer.orEmpty(), InlineBotResultKind.Audio,
        result.audio?.albumCoverThumbnail?.file?.id
    )
    is TdApi.InlineQueryResultDocument -> InlineBotResult(
        result.id, result.title, result.description, InlineBotResultKind.Document, result.document?.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultVoiceNote -> InlineBotResult(
        result.id, result.title, "", InlineBotResultKind.Voice
    )
    is TdApi.InlineQueryResultSticker -> InlineBotResult(
        result.id, result.sticker?.emoji.orEmpty(), "", InlineBotResultKind.Sticker, result.sticker?.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultContact -> InlineBotResult(
        result.id,
        listOfNotNull(result.contact?.firstName, result.contact?.lastName).filter { it.isNotBlank() }.joinToString(" "),
        result.contact?.phoneNumber.orEmpty(),
        InlineBotResultKind.Contact,
        result.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultLocation -> InlineBotResult(
        result.id, result.title, "", InlineBotResultKind.Location, result.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultVenue -> InlineBotResult(
        result.id, result.venue?.title.orEmpty(), result.venue?.address.orEmpty(), InlineBotResultKind.Venue,
        result.thumbnail?.file?.id
    )
    is TdApi.InlineQueryResultGame -> InlineBotResult(
        result.id, result.game?.title.orEmpty(), result.game?.description.orEmpty(), InlineBotResultKind.Game,
        previewPhotoFileId(result.game?.photo)
    )
    else -> null
}

/** Inline previews are 44dp, so download the smallest 96px+ size instead of a full-resolution photo. */
private fun previewPhotoFileId(photo: TdApi.Photo?): Int? {
    val sizes = photo?.sizes.orEmpty()
    val preferred = sizes.filter { it.width >= 96 }.minByOrNull { it.width } ?: sizes.minByOrNull { it.width }
    return preferred?.photo?.id
}
