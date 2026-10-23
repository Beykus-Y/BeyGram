package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdSecondaryRestoreStoreTest {
    @Test fun storedRouteWithoutNativeReadyNeverStartsClient() {
        for (name in TdSecondaryRoutePreference.entries.map { it.name }) {
            assertNull(tdParseAuthorizedSecondaryRoute(false, name))
        }
        assertNull(tdParseAuthorizedSecondaryRoute(true, null))
    }

    @Test fun invalidOrModifiedRouteFailsClosed() {
        assertNull(tdParseAuthorizedSecondaryRoute(true, ""))
        assertNull(tdParseAuthorizedSecondaryRoute(true, "DIRECT"))
        assertNull(tdParseAuthorizedSecondaryRoute(true, "EXPLICIT_DIRECT!"))
        assertNull(tdParseAuthorizedSecondaryRoute(true, "MTPROTO"))
    }

    @Test fun onlyPreviouslyConfirmedExactRouteCanRestore() {
        TdSecondaryRoutePreference.entries.forEach { route ->
            assertEquals(route, tdParseAuthorizedSecondaryRoute(true, route.name))
        }
    }
}
