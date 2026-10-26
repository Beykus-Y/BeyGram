package com.beykus.beygram.data.tdlib

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.domain.model.CallStatus
import com.beykus.beygram.domain.model.formatStarAmount
import org.drinkless.tdlib.TdApi
import java.math.BigDecimal

/**
 * Converts TDLib service content to text for both the chat timeline and chat-list preview.
 * Unsupported regular content is deliberately not identified as a service event.
 */
/** Resolve the affected member, not the admin who performed the action. */
internal fun serviceAffectedUserId(content: TdApi.MessageContent, senderId: TdApi.MessageSender?): Long {
    val actor = (senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
    return when (content) {
        is TdApi.MessageChatJoinByLink, is TdApi.MessageChatJoinByRequest -> actor
        is TdApi.MessageChatAddMembers -> content.memberUserIds.singleOrNull() ?: 0L
        is TdApi.MessageChatDeleteMember -> content.userId
        else -> 0L
    }
}

internal data class TdServiceEvent(val text: String, val userId: Long = 0L)

/** The event actor or affected member is preserved separately so the UI can open their profile. */
internal class TdServiceMessageMapper(private val baseContext: Context) {
    // The application context has no in-app locale below API 33; follow the selected language.
    @Volatile private var localized: Pair<String, Context>? = null
    private val context: Context
        get() {
            val language = AppLocale.selected(baseContext)
            localized?.takeIf { it.first == language }?.let { return it.second }
            return AppLocale.context(baseContext).also { localized = language to it }
        }

    fun event(message: TdApi.Message, nameForUser: (Long) -> String): TdServiceEvent? {
        if (message.content is TdApi.MessageCall) {
            return TdServiceEvent(callText(message.content as TdApi.MessageCall, message.isOutgoing))
        }
        val base = text(message.content, message.isOutgoing) ?: return null
        val userId = serviceAffectedUserId(message.content, message.senderId)
        if (userId <= 0L) return TdServiceEvent(base)
        val name = nameForUser(userId).ifBlank {
            context.getString(R.string.service_unknown_member, userId.toString())
        }
        val label = when (message.content) {
            is TdApi.MessageChatJoinByLink -> context.getString(R.string.service_join_link_named, name)
            is TdApi.MessageChatJoinByRequest -> context.getString(R.string.service_join_request_named, name)
            is TdApi.MessageChatAddMembers -> context.getString(R.string.service_members_added_named, name)
            is TdApi.MessageChatDeleteMember -> context.getString(R.string.service_member_removed_named, name)
            else -> base
        }
        return TdServiceEvent(label, userId)
    }

    internal fun callText(call: TdApi.MessageCall, isOutgoing: Boolean): String {
        val direction = context.getString(if (isOutgoing) R.string.calls_direction_outgoing else R.string.calls_direction_incoming)
        val kind = context.getString(if (call.isVideo) R.string.calls_kind_video else R.string.calls_kind_call)
        return when (call.discardReason.toCallStatus(call.duration, isOutgoing)) {
            CallStatus.Answered -> context.getString(R.string.service_call_answered, direction, kind, callEventDuration(call.duration))
            CallStatus.Missed -> context.getString(R.string.service_call_missed, kind)
            CallStatus.Declined -> context.getString(R.string.service_call_declined, direction, kind)
            CallStatus.NoAnswer -> context.getString(R.string.service_call_no_answer, direction, kind)
        }
    }

    /** [isOutgoing] only changes gift wording; without it a gift line stays neutral. */
    fun text(content: TdApi.MessageContent, isOutgoing: Boolean? = null): String? = when (content) {
        is TdApi.MessageCall -> context.getString(if (content.isVideo) R.string.calls_kind_video else R.string.calls_kind_call)
        is TdApi.MessageBasicGroupChatCreate -> context.getString(R.string.service_group_created)
        is TdApi.MessageSupergroupChatCreate -> context.getString(R.string.service_supergroup_created)
        is TdApi.MessageChatChangeTitle -> context.getString(R.string.service_title_changed, content.title)
        is TdApi.MessageChatChangePhoto -> context.getString(R.string.service_photo_changed)
        is TdApi.MessageChatDeletePhoto -> context.getString(R.string.service_photo_deleted)
        is TdApi.MessageChatAddMembers -> context.getString(R.string.service_members_added)
        is TdApi.MessageChatDeleteMember -> context.getString(R.string.service_member_removed)
        is TdApi.MessageChatJoinByLink -> context.getString(R.string.service_join_link)
        is TdApi.MessageChatJoinByRequest -> context.getString(R.string.service_join_request)
        is TdApi.MessagePinMessage -> context.getString(R.string.service_message_pinned)
        is TdApi.MessageForumTopicCreated -> context.getString(R.string.service_topic_created, content.name)
        is TdApi.MessageForumTopicEdited -> context.getString(R.string.service_topic_edited)
        is TdApi.MessageForumTopicIsClosedToggled -> context.getString(R.string.service_topic_closed_changed)
        is TdApi.MessageForumTopicIsHiddenToggled -> context.getString(R.string.service_topic_hidden_changed)
        is TdApi.MessageVideoChatStarted -> context.getString(R.string.service_videochat_started)
        is TdApi.MessageVideoChatEnded -> context.getString(R.string.service_videochat_ended)
        is TdApi.MessageChatSetMessageAutoDeleteTime -> context.getString(R.string.service_autodelete_changed)
        is TdApi.MessageChatSetBackground -> context.getString(R.string.service_background_changed)
        is TdApi.MessageGift -> giftText(content, isOutgoing)
        is TdApi.MessageUpgradedGift -> context.getString(
            R.string.service_upgraded_gift,
            content.gift.title.ifBlank { content.gift.name },
            content.gift.number
        )
        is TdApi.MessageRefundedUpgradedGift -> context.getString(R.string.service_upgraded_gift_refunded)
        is TdApi.MessageGiftedPremium -> premiumText(
            content.monthCount,
            content.dayCount,
            R.plurals.service_gift_premium_months,
            R.plurals.service_gift_premium_days,
            R.string.service_gift_premium
        )
        // The code itself is a redeemable secret and is never part of the chat text.
        is TdApi.MessagePremiumGiftCode -> premiumText(
            content.monthCount,
            content.dayCount,
            R.plurals.service_gift_premium_code_months,
            R.plurals.service_gift_premium_code_days,
            R.string.service_gift_premium_code
        )
        is TdApi.MessageGiftedStars -> context.getString(
            R.string.service_gifted_stars,
            formatStarAmount(content.starCount)
        )
        is TdApi.MessageGiftedTon -> context.getString(R.string.service_gifted_ton, tonAmountText(content.tonAmount))
        is TdApi.MessageGiveaway -> context.resources.getQuantityString(
            R.plurals.service_giveaway_started,
            content.winnerCount,
            content.winnerCount
        )
        is TdApi.MessageGiveawayCreated -> context.getString(
            R.string.service_giveaway_stars_created,
            formatStarAmount(content.starCount)
        )
        is TdApi.MessageGiveawayPrizeStars -> context.getString(
            R.string.service_giveaway_prize_stars,
            formatStarAmount(content.starCount)
        )
        is TdApi.MessageGiveawayCompleted -> context.getString(R.string.service_giveaway_completed)
        is TdApi.MessageGiveawayWinners ->
            if (content.wasRefunded) context.getString(R.string.service_giveaway_refunded)
            else context.getString(R.string.service_giveaway_winners, content.winnerCount)
        is TdApi.MessagePaidMessagesRefunded -> context.getString(
            R.string.service_paid_messages_refunded,
            formatStarAmount(content.starCount)
        )
        is TdApi.MessagePaidMessagePriceChanged ->
            if (content.paidMessageStarCount > 0L) context.getString(
                R.string.service_paid_message_price,
                formatStarAmount(content.paidMessageStarCount)
            )
            else context.getString(R.string.service_paid_message_free)
        else -> null
    }

    private fun giftText(content: TdApi.MessageGift, isOutgoing: Boolean?): String {
        val stars = content.gift?.starCount ?: 0L
        val line = when (giftServiceVariant(content.wasRefunded, content.wasConverted, isOutgoing)) {
            GiftServiceVariant.Refunded -> context.getString(R.string.service_gift_refunded)
            GiftServiceVariant.Converted ->
                if (content.sellStarCount > 0L) context.getString(
                    R.string.service_gift_converted_stars,
                    formatStarAmount(content.sellStarCount)
                )
                else context.getString(R.string.service_gift_converted)
            GiftServiceVariant.Sent -> starLine(stars, R.string.service_gift_sent_stars, R.string.service_gift_sent)
            GiftServiceVariant.Received -> starLine(stars, R.string.service_gift_received_stars, R.string.service_gift_received)
            GiftServiceVariant.Unspecified -> starLine(stars, R.string.service_gift_stars, R.string.service_gift)
        }
        // The note is the only place the chat shows what the sender wrote with the gift.
        val note = content.text?.text?.trim().orEmpty()
        return if (note.isEmpty()) line else "$line\n${context.getString(R.string.service_gift_note, note)}"
    }

    private fun starLine(stars: Long, @StringRes withStars: Int, @StringRes plain: Int): String =
        if (stars > 0L) context.getString(withStars, formatStarAmount(stars)) else context.getString(plain)

    private fun premiumText(
        monthCount: Int,
        dayCount: Int,
        @PluralsRes monthsRes: Int,
        @PluralsRes daysRes: Int,
        @StringRes plainRes: Int
    ): String = when (serviceDurationUnit(monthCount, dayCount)) {
        ServiceDurationUnit.Months -> context.resources.getQuantityString(monthsRes, monthCount, monthCount)
        ServiceDurationUnit.Days -> context.resources.getQuantityString(daysRes, dayCount, dayCount)
        ServiceDurationUnit.None -> context.getString(plainRes)
    }
}

/** Which gift line applies. Decided without Context so the precedence stays unit-testable. */
internal enum class GiftServiceVariant { Refunded, Converted, Sent, Received, Unspecified }

internal fun giftServiceVariant(wasRefunded: Boolean, wasConverted: Boolean, isOutgoing: Boolean?): GiftServiceVariant = when {
    wasRefunded -> GiftServiceVariant.Refunded
    wasConverted -> GiftServiceVariant.Converted
    isOutgoing == true -> GiftServiceVariant.Sent
    isOutgoing == false -> GiftServiceVariant.Received
    else -> GiftServiceVariant.Unspecified
}

/** Premium durations use months when Telegram sent them, otherwise days; a zero count is no duration. */
internal enum class ServiceDurationUnit { Months, Days, None }

internal fun serviceDurationUnit(monthCount: Int, dayCount: Int): ServiceDurationUnit = when {
    monthCount > 0 -> ServiceDurationUnit.Months
    dayCount > 0 -> ServiceDurationUnit.Days
    else -> ServiceDurationUnit.None
}

/** TON amounts arrive in nanotons; the line shows the exact decimal value without grouping. */
internal fun tonAmountText(nanoTon: Long): String =
    BigDecimal.valueOf(nanoTon).movePointLeft(9).stripTrailingZeros().toPlainString()

/** Duration of a call, independent of the device clock. */
internal fun callEventDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val secs = (total % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$secs" else "$minutes:$secs"
}
