package com.beykus.beygram.navigation

/**
 * Until every tab has a true account-scoped repository, never display legacy
 * chats, profiles, calls, searches or media while the secondary is selected.
 * The secondary Chats route is dispatched separately, not to legacy ChatsScreen.
 */
internal fun secondaryAccountMayOpen(route: AppRoute): Boolean =
    route == AppRoute.Chats ||
    route == AppRoute.AccountPicker ||
    route == AppRoute.SecondaryAccounts

/**
 * Legacy notifications and direct-share shortcuts still contain bare chat
 * IDs, so their producer is explicitly account "legacy". A future secondary
 * notification MUST carry its source account ID as part of the deep link.
 */
internal const val LEGACY_NOTIFICATION_SOURCE_ACCOUNT = "legacy"
