package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Bot commands, inline mode and Mini App launches. TDLib is the only source of commands/results and the only
 * component that turns a bot button into a Web App URL; the UI never opens a button's raw URL.
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

    suspend fun inlineQuery(chatId: Long, username: String, query: String, offset: String): Result<InlineBotResults> = requestResult {
        val botUserId = inlineBot(username)
        val answer = request(TdApi.GetInlineQueryResults(botUserId, chatId, null, query, offset))
        val button = answer.button
        val buttonUrl = (button?.type as? TdApi.InlineQueryResultsButtonTypeWebApp)?.url?.let(::normalizedWebAppUrl)
        InlineBotResults(
            queryId = answer.inlineQueryId,
            botUserId = botUserId,
            results = answer.results.orEmpty().mapNotNull(::inlineResult),
            nextOffset = answer.nextOffset.orEmpty(),
            buttonText = if (buttonUrl == null) "" else button?.text.orEmpty().trim(),
            buttonWebAppUrl = buttonUrl.orEmpty()
        )
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
     */
    suspend fun openWebApp(
        chatId: Long,
        botUserId: Long,
        url: String,
        source: BotWebAppSource,
        forumTopicId: Long,
        replyToMessageId: Long?,
        theme: BotWebAppTheme
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
            BotWebAppLaunch(info.launchId, resolvedUrl, info.url?.requireSameOrigin == true)
        } else {
            // Reply-keyboard and inline-result buttons are resolved with GetWebAppUrl and have no launch id.
            val info = request(TdApi.GetWebAppUrl(botId, requestedUrl, openParameters))
            val resolvedUrl = normalizedWebAppUrl(info.url.orEmpty())
                ?: throw UserMessageException(UiText.Res(R.string.err_webapp_insecure_url))
            BotWebAppLaunch(0L, resolvedUrl, info.requireSameOrigin)
        }
    }

    suspend fun closeWebApp(launchId: Long): Result<Unit> =
        // The launch id is an opaque signed query id; only zero is the "no launch" sentinel.
        if (launchId == 0L) Result.success(Unit)
        else requestResult { request(TdApi.CloseWebApp(launchId)); Unit }

    private suspend fun inlineBot(username: String): Long {
        val now = System.currentTimeMillis()
        val cached = inlineBots[username]?.takeIf { now - it.resolvedAtMillis < INLINE_BOT_CACHE_MILLIS }
        if (cached != null) return cached.userId
        val chat = request(TdApi.SearchPublicChat(username))
        val type = chat.type as? TdApi.ChatTypePrivate
            ?: throw UserMessageException(UiText.Res(R.string.err_inline_bot_unavailable))
        val user = request(TdApi.GetUser(type.userId))
        val bot = user.type as? TdApi.UserTypeBot
            ?: throw UserMessageException(UiText.Res(R.string.err_inline_bot_unavailable))
        if (!bot.isInline) throw UserMessageException(UiText.Res(R.string.err_inline_bot_unavailable))
        if (inlineBots.size >= MAX_CACHED_INLINE_BOTS) inlineBots.clear()
        inlineBots[username] = CachedInlineBot(type.userId, now)
        return type.userId
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

    private data class CachedInlineBot(val userId: Long, val resolvedAtMillis: Long)

    private companion object {
        const val INLINE_BOT_CACHE_MILLIS = 10 * 60 * 1000L
        const val MAX_CACHED_INLINE_BOTS = 32
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
