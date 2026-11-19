package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.SessionDeviceKind
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdSessionMappingTest {
    @Test fun typedDeviceTypesMapToTheirIconKinds() {
        assertEquals(SessionDeviceKind.Android, sessionDeviceKindOf(TdApi.SessionDeviceTypeAndroid()))
        assertEquals(SessionDeviceKind.IPhone, sessionDeviceKindOf(TdApi.SessionDeviceTypeIphone()))
        assertEquals(SessionDeviceKind.IPad, sessionDeviceKindOf(TdApi.SessionDeviceTypeIpad()))
        assertEquals(SessionDeviceKind.Mac, sessionDeviceKindOf(TdApi.SessionDeviceTypeMac()))
        assertEquals(SessionDeviceKind.Apple, sessionDeviceKindOf(TdApi.SessionDeviceTypeApple()))
        assertEquals(SessionDeviceKind.Windows, sessionDeviceKindOf(TdApi.SessionDeviceTypeWindows()))
        assertEquals(SessionDeviceKind.Linux, sessionDeviceKindOf(TdApi.SessionDeviceTypeLinux()))
        assertEquals(SessionDeviceKind.Linux, sessionDeviceKindOf(TdApi.SessionDeviceTypeUbuntu()))
        assertEquals(SessionDeviceKind.Xbox, sessionDeviceKindOf(TdApi.SessionDeviceTypeXbox()))
        val browsers = listOf<TdApi.SessionDeviceType>(
            TdApi.SessionDeviceTypeBrave(),
            TdApi.SessionDeviceTypeChrome(),
            TdApi.SessionDeviceTypeEdge(),
            TdApi.SessionDeviceTypeFirefox(),
            TdApi.SessionDeviceTypeOpera(),
            TdApi.SessionDeviceTypeSafari(),
            TdApi.SessionDeviceTypeVivaldi()
        )
        browsers.forEach { assertEquals(SessionDeviceKind.Browser, sessionDeviceKindOf(it)) }
    }

    @Test fun missingOrUnknownTypeIsShownAsUnknown() {
        assertEquals(SessionDeviceKind.Unknown, sessionDeviceKindOf(null))
        assertEquals(SessionDeviceKind.Unknown, sessionDeviceKindOf(TdApi.SessionDeviceTypeUnknown()))
    }

    @Test fun deviceTypeDecidesTheKindNotTheModelName() {
        val mapped = deviceSessionOf(sessionOf(deviceType = TdApi.SessionDeviceTypeAndroid(), deviceModel = "iPhone 15"))
        assertEquals(SessionDeviceKind.Android, mapped.kind)
    }

    @Test fun sessionFieldsKeepTheirMeaning() {
        val mapped = deviceSessionOf(sessionOf(deviceType = TdApi.SessionDeviceTypeAndroid(), deviceModel = "Pixel 8"))
        assertEquals(7L, mapped.id)
        assertFalse(mapped.current)
        assertEquals("Pixel 8", mapped.device)
        assertEquals("Android", mapped.platform)
        assertEquals("14", mapped.systemVersion)
        assertEquals("Telegram Android", mapped.application)
        assertEquals("11.5", mapped.applicationVersion)
        assertTrue(mapped.officialApplication)
        assertEquals("Berlin, Germany", mapped.location)
        assertEquals("203.0.113.7", mapped.ip)
        assertEquals(1_700_000_000L, mapped.logInDate)
        assertEquals(1_700_086_400L, mapped.lastActive)
        assertTrue(mapped.unconfirmed)
        assertFalse(mapped.passwordPending)
        assertTrue(mapped.canAcceptSecretChats)
        assertFalse(mapped.canAcceptCalls)
    }

    @Test fun missingTextFromTdlibBecomesEmptyText() {
        val mapped = deviceSessionOf(
            TdApi.Session(3L, false, false, false, false, false, null, 0, null, null, false, null, null, null, 0, 0, null, null)
        )
        assertEquals(SessionDeviceKind.Unknown, mapped.kind)
        assertEquals("", mapped.device)
        assertEquals("", mapped.platform)
        assertEquals("", mapped.systemVersion)
        assertEquals("", mapped.application)
        assertEquals("", mapped.applicationVersion)
        assertEquals("", mapped.location)
        assertEquals("", mapped.ip)
        assertEquals(0L, mapped.logInDate)
        assertEquals(0L, mapped.lastActive)
    }

    @Test fun sessionsKeepTheServerOrderAndTheInactivityLimit() {
        val mapped = activeSessionsOf(TdApi.Sessions(arrayOf(sessionOf(id = 9L), sessionOf(id = 4L)), 30))
        assertEquals(listOf(9L, 4L), mapped.sessions.map { it.id })
        assertEquals(30, mapped.inactiveSessionTtlDays)
    }

    @Test fun missingSessionListIsAnEmptyList() {
        val mapped = activeSessionsOf(TdApi.Sessions(null, 365))
        assertTrue(mapped.sessions.isEmpty())
        assertEquals(365, mapped.inactiveSessionTtlDays)
    }

    private fun sessionOf(
        id: Long = 7L,
        deviceType: TdApi.SessionDeviceType? = TdApi.SessionDeviceTypeAndroid(),
        deviceModel: String = "Pixel 8"
    ): TdApi.Session = TdApi.Session(
        id, false, false, true, true, false,
        deviceType, 2040, "Telegram Android", "11.5", true, deviceModel, "Android", "14",
        1_700_000_000, 1_700_086_400, "203.0.113.7", "Berlin, Germany"
    )
}
