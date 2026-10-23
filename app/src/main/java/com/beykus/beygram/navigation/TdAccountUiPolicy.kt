package com.beykus.beygram.navigation

/**
 * All normal BeyGram routes use the selected account's complete repository.
 * Do not solve data isolation by disabling screens or reimplementing a
 * second ChatScreen: the repository identity is the security boundary.
 */
internal fun accountRepositoryMatchesSelection(
    selectedAccountId: String,
    resolvedRepositoryAccountId: String?
): Boolean = selectedAccountId.isNotBlank() &&
    resolvedRepositoryAccountId == selectedAccountId

/**
 * Account management works while a selected TDLib session is offline. Normal
 * chat/profile/call/media routes require an actual bound account repository.
 */
internal fun accountRouteCanRender(
    route: AppRoute,
    selectedAccountId: String,
    resolvedRepositoryAccountId: String?
): Boolean = route == AppRoute.AccountPicker ||
    route == AppRoute.SecondaryAccounts ||
    accountRepositoryMatchesSelection(selectedAccountId, resolvedRepositoryAccountId)

/** Legacy shortcuts currently carry unscoped chat IDs; their source never changes. */
internal const val LEGACY_NOTIFICATION_SOURCE_ACCOUNT = "legacy"
