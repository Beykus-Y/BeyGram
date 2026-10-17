package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.*
import org.drinkless.tdlib.TdApi

internal fun messageFormattedText(content: TdApi.MessageContent): TdApi.FormattedText? = when (content) {
    is TdApi.MessageText -> content.text
    is TdApi.MessagePhoto -> content.caption
    is TdApi.MessageVideo -> content.caption
    is TdApi.MessageAnimation -> content.caption
    is TdApi.MessageAudio -> content.caption
    is TdApi.MessageDocument -> content.caption
    is TdApi.MessageVoiceNote -> content.caption
    else -> null
}

internal fun TdApi.FormattedText.domainEntities(): List<MessageTextEntity> = validTextEntities(text, entities.orEmpty().mapNotNull { entity ->
    var argument = ""
    val kind = when (val type = entity.type) {
        is TdApi.TextEntityTypeBold -> TextStyleKind.Bold
        is TdApi.TextEntityTypeItalic -> TextStyleKind.Italic
        is TdApi.TextEntityTypeUnderline -> TextStyleKind.Underline
        is TdApi.TextEntityTypeStrikethrough -> TextStyleKind.Strike
        is TdApi.TextEntityTypeCode -> TextStyleKind.Code
        is TdApi.TextEntityTypePre -> TextStyleKind.Pre
        is TdApi.TextEntityTypePreCode -> { argument = type.language; TextStyleKind.Pre }
        is TdApi.TextEntityTypeBlockQuote, is TdApi.TextEntityTypeExpandableBlockQuote -> TextStyleKind.Quote
        is TdApi.TextEntityTypeSpoiler -> TextStyleKind.Spoiler
        is TdApi.TextEntityTypeUrl -> TextStyleKind.Url
        is TdApi.TextEntityTypeTextUrl -> { argument = type.url; TextStyleKind.Url }
        is TdApi.TextEntityTypeMention -> TextStyleKind.Mention
        is TdApi.TextEntityTypeMentionName -> { argument = type.userId.toString(); TextStyleKind.MentionName }
        is TdApi.TextEntityTypeEmailAddress -> TextStyleKind.Email
        is TdApi.TextEntityTypePhoneNumber -> TextStyleKind.Phone
        is TdApi.TextEntityTypeBotCommand -> TextStyleKind.Command
        is TdApi.TextEntityTypeHashtag, is TdApi.TextEntityTypeCashtag -> TextStyleKind.Hashtag
        is TdApi.TextEntityTypeCustomEmoji -> { argument = type.customEmojiId.toString(); TextStyleKind.CustomEmoji }
        else -> return@mapNotNull null
    }
    MessageTextEntity(entity.offset, entity.length, kind, argument)
})

internal fun formattedText(text: String, entities: List<MessageTextEntity>) = TdApi.FormattedText(text, validTextEntities(text, entities).sortedWith(compareBy<MessageTextEntity> { it.offset }.thenByDescending { it.length }).mapNotNull { entity ->
    val type = when (entity.kind) {
        TextStyleKind.Bold -> TdApi.TextEntityTypeBold()
        TextStyleKind.Italic -> TdApi.TextEntityTypeItalic()
        TextStyleKind.Underline -> TdApi.TextEntityTypeUnderline()
        TextStyleKind.Strike -> TdApi.TextEntityTypeStrikethrough()
        TextStyleKind.Code -> TdApi.TextEntityTypeCode()
        TextStyleKind.Pre -> if (entity.argument.isEmpty()) TdApi.TextEntityTypePre() else TdApi.TextEntityTypePreCode(entity.argument)
        TextStyleKind.Quote -> TdApi.TextEntityTypeBlockQuote()
        TextStyleKind.Spoiler -> TdApi.TextEntityTypeSpoiler()
        TextStyleKind.Url -> if (entity.argument.isEmpty()) TdApi.TextEntityTypeUrl() else TdApi.TextEntityTypeTextUrl(entity.argument)
        TextStyleKind.Mention -> TdApi.TextEntityTypeMention()
        TextStyleKind.MentionName -> entity.argument.toLongOrNull()?.let { TdApi.TextEntityTypeMentionName(it) }
        TextStyleKind.Email -> TdApi.TextEntityTypeEmailAddress()
        TextStyleKind.Phone -> TdApi.TextEntityTypePhoneNumber()
        TextStyleKind.Command -> TdApi.TextEntityTypeBotCommand()
        TextStyleKind.Hashtag -> TdApi.TextEntityTypeHashtag()
        TextStyleKind.CustomEmoji -> entity.argument.toLongOrNull()?.let { TdApi.TextEntityTypeCustomEmoji(it) }
    } ?: return@mapNotNull null
    TdApi.TextEntity(entity.offset, entity.length, type)
}.toTypedArray())

internal fun inlineKeyboard(markup: TdApi.ReplyMarkup?): List<List<BotButton>> = (markup as? TdApi.ReplyMarkupInlineKeyboard)?.rows.orEmpty().map { row -> row.map { button ->
    when (val type = button.type) {
        is TdApi.InlineKeyboardButtonTypeCallback -> BotButton(button.text, BotButtonKind.Callback, data = type.data.toList())
        is TdApi.InlineKeyboardButtonTypeUrl -> BotButton(button.text, BotButtonKind.Url, type.url)
        is TdApi.InlineKeyboardButtonTypeCopyText -> BotButton(button.text, BotButtonKind.Copy, type.text)
        is TdApi.InlineKeyboardButtonTypeUser -> BotButton(button.text, BotButtonKind.User, type.userId.toString())
        else -> BotButton(button.text, BotButtonKind.Unsupported)
    }
} }

internal fun botKeyboard(message: TdApi.Message?): BotKeyboard? = when (val markup = message?.replyMarkup) {
    is TdApi.ReplyMarkupShowKeyboard -> BotKeyboard(message.id, markup.rows.orEmpty().map { row -> row.map {
        BotButton(it.text, if (it.type is TdApi.KeyboardButtonTypeText) BotButtonKind.Text else BotButtonKind.Unsupported)
    } }, markup.oneTime, markup.isPersistent, markup.inputFieldPlaceholder)
    is TdApi.ReplyMarkupForceReply -> BotKeyboard(message.id, emptyList(), placeholder = markup.inputFieldPlaceholder, forceReply = true)
    else -> null
}
