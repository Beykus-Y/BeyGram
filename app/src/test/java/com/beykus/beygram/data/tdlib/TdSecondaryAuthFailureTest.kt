package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Test

class TdSecondaryAuthFailureTest {
    @Test fun knownCredentialErrorsAreClassifiedWithoutLeakingSecrets() {
        assertEquals(TdSecondaryAuthFailure.INVALID_PHONE, tdSecondaryAuthFailure("PHONE_NUMBER_INVALID"))
        assertEquals(TdSecondaryAuthFailure.INVALID_CODE, tdSecondaryAuthFailure("PHONE_CODE_INVALID"))
        assertEquals(TdSecondaryAuthFailure.CODE_EXPIRED, tdSecondaryAuthFailure("PHONE_CODE_EXPIRED"))
        assertEquals(TdSecondaryAuthFailure.INVALID_PASSWORD, tdSecondaryAuthFailure("PASSWORD_HASH_INVALID"))
        assertEquals(TdSecondaryAuthFailure.FLOOD_WAIT, tdSecondaryAuthFailure("FLOOD_WAIT_42"))
    }

    @Test fun unknownOrModifiedMessagesAreNotClaimedAsWrongCredentials() {
        assertEquals(TdSecondaryAuthFailure.UNKNOWN, tdSecondaryAuthFailure(""))
        assertEquals(TdSecondaryAuthFailure.UNKNOWN, tdSecondaryAuthFailure("PHONE_NUMBER_BANNED"))
        assertEquals(TdSecondaryAuthFailure.UNKNOWN, tdSecondaryAuthFailure("Bad password attempt"))
    }
}
