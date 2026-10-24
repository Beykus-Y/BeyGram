package com.beykus.beygram.domain.model

/**
 * Telegram account privacy settings that BeyGram can edit. The list is ordered like the
 * "Privacy and security" screen: visibility first, then who may contact the account.
 * Rules live on Telegram's servers, never in local preferences, so they apply to every
 * device signed in to the same account.
 */
enum class PrivacySetting {
    PhoneNumber,
    ProfilePhoto,
    LastSeen,
    Bio,
    ForwardedMessages,
    Calls,
    Messages,
    GroupInvites,
    FindByPhoneNumber
}

/**
 * The three values offered by Telegram for a privacy setting. User-specific exceptions
 * are kept separately in [PrivacyRules].
 */
enum class PrivacyRuleChoice { Everyone, Contacts, Nobody }

/**
 * A normalized Telegram privacy rule list. [alwaysAllowUserIds] and [neverAllowUserIds]
 * are the user-specific exceptions shown by Telegram as "Always allow" and "Never allow".
 *
 * The server matches rules in order and the first match wins, so when this is converted
 * back to TDLib exceptions always precede the base rule.
 */
data class PrivacyRules(
    val choice: PrivacyRuleChoice,
    val alwaysAllowUserIds: List<Long> = emptyList(),
    val neverAllowUserIds: List<Long> = emptyList()
) {
    /**
     * Removes duplicate/invalid ids and exceptions that are already implied by the base
     * choice. A user listed in both exception lists is treated as never allowed: an
     * explicit denial must not be turned into an allowance while the screen is edited.
     */
    fun normalized(): PrivacyRules {
        val always = alwaysAllowUserIds.filter { it > 0L }.toSet()
        val never = neverAllowUserIds.filter { it > 0L }.toSet()
        return copy(
            alwaysAllowUserIds = if (choice == PrivacyRuleChoice.Everyone) {
                emptyList()
            } else {
                (always - never).sorted()
            },
            neverAllowUserIds = if (choice == PrivacyRuleChoice.Nobody) {
                emptyList()
            } else {
                never.sorted()
            }
        )
    }

    /** Changes the base audience while preserving exceptions that the new choice supports. */
    fun withChoice(choice: PrivacyRuleChoice): PrivacyRules = copy(choice = choice).normalized()
}

/** Current server rules plus resolved names for exception rows that BeyGram could load. */
data class PrivacySettingState(
    val setting: PrivacySetting,
    val rules: PrivacyRules,
    val exceptionNames: Map<Long, String> = emptyMap()
)

enum class BlockedSenderKind { User, Chat }

/**
 * A row in the Telegram block list. TDLib may return users and chats; the UI presents both
 * without pretending that a channel is a contact.
 */
data class BlockedSender(
    val senderId: Long,
    val kind: BlockedSenderKind,
    val title: String,
    val username: String = "",
    val photo: AvatarPhoto? = null,
    val avatarColorArgb: Long = 0L,
    val isBot: Boolean = false
) {
    val initials: String get() = initialsOf(title)
}

/** One page of TDLib's block list. [fetched] moves the server offset even if a row was skipped. */
data class BlockedSendersPage(
    val totalCount: Int,
    val senders: List<BlockedSender>,
    val fetched: Int
)
