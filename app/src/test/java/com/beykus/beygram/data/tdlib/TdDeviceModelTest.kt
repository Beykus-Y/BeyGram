package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdDeviceModelTest {

    @Test
    fun devVersionWithSamsungDevice() {
        assertEquals(
            "BG Android (0.0.6.1-dev) Samsung SM-M215F",
            TdDeviceModel.build("0.0.6.1-dev", "samsung", "SM-M215F")
        )
    }

    @Test
    fun stableVersionWithSamsungDevice() {
        assertEquals(
            "BG Android (0.0.6.1) Samsung SM-M215F",
            TdDeviceModel.build("0.0.6.1", "samsung", "SM-M215F")
        )
    }

    @Test
    fun manufacturerIsNotRepeatedWhenModelStartsWithIt() {
        assertEquals("Google Pixel 7", TdDeviceModel.device("Google", "Google Pixel 7"))
        assertEquals(
            "BG Android (0.0.6.1) Google Pixel 7",
            TdDeviceModel.build("0.0.6.1", "Google", "Google Pixel 7")
        )
    }

    @Test
    fun manufacturerIsNotRepeatedWhenModelStartsWithItIgnoringCase() {
        assertEquals("Xiaomi 13", TdDeviceModel.device("xiaomi", "Xiaomi 13"))
        assertEquals(
            "BG Android (0.0.6.1) Xiaomi 13",
            TdDeviceModel.build("0.0.6.1", "xiaomi", "Xiaomi 13")
        )
    }

    @Test
    fun manufacturerIsKeptWhenModelOnlyPrefixesItWithoutWordBoundary() {
        assertEquals("Sony SonyXperia1", TdDeviceModel.device("Sony", "SonyXperia1"))
        assertEquals(
            "BG Android (0.0.6.1) Sony SonyXperia1",
            TdDeviceModel.build("0.0.6.1", "Sony", "SonyXperia1")
        )
    }

    @Test
    fun extraWhitespaceIsTrimmedAndCollapsed() {
        assertEquals(
            "BG Android (0.0.6.1) Samsung SM-M215F",
            TdDeviceModel.build("0.0.6.1", "  samsung ", " SM-M215F  ")
        )
        assertEquals("Samsung Galaxy S23", TdDeviceModel.device("  Samsung   Galaxy  ", " S23  "))
    }

    @Test
    fun emptyOrNullModelLeavesOnlyManufacturer() {
        assertEquals(
            "BG Android (0.0.6.1) Samsung",
            TdDeviceModel.build("0.0.6.1", "samsung", "")
        )
        assertEquals(
            "BG Android (0.0.6.1) Samsung",
            TdDeviceModel.build("0.0.6.1", "samsung", null)
        )
    }

    @Test
    fun emptyOrUnknownDeviceLeavesOnlyHead() {
        assertEquals("BG Android (0.0.6.1)", TdDeviceModel.build("0.0.6.1", "", ""))
        assertEquals("BG Android (0.0.6.1)", TdDeviceModel.build("0.0.6.1", null, null))
        assertEquals("BG Android (0.0.6.1)", TdDeviceModel.build("0.0.6.1", "unknown", "UNKNOWN"))
    }

    @Test
    fun nullManufacturerLeavesOnlyModel() {
        assertEquals(
            "BG Android (0.0.6.1) SM-M215F",
            TdDeviceModel.build("0.0.6.1", null, "SM-M215F")
        )
    }

    @Test
    fun veryLongModelIsShortenedToMaxLength() {
        val model = "X".repeat(200)
        val result = TdDeviceModel.build("0.0.6.1-dev", "samsung", model)

        assertTrue(result.length <= TdDeviceModel.MAX_LENGTH)
        assertTrue(result.startsWith("BG Android (0.0.6.1-dev) "))
        // head "BG Android (0.0.6.1-dev)" is 24 chars, so the device gets 64 - 24 - 1 = 39 chars
        assertEquals(64, result.length)
        assertEquals("BG Android (0.0.6.1-dev) Samsung " + "X".repeat(31), result)
    }

    @Test
    fun veryLongVersionLeavesOnlyHead() {
        val version = "1".repeat(100)
        assertEquals("BG Android ($version)", TdDeviceModel.build(version, "samsung", "SM-M215F"))
    }

    @Test
    fun emptyVersionUsesBarePrefix() {
        assertEquals(
            "BG Android Samsung SM-M215F",
            TdDeviceModel.build("", "samsung", "SM-M215F")
        )
        assertEquals(
            "BG Android Samsung SM-M215F",
            TdDeviceModel.build("   ", "samsung", "SM-M215F")
        )
    }
}
