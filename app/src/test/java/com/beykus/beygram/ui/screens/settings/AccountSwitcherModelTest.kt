package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.data.tdlib.TdAccountAuthPhase
import com.beykus.beygram.data.tdlib.TdAccountSwitchFailure
import com.beykus.beygram.data.tdlib.TdSecondaryAccountRow
import com.beykus.beygram.data.tdlib.TdSecondaryAuthException
import com.beykus.beygram.data.tdlib.TdSecondaryRouteBlock
import com.beykus.beygram.domain.model.TelegramConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSwitcherModelTest {
    private fun row(
        id: String = "a-1",
        phase: TdAccountAuthPhase? = TdAccountAuthPhase.READY,
        connected: Boolean = true,
        authorized: Boolean = true,
        manualStop: Boolean = false,
        suspended: Boolean = false
    ) = TdSecondaryAccountRow(
        accountId = id,
        phase = phase,
        connectedInThisProcess = connected,
        networkSuspended = suspended,
        routePreference = null,
        authorized = authorized,
        manualStop = manualStop
    )

    private fun input(
        row: TdSecondaryAccountRow,
        current: String = "legacy",
        connection: TelegramConnectionState = TelegramConnectionState.Connected,
        meta: AccountSwitcherMeta? = AccountSwitcherMeta(
            identity = AccountSwitcherIdentity(
                title = "Alice",
                username = "alice",
                unread = 3,
                connection = connection
            )
        ),
        busy: String? = null,
        errors: Map<String, AccountSwitcherError> = emptyMap()
    ) = AccountSwitcherInput(
        currentAccountId = current,
        legacyReady = true,
        legacyMeta = null,
        accounts = listOf(row),
        metadata = if (meta == null) emptyMap() else mapOf(row.accountId to meta),
        busyAccountId = busy,
        errors = errors
    )

    @Test fun readyAccountExposesIdentityAndOneTapSelection() {
        val rows = accountSwitcherRows(input(row()))
        assertEquals(2, rows.size)
        assertEquals(AccountSwitcherStatus.CURRENT, rows[0].status)
        assertFalse(rows[0].canSelect)
        val account = rows[1]
        assertEquals(AccountSwitcherStatus.READY, account.status)
        assertEquals("Alice", account.title)
        assertEquals("alice", account.username)
        assertEquals(3, account.unread)
        assertTrue(account.canSelect)
        assertFalse(account.canSignIn)
    }

    @Test fun authorizedStoppedAccountReconnectsFromItsRow() {
        val account = accountSwitcherRows(input(row(connected = false, manualStop = true)))[1]
        assertEquals(AccountSwitcherStatus.DISCONNECTED, account.status)
        assertTrue(account.canSelect)
        assertTrue(account.canLogout)
        assertFalse(account.canDelete)
    }

    @Test fun authorizedButNotRunningIsShownAsAStoppedSession() {
        val account = accountSwitcherRows(input(row(connected = false)))[1]
        assertEquals(AccountSwitcherStatus.DISCONNECTED, account.status)
        assertTrue(account.canSelect)
    }

    @Test fun unfinishedSlotOffersContinueAndDeleteInsteadOfCheckmark() {
        val account = accountSwitcherRows(
            input(row(phase = null, connected = false, authorized = false))
        )[1]
        assertEquals(AccountSwitcherStatus.UNSIGNED, account.status)
        assertFalse(account.isCurrent)
        assertTrue(account.canSignIn)
        assertTrue(account.canDelete)
        assertFalse(account.canSelect)
        assertFalse(account.canLogout)
    }

    @Test fun waitingForCredentialsIsNotAReadyOrSelectedAccount() {
        listOf(
            TdAccountAuthPhase.WAIT_PHONE to AccountSwitcherStatus.AWAITING_PHONE,
            TdAccountAuthPhase.WAIT_CODE to AccountSwitcherStatus.AWAITING_CODE,
            TdAccountAuthPhase.WAIT_PASSWORD to AccountSwitcherStatus.AWAITING_PASSWORD
        ).forEach { (phase, expected) ->
            val account = accountSwitcherRows(
                input(row(phase = phase, connected = true, authorized = false))
            )[1]
            assertEquals(expected, account.status)
            assertTrue(account.canSignIn)
            assertTrue(account.canDisconnect)
            assertFalse(account.isCurrent)
            assertFalse(account.canSelect)
        }
    }

    @Test fun routeSuspensionNeverRendersLegacyDataUnderAnotherAccount() {
        val account = accountSwitcherRows(
            input(row(suspended = true))
        )[1]
        assertEquals(AccountSwitcherStatus.ROUTE_PAUSED, account.status)
        assertFalse(account.canSelect)
        assertTrue(account.canConfigureRoute)
        assertTrue(account.canDisconnect)
        assertFalse(account.canLogout)
    }

    @Test fun networkLossIsDistinctFromDisconnectedSession() {
        val account = accountSwitcherRows(
            input(row(), connection = TelegramConnectionState.WaitingForNetwork)
        )[1]
        assertEquals(AccountSwitcherStatus.OFFLINE, account.status)
        assertTrue(account.canSelect)
    }

    @Test fun busyAccountIgnoresDoubleTaps() {
        val account = accountSwitcherRows(
            input(row(), busy = "a-1")
        )[1]
        assertEquals(AccountSwitcherStatus.SWITCHING, account.status)
        assertFalse(account.canSelect)
        assertFalse(account.canSignIn)
        assertFalse(account.canDelete)
        assertFalse(account.canLogout)
    }

    @Test fun authorizedErrorOffersRetryWhileUnfinishedErrorContinuesSignIn() {
        val authorized = accountSwitcherRows(
            input(row(), errors = mapOf("a-1" to AccountSwitcherError("a-1", AccountSwitcherErrorReason.TIMEOUT)))
        )[1]
        assertEquals(AccountSwitcherStatus.ERROR, authorized.status)
        assertTrue(authorized.canSelect)
        assertFalse(authorized.canSignIn)

        val unfinished = accountSwitcherRows(
            input(
                row(phase = null, connected = false, authorized = false),
                errors = mapOf("a-1" to AccountSwitcherError("a-1", AccountSwitcherErrorReason.GENERIC))
            )
        )[1]
        assertEquals(AccountSwitcherStatus.ERROR, unfinished.status)
        assertTrue(unfinished.canSignIn)
        assertTrue(unfinished.canDelete)
    }

    @Test fun revokedAuthorizationStillOffersSignInAfterSelectionError() {
        val account = accountSwitcherRows(
            input(
                row(phase = TdAccountAuthPhase.WAIT_PHONE, connected = true, authorized = true),
                errors = mapOf("a-1" to AccountSwitcherError("a-1", AccountSwitcherErrorReason.NOT_AUTHORIZED))
            )
        )[1]
        assertEquals(AccountSwitcherStatus.AWAITING_PHONE, account.status)
        assertTrue(account.canSignIn)
        assertFalse(account.canSelect)
    }

    @Test fun unsupportedNativeMethodIsNotPresentedAsReady() {        val account = accountSwitcherRows(
            input(row(phase = TdAccountAuthPhase.OTHER, connected = true, authorized = false))
        )[1]
        assertEquals(AccountSwitcherStatus.UNSUPPORTED, account.status)
        assertFalse(account.canSelect)
    }

    @Test fun connectingSessionDoesNotOfferPrematureRouteChange() {
        val account = accountSwitcherRows(
            input(row(phase = TdAccountAuthPhase.STARTING, connected = true, authorized = false))
        )[1]
        assertEquals(AccountSwitcherStatus.CONNECTING, account.status)
        assertFalse(account.canSelect)
        assertFalse(account.canConfigureRoute)
    }

    @Test fun authStepTracksNativePhaseOnly() {
        assertEquals(AccountAuthStep.PREPARING, accountAuthStep(null, false))
        assertEquals(AccountAuthStep.PREPARING, accountAuthStep(TdAccountAuthPhase.STARTING, true))
        assertEquals(AccountAuthStep.PHONE, accountAuthStep(TdAccountAuthPhase.WAIT_PHONE, true))
        assertEquals(AccountAuthStep.CODE, accountAuthStep(TdAccountAuthPhase.WAIT_CODE, true))
        assertEquals(AccountAuthStep.PASSWORD, accountAuthStep(TdAccountAuthPhase.WAIT_PASSWORD, true))
        assertEquals(AccountAuthStep.READY, accountAuthStep(TdAccountAuthPhase.READY, true))
        assertEquals(AccountAuthStep.UNSUPPORTED, accountAuthStep(TdAccountAuthPhase.OTHER, true))
        assertEquals(AccountAuthStep.CLOSED, accountAuthStep(TdAccountAuthPhase.CLOSED, false))
    }

    @Test fun switchFailuresMapToActionableMessages() {
        assertEquals(
            AccountSwitcherErrorReason.ROUTE_UNAVAILABLE,
            accountSwitcherErrorReason(
                TdAccountSwitchFailure.RouteUnavailable(TdSecondaryRouteBlock.LISTENER_NOT_READY)
            )
        )
        assertEquals(
            AccountSwitcherErrorReason.SESSION_LIMIT,
            accountSwitcherErrorReason(TdAccountSwitchFailure.SessionLimit())
        )
        assertEquals(
            AccountSwitcherErrorReason.TIMEOUT,
            accountSwitcherErrorReason(TdAccountSwitchFailure.Timeout())
        )
        assertEquals(
            AccountSwitcherErrorReason.AUTH_INVALID_CODE,
            accountSwitcherErrorReason(TdSecondaryAuthException(400, "PHONE_CODE_INVALID"))
        )
        assertEquals(
            AccountSwitcherErrorReason.AUTH_CODE_EXPIRED,
            accountSwitcherErrorReason(TdSecondaryAuthException(400, "PHONE_CODE_EXPIRED"))
        )
        assertEquals(
            AccountSwitcherErrorReason.GENERIC,
            accountSwitcherErrorReason(IllegalStateException("boom"))
        )
    }

    @Test fun signedOutPrimaryCanStillOpenItsSignInFlow() {
        val input = AccountSwitcherInput(
            currentAccountId = "a-1",
            legacyReady = false,
            legacyMeta = null,
            accounts = emptyList(),
            metadata = emptyMap()
        )
        val primary = accountSwitcherRows(input).single()
        assertEquals(AccountSwitcherStatus.PRIMARY_SIGNED_OUT, primary.status)
        assertTrue(primary.canSelect)
    }
}
