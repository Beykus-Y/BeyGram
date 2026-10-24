package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.AuthState
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
