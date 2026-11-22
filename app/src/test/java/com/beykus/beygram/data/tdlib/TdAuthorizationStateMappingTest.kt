package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.AuthState
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TdAuthorizationStateMappingTest {
    @Test
    fun qrAuthorizationStateIsMappedToTheUiWithoutItsSecret() {
        val token = "secret_QR_TOKEN-123"
        val link = "tg://login?token=$token"
        val state = TdApi.AuthorizationStateWaitOtherDeviceConfirmation(link)

        assertEquals("wait_other_device", state.authWireState())
        assertEquals("ожидание подтверждения", state.authLogLabel())
        assertFalse(state.authWireState().contains(token))
        assertFalse(state.authLogLabel().contains(token))
    }

    @Test
    fun qrLinkIsValidatedBeforeItReachesTheScreen() {
        val link = "tg://login?token=valid_TOKEN-123"
        assertEquals(AuthState.WaitingForQr(link), qrAuthState(link))
        assertEquals(
            AuthState.Error(UiText.Res(R.string.err_auth_qr_link)),
            qrAuthState("https://example.com/?token=valid_TOKEN-123")
        )
        assertEquals(
            AuthState.Error(UiText.Res(R.string.err_auth_qr_link)),
            qrAuthState(null)
        )
    }

    @Test
    fun otherAuthorizationStatesKeepTheirExistingLabels() {
        assertEquals("wait_phone", TdApi.AuthorizationStateWaitPhoneNumber().authWireState())
        assertEquals("wait_code", TdApi.AuthorizationStateWaitCode().authWireState())
        assertEquals("ready", TdApi.AuthorizationStateReady().authWireState())
        assertEquals("closed", TdApi.AuthorizationStateClosed().authWireState())
    }

    @Test
    fun phoneSubmitRouteFollowsTheTdlibPhase() {
        assertEquals(PhoneSubmitRoute.SEND, phoneSubmitRoute("wait_phone", qrActive = false, reopening = false))
        assertEquals(PhoneSubmitRoute.SEND, phoneSubmitRoute("wait_code", qrActive = false, reopening = false))
        assertEquals(
            PhoneSubmitRoute.CANCEL_QR_THEN_SEND,
            phoneSubmitRoute("wait_other_device", qrActive = false, reopening = false)
        )
        assertEquals(PhoneSubmitRoute.CANCEL_QR_THEN_SEND, phoneSubmitRoute("wait_phone", qrActive = true, reopening = false))
        assertEquals(PhoneSubmitRoute.WAIT_FOR_CLIENT, phoneSubmitRoute("wait_phone", qrActive = false, reopening = true))
        assertEquals(PhoneSubmitRoute.WAIT_FOR_CLIENT, phoneSubmitRoute("closed", qrActive = false, reopening = true))
        assertEquals(PhoneSubmitRoute.WAIT_FOR_CLIENT, phoneSubmitRoute(null, qrActive = false, reopening = false))
        assertEquals(PhoneSubmitRoute.WAIT_FOR_CLIENT, phoneSubmitRoute("closed", qrActive = false, reopening = false))
        assertEquals(
            PhoneSubmitRoute.WAIT_FOR_CLIENT,
            phoneSubmitRoute("wait_parameters", qrActive = false, reopening = false)
        )
        assertEquals(PhoneSubmitRoute.IGNORE, phoneSubmitRoute("ready", qrActive = false, reopening = false))
        assertEquals(PhoneSubmitRoute.IGNORE, phoneSubmitRoute("ready", qrActive = false, reopening = true))
    }

    @Test
    fun startAuthorizationNeverOverwritesARealPhoneStateWithASpinner() {
        // Regression: TDLib already sent WaitPhoneNumber, so no new update would ever clear a Loading spinner.
        assertNull(startAuthorizationState(AuthState.WaitingForPhone, "wait_phone"))
        assertEquals(AuthState.WaitingForPhone, startAuthorizationState(AuthState.Idle, "wait_phone"))
        assertEquals(AuthState.Loading, startAuthorizationState(AuthState.Idle, null))
        assertNull(startAuthorizationState(AuthState.WaitingForQr("tg://login?token=x"), "wait_other_device"))
        assertNull(startAuthorizationState(AuthState.Ready, "ready"))
    }

    @Test
    fun authErrorReasonIsAStableKeyWithoutPayload() {
        assertEquals("phone_invalid", authErrorReason("PHONE_NUMBER_INVALID"))
        assertEquals("flood_wait", authErrorReason("FLOOD_WAIT_30"))
        assertEquals("unexpected_state", authErrorReason("Unexpected setAuthenticationPhoneNumber"))
        assertEquals("other", authErrorReason(null))

        val reason = authErrorReason("+79991234567 PHONE_NUMBER_INVALID")
        assertEquals("phone_invalid", reason)
        assertFalse(reason.contains("7999"))
        assertTrue(reason.none { it.isDigit() })
    }
}
