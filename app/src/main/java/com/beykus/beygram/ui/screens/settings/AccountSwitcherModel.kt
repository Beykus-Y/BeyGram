package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.R
import com.beykus.beygram.data.tdlib.TdAccountAuthPhase
import com.beykus.beygram.data.tdlib.TdAccountLimitException
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.data.tdlib.TdAccountSwitchFailure
import com.beykus.beygram.data.tdlib.TdSecondaryAccountRow
import com.beykus.beygram.data.tdlib.TdSecondaryAuthException
import com.beykus.beygram.data.tdlib.TdSecondaryAuthFailure
import com.beykus.beygram.data.tdlib.TdSecondarySessionLimitException
import com.beykus.beygram.data.tdlib.tdSecondaryAuthFailure
import com.beykus.beygram.domain.model.AvatarPhoto
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.TelegramConnectionState
import kotlinx.coroutines.flow.StateFlow

/** Identity and unread badge of one account, read only from that account's repository. */
internal data class AccountSwitcherIdentity(
    val title: String = "",
    val username: String = "",
    val phone: String = "",
    val unread: Int = 0,
    val photo: AvatarPhoto? = null,
    val connection: TelegramConnectionState = TelegramConnectionState.Unknown
)

/**
 * UI-side metadata. File callbacks stay nullable so an unavailable account can
 * never accidentally render the primary account's avatar file.
 */
internal data class AccountSwitcherMeta(
    val identity: AccountSwitcherIdentity = AccountSwitcherIdentity(),
    val observeFile: ((Int) -> StateFlow<MediaFileState>)? = null,
    val downloadFile: ((Int) -> Unit)? = null
)

internal enum class AccountSwitcherStatus {
    CURRENT,
    READY,
    SWITCHING,
    CONNECTING,
    RESTORING,
    OFFLINE,
    ROUTE_PAUSED,
    AWAITING_PHONE,
    AWAITING_CODE,
    AWAITING_PASSWORD,
    DISCONNECTED,
    UNSIGNED,
    LOGGING_OUT,
    UNSUPPORTED,
    ERROR,
    PRIMARY_SIGNED_OUT
}

internal enum class AccountSwitcherErrorReason {
    ROUTE_UNAVAILABLE,
    ROUTE_PAUSED,
    SESSION_LIMIT,
    ACCOUNT_LIMIT,
    TIMEOUT,
    NOT_AUTHORIZED,
    AUTH_INVALID_PHONE,
    AUTH_INVALID_CODE,
    AUTH_CODE_EXPIRED,
    AUTH_INVALID_PASSWORD,
    AUTH_FLOOD_WAIT,
    AUTH_FAILED,
    GENERIC
}

internal data class AccountSwitcherError(
    val accountId: String?,
    val reason: AccountSwitcherErrorReason
)

internal data class AccountSwitcherRow(
    val accountId: String,
    val isPrimary: Boolean,
    /** 1 for the primary account, 2..N for secondary accounts. */
    val labelNumber: Int,
    val isCurrent: Boolean,
    val title: String?,
    val username: String,
    val phone: String,
    val unread: Int,
    val meta: AccountSwitcherMeta?,
    val status: AccountSwitcherStatus,
    val busy: Boolean,
    val error: AccountSwitcherError?,
    val canSelect: Boolean,
    val canSignIn: Boolean,
    val canDelete: Boolean,
    val canDisconnect: Boolean,
    val canLogout: Boolean,
    val canConfigureRoute: Boolean
)

internal data class AccountSwitcherInput(
    val currentAccountId: String,
    val legacyReady: Boolean,
    val legacyMeta: AccountSwitcherMeta?,
    val accounts: List<TdSecondaryAccountRow>,
    val metadata: Map<String, AccountSwitcherMeta>,
    val busyAccountId: String? = null,
    val errors: Map<String, AccountSwitcherError> = emptyMap()
)

/** Sign-in form step derived only from native TDLib state; never from a UI guess. */
internal enum class AccountAuthStep {
    PREPARING,
    PHONE,
    CODE,
    PASSWORD,
    READY,
    UNSUPPORTED,
    CLOSED
}

internal fun accountAuthStep(phase: TdAccountAuthPhase?, connected: Boolean): AccountAuthStep = when {
    phase == TdAccountAuthPhase.READY -> AccountAuthStep.READY
    phase == TdAccountAuthPhase.WAIT_PHONE -> AccountAuthStep.PHONE
    phase == TdAccountAuthPhase.WAIT_CODE -> AccountAuthStep.CODE
    phase == TdAccountAuthPhase.WAIT_PASSWORD -> AccountAuthStep.PASSWORD
    phase == TdAccountAuthPhase.OTHER -> AccountAuthStep.UNSUPPORTED
    phase == TdAccountAuthPhase.CLOSED -> AccountAuthStep.CLOSED
    connected -> AccountAuthStep.PREPARING
    else -> AccountAuthStep.PREPARING
}

internal fun accountSwitcherErrorReason(failure: Throwable): AccountSwitcherErrorReason = when (failure) {
    is TdAccountSwitchFailure.RouteUnavailable -> AccountSwitcherErrorReason.ROUTE_UNAVAILABLE
    is TdAccountSwitchFailure.RoutePaused -> AccountSwitcherErrorReason.ROUTE_PAUSED
    is TdAccountSwitchFailure.SessionLimit,
    is TdSecondarySessionLimitException -> AccountSwitcherErrorReason.SESSION_LIMIT
    is TdAccountSwitchFailure.AccountLimit,
    is TdAccountLimitException -> AccountSwitcherErrorReason.ACCOUNT_LIMIT
    is TdAccountSwitchFailure.Timeout -> AccountSwitcherErrorReason.TIMEOUT
    is TdAccountSwitchFailure.NotAuthorized -> AccountSwitcherErrorReason.NOT_AUTHORIZED
    is TdSecondaryAuthException -> when (tdSecondaryAuthFailure(failure.tdlibMessage)) {
        TdSecondaryAuthFailure.INVALID_PHONE -> AccountSwitcherErrorReason.AUTH_INVALID_PHONE
        TdSecondaryAuthFailure.INVALID_CODE -> AccountSwitcherErrorReason.AUTH_INVALID_CODE
        TdSecondaryAuthFailure.CODE_EXPIRED -> AccountSwitcherErrorReason.AUTH_CODE_EXPIRED
        TdSecondaryAuthFailure.INVALID_PASSWORD -> AccountSwitcherErrorReason.AUTH_INVALID_PASSWORD
        TdSecondaryAuthFailure.FLOOD_WAIT -> AccountSwitcherErrorReason.AUTH_FLOOD_WAIT
        TdSecondaryAuthFailure.UNKNOWN -> AccountSwitcherErrorReason.AUTH_FAILED
    }
    else -> AccountSwitcherErrorReason.GENERIC
}

/** Non-composable mapping for snackbars/notices outside the switcher sheet. */
internal fun accountSwitcherErrorResource(reason: AccountSwitcherErrorReason): Int = when (reason) {
    AccountSwitcherErrorReason.ROUTE_UNAVAILABLE -> R.string.account_switcher_error_route
    AccountSwitcherErrorReason.ROUTE_PAUSED -> R.string.account_switcher_error_route_paused
    AccountSwitcherErrorReason.SESSION_LIMIT -> R.string.account_switcher_error_session_limit
    AccountSwitcherErrorReason.ACCOUNT_LIMIT -> R.string.account_switcher_error_account_limit
    AccountSwitcherErrorReason.TIMEOUT -> R.string.account_switcher_error_timeout
    AccountSwitcherErrorReason.NOT_AUTHORIZED -> R.string.account_switcher_error_not_authorized
    AccountSwitcherErrorReason.AUTH_INVALID_PHONE -> R.string.account_switcher_error_auth_phone
    AccountSwitcherErrorReason.AUTH_INVALID_CODE -> R.string.account_switcher_error_auth_code
    AccountSwitcherErrorReason.AUTH_CODE_EXPIRED -> R.string.account_switcher_error_auth_code_expired
    AccountSwitcherErrorReason.AUTH_INVALID_PASSWORD -> R.string.account_switcher_error_auth_password
    AccountSwitcherErrorReason.AUTH_FLOOD_WAIT -> R.string.account_switcher_error_auth_flood
    AccountSwitcherErrorReason.AUTH_FAILED -> R.string.account_switcher_error_auth_failed
    AccountSwitcherErrorReason.GENERIC -> R.string.account_switcher_error_generic
}

/**
 * Builds the single account list used by every entry point. A READY secondary
 * may be selected while its network is reconnecting; a route-suspended one may
 * not, because its full repository is deliberately unavailable.
 */
internal fun accountSwitcherRows(input: AccountSwitcherInput): List<AccountSwitcherRow> {
    val primaryMeta = input.legacyMeta
    val primaryIdentity = primaryMeta?.identity ?: AccountSwitcherIdentity()
    val primaryIsCurrent = input.currentAccountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID
    val primary = AccountSwitcherRow(
        accountId = TdAccountStorageLayout.LEGACY_ACCOUNT_ID,
        isPrimary = true,
        labelNumber = 1,
        isCurrent = primaryIsCurrent,
        title = primaryIdentity.title.takeIf { it.isNotBlank() },
        username = primaryIdentity.username,
        phone = primaryIdentity.phone,
        unread = primaryIdentity.unread.coerceAtLeast(0),
        meta = primaryMeta,
        status = when {
            !input.legacyReady -> AccountSwitcherStatus.PRIMARY_SIGNED_OUT
            primaryIsCurrent -> AccountSwitcherStatus.CURRENT
            else -> AccountSwitcherStatus.READY
        },
        busy = false,
        error = null,
        canSelect = !primaryIsCurrent,
        canSignIn = false,
        canDelete = false,
        canDisconnect = false,
        canLogout = false,
        canConfigureRoute = false
    )
    val secondaries = input.accounts.mapIndexed { index, account ->
        val meta = input.metadata[account.accountId]
        val identity = meta?.identity ?: AccountSwitcherIdentity()
        val busy = input.busyAccountId == account.accountId
        val error = input.errors[account.accountId]
        val isCurrent = input.currentAccountId == account.accountId
        val status = secondaryStatus(account, identity.connection, isCurrent, busy, error)
        AccountSwitcherRow(
            accountId = account.accountId,
            isPrimary = false,
            labelNumber = index + 2,
            isCurrent = isCurrent,
            title = identity.title.takeIf { it.isNotBlank() },
            username = identity.username,
            phone = identity.phone,
            unread = identity.unread.coerceAtLeast(0),
            meta = meta,
            status = status,
            busy = busy,
            error = error,
            canSelect = !busy && when (status) {
                AccountSwitcherStatus.CURRENT,
                AccountSwitcherStatus.READY,
                AccountSwitcherStatus.OFFLINE,
                AccountSwitcherStatus.DISCONNECTED,
                AccountSwitcherStatus.RESTORING -> true
                AccountSwitcherStatus.ERROR -> account.authorized
                else -> false
            },
            canSignIn = !busy && when (status) {
                AccountSwitcherStatus.AWAITING_PHONE,
                AccountSwitcherStatus.AWAITING_CODE,
                AccountSwitcherStatus.AWAITING_PASSWORD,
                AccountSwitcherStatus.UNSIGNED -> true
                AccountSwitcherStatus.ERROR -> !account.authorized
                else -> false
            },
            canDelete = !busy && !account.authorized,
            canDisconnect = !busy && account.connectedInThisProcess &&
                account.phase == TdAccountAuthPhase.READY && !account.networkSuspended,
            canLogout = !busy && account.authorized,
            canConfigureRoute = !busy && when (status) {
                AccountSwitcherStatus.CONNECTING,
                AccountSwitcherStatus.UNSUPPORTED,
                AccountSwitcherStatus.LOGGING_OUT,
                AccountSwitcherStatus.SWITCHING -> false
                else -> true
            }
        )
    }
    return listOf(primary) + secondaries
}

private fun secondaryStatus(
    account: TdSecondaryAccountRow,
    connection: TelegramConnectionState,
    isCurrent: Boolean,
    busy: Boolean,
    error: AccountSwitcherError?
): AccountSwitcherStatus {
    val phase = account.phase
    return when {
        phase == TdAccountAuthPhase.LOGGING_OUT -> AccountSwitcherStatus.LOGGING_OUT
        busy -> AccountSwitcherStatus.SWITCHING
        // Native credential prompts outrank a previous selection error: an
        // account whose authorization was revoked must still offer sign-in.
        phase == TdAccountAuthPhase.WAIT_PHONE -> AccountSwitcherStatus.AWAITING_PHONE
        phase == TdAccountAuthPhase.WAIT_CODE -> AccountSwitcherStatus.AWAITING_CODE
        phase == TdAccountAuthPhase.WAIT_PASSWORD -> AccountSwitcherStatus.AWAITING_PASSWORD
        error != null -> AccountSwitcherStatus.ERROR
        account.connectedInThisProcess && phase == TdAccountAuthPhase.READY -> when {
            account.networkSuspended -> AccountSwitcherStatus.ROUTE_PAUSED
            connection == TelegramConnectionState.WaitingForNetwork -> AccountSwitcherStatus.OFFLINE
            connection == TelegramConnectionState.Connecting ||
                connection == TelegramConnectionState.Updating ||
                connection == TelegramConnectionState.Unknown -> AccountSwitcherStatus.CONNECTING
            isCurrent -> AccountSwitcherStatus.CURRENT
            else -> AccountSwitcherStatus.READY
        }
        account.connectedInThisProcess && phase == TdAccountAuthPhase.OTHER ->
            AccountSwitcherStatus.UNSUPPORTED
        account.connectedInThisProcess -> AccountSwitcherStatus.CONNECTING
        account.authorized && account.manualStop -> AccountSwitcherStatus.DISCONNECTED
        account.authorized -> AccountSwitcherStatus.RESTORING
        else -> AccountSwitcherStatus.UNSIGNED
    }
}
