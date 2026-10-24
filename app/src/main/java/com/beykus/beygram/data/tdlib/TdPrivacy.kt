package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.BlockedSender
import com.beykus.beygram.domain.model.BlockedSenderKind
import com.beykus.beygram.domain.model.BlockedSendersPage
import com.beykus.beygram.domain.model.PrivacyRuleChoice
import com.beykus.beygram.domain.model.PrivacyRules
import com.beykus.beygram.domain.model.PrivacySetting
import com.beykus.beygram.domain.model.PrivacySettingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

internal const val BLOCKED_SENDERS_PAGE_SIZE = 50

/** Maps each editable setting to the TDLib privacy-setting object verified in app/libs/tdlib-java.jar. */
internal fun PrivacySetting.toTdSetting(): TdApi.UserPrivacySetting = when (this) {
    PrivacySetting.PhoneNumber -> TdApi.UserPrivacySettingShowPhoneNumber()
    PrivacySetting.ProfilePhoto -> TdApi.UserPrivacySettingShowProfilePhoto()
    PrivacySetting.LastSeen -> TdApi.UserPrivacySettingShowStatus()
    PrivacySetting.Bio -> TdApi.UserPrivacySettingShowBio()
    PrivacySetting.ForwardedMessages -> TdApi.UserPrivacySettingShowLinkInForwardedMessages()
    PrivacySetting.Calls -> TdApi.UserPrivacySettingAllowCalls()
    PrivacySetting.Messages -> TdApi.UserPrivacySettingAllowUnpaidMessages()
    PrivacySetting.GroupInvites -> TdApi.UserPrivacySettingAllowChatInvites()
    PrivacySetting.FindByPhoneNumber -> TdApi.UserPrivacySettingAllowFindingByPhoneNumber()
}

internal fun privacyRulesRequest(setting: PrivacySetting): TdApi.GetUserPrivacySettingRules =
    TdApi.GetUserPrivacySettingRules(setting.toTdSetting())

internal fun privacyRulesRequest(setting: PrivacySetting, rules: PrivacyRules): TdApi.SetUserPrivacySettingRules =
    TdApi.SetUserPrivacySettingRules(setting.toTdSetting(), rules.toTdRules())

/** TDLib rules are ordered: the first matching rule wins, so exceptions precede the base rule. */
internal fun PrivacyRules.toTdRules(): TdApi.UserPrivacySettingRules {
    val normalized = normalized()
    val rules = mutableListOf<TdApi.UserPrivacySettingRule>()
    if (normalized.alwaysAllowUserIds.isNotEmpty()) {
        rules += TdApi.UserPrivacySettingRuleAllowUsers(normalized.alwaysAllowUserIds.toLongArray())
    }
    if (normalized.neverAllowUserIds.isNotEmpty()) {
        rules += TdApi.UserPrivacySettingRuleRestrictUsers(normalized.neverAllowUserIds.toLongArray())
    }
    rules += when (normalized.choice) {
        PrivacyRuleChoice.Everyone -> TdApi.UserPrivacySettingRuleAllowAll()
        PrivacyRuleChoice.Contacts -> TdApi.UserPrivacySettingRuleAllowContacts()
        PrivacyRuleChoice.Nobody -> TdApi.UserPrivacySettingRuleRestrictAll()
    }
    return TdApi.UserPrivacySettingRules(rules.toTypedArray())
}

/**
 * Converts the server list to the three choices BeyGram can edit. Telegram may also return
 * rules that have no equivalent in this compact editor (premium users, chat members,
 * restricted contacts). The base is taken from the first supported base rule; user lists
 * are preserved as exceptions. With no supported base rule the empty list means "nobody",
 * matching TDLib's documented "no match, action is not allowed".
 */
internal fun TdApi.UserPrivacySettingRules.toPrivacyRules(): PrivacyRules {
    var choice: PrivacyRuleChoice? = null
    val always = linkedSetOf<Long>()
    val never = linkedSetOf<Long>()
    for (rule in rules.orEmpty()) {
        when (rule) {
            is TdApi.UserPrivacySettingRuleAllowAll -> if (choice == null) choice = PrivacyRuleChoice.Everyone
            is TdApi.UserPrivacySettingRuleAllowContacts -> if (choice == null) choice = PrivacyRuleChoice.Contacts
            is TdApi.UserPrivacySettingRuleRestrictAll -> if (choice == null) choice = PrivacyRuleChoice.Nobody
            // "Everyone except contacts" has no exact compact equivalent; showing Everybody
            // preserves the broadest part of the server rule instead of silently hiding it.
            is TdApi.UserPrivacySettingRuleRestrictContacts -> if (choice == null) choice = PrivacyRuleChoice.Everyone
            is TdApi.UserPrivacySettingRuleAllowUsers -> rule.userIds?.toList()?.let { always += it }
            is TdApi.UserPrivacySettingRuleRestrictUsers -> rule.userIds?.toList()?.let { never += it }
            else -> Unit
        }
    }
    val fallback = when {
        always.isNotEmpty() && never.isEmpty() -> PrivacyRuleChoice.Nobody
        never.isNotEmpty() && always.isEmpty() -> PrivacyRuleChoice.Everyone
        else -> PrivacyRuleChoice.Nobody
    }
    return PrivacyRules(choice ?: fallback, always.toList(), never.toList()).normalized()
}

internal fun blockedSendersRequest(offset: Int, limit: Int = BLOCKED_SENDERS_PAGE_SIZE): TdApi.GetBlockedMessageSenders {
    require(offset >= 0) { "Block list offset must not be negative" }
    require(limit > 0) { "Block list limit must be positive" }
    // This TDLib jar returns MessageSenders, not BlockedMessageSenders from older documentation.
    return TdApi.GetBlockedMessageSenders(TdApi.BlockListMain(), offset, limit)
}

internal fun messageSender(kind: BlockedSenderKind, senderId: Long): TdApi.MessageSender = when (kind) {
    BlockedSenderKind.User -> {
        require(senderId > 0L) { "User id must be positive" }
        TdApi.MessageSenderUser(senderId)
    }
    BlockedSenderKind.Chat -> {
        // Supergroup and channel chat ids are negative, so only zero is invalid here.
        require(senderId != 0L) { "Chat id must not be zero" }
        TdApi.MessageSenderChat(senderId)
    }
}

internal fun setMessageSenderBlockListRequest(
    kind: BlockedSenderKind,
    senderId: Long,
    blocked: Boolean
): TdApi.SetMessageSenderBlockList = TdApi.SetMessageSenderBlockList(
    messageSender(kind, senderId),
    if (blocked) TdApi.BlockListMain() else null
)

internal fun reportChatRequest(
    chatId: Long,
    optionId: ByteArray = ByteArray(0),
    text: String = ""
): TdApi.ReportChat {
    require(chatId != 0L) { "Chat id must not be zero" }
    return TdApi.ReportChat(chatId, optionId, longArrayOf(), text)
}

/** Picks Telegram's canonical "spam" option and never substitutes an unrelated report reason. */
internal fun chooseSpamReportOption(options: Array<TdApi.ReportOption>?): TdApi.ReportOption? {
    val available = options.orEmpty()
    return available.firstOrNull { option ->
        String(option.id ?: ByteArray(0), Charsets.UTF_8).contains("spam", ignoreCase = true)
    } ?: available.firstOrNull { option ->
        option.text.orEmpty().contains("spam", ignoreCase = true) ||
            option.text.orEmpty().contains("спам", ignoreCase = true)
    }
}

/**
 * Read/write TDLib privacy rules and the block list. One instance belongs to one account's
 * repository, so no local cache or preference can leak rules between accounts.
 */
internal class TdPrivacy(private val client: () -> Client, private val profiles: TdProfiles) {
    private val requests = TdRequests(client)

    suspend fun loadSettings(): List<PrivacySettingState> {
        val loaded = coroutineScope {
            PrivacySetting.entries.map { setting ->
                async { setting to requests(privacyRulesRequest(setting)).toPrivacyRules() }
            }.awaitAll()
        }
        val exceptionIds = loaded
            .flatMap { (_, rules) -> rules.alwaysAllowUserIds + rules.neverAllowUserIds }
            .distinct()
        val names = coroutineScope {
            exceptionIds.map { id -> async { id to userDisplayName(id) } }.awaitAll()
        }.mapNotNull { (id, name) -> name?.let { id to it } }.toMap()
        return loaded.map { (setting, rules) -> PrivacySettingState(setting, rules, names) }
    }

    suspend fun setSetting(setting: PrivacySetting, rules: PrivacyRules) {
        requests(privacyRulesRequest(setting, rules))
    }

    suspend fun loadBlockedSenders(offset: Int, limit: Int = BLOCKED_SENDERS_PAGE_SIZE): BlockedSendersPage {
        val page = requests(blockedSendersRequest(offset, limit))
        val raw = page.senders.orEmpty()
        // A block-list page can contain dozens of users; fetch their names concurrently so
        // one slow profile request does not serially delay the whole page.
        val senders = coroutineScope {
            raw.map { sender -> async { sender.toBlockedSender() } }.awaitAll()
        }.filterNotNull()
        return BlockedSendersPage(
            totalCount = page.totalCount,
            senders = senders,
            fetched = raw.size
        )
    }

    suspend fun setBlocked(kind: BlockedSenderKind, senderId: Long, blocked: Boolean) {
        requests(setMessageSenderBlockListRequest(kind, senderId, blocked))
    }

    private suspend fun TdApi.MessageSender.toBlockedSender(): BlockedSender? = when (this) {
        is TdApi.MessageSenderUser -> {
            val user = userOrNull(userId)
            BlockedSender(
                senderId = userId,
                kind = BlockedSenderKind.User,
                title = user?.let { userDisplayName(it) }.orEmpty(),
                username = user?.let { it.usernames?.editableUsername.orEmpty() }.orEmpty(),
                photo = user?.profilePhoto?.let { photo -> profiles.photo(photo.small, photo.big) },
                avatarColorArgb = userId.stableColor(),
                isBot = user?.type is TdApi.UserTypeBot
            )
        }
        is TdApi.MessageSenderChat -> {
            val chat = chatOrNull(chatId)
            BlockedSender(
                senderId = chatId,
                kind = BlockedSenderKind.Chat,
                title = chat?.title.orEmpty(),
                photo = chat?.let { profiles.photo(it.photo) },
                avatarColorArgb = chatId.stableColor()
            )
        }
        else -> null
    }

    private suspend fun userOrNull(id: Long): TdApi.User? = try {
        requests(TdApi.GetUser(id))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun chatOrNull(id: Long): TdApi.Chat? = try {
        requests(TdApi.GetChat(id))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun userDisplayName(id: Long): String? = userOrNull(id)?.let { userDisplayName(it) }

    private fun userDisplayName(user: TdApi.User): String? =
        listOf(user.firstName, user.lastName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
}
