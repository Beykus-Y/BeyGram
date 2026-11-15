package com.beykus.beygram.data.yandex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexAccountParserTest {

    private fun body(json: String) = JSONObject(json)

    @Test
    fun nestedPlusFlagIsRead() {
        val account = parseAccountStatus(
            body("""{"result":{"account":{"uid":12345,"login":"someone","displayName":"Name"},"plus":{"has_plus":true}}}"""),
        )!!
        assertEquals("12345", account.uid)
        assertEquals("Name", account.displayName)
        assertTrue(account.hasPlus)
    }

    @Test
    fun subscriptionObjectFlagIsRead() {
        val account = parseAccountStatus(
            body("""{"result":{"account":{"uid":1},"subscription":{"hasPlus":true}}}"""),
        )!!
        assertTrue(account.hasPlus)
    }

    @Test
    fun missingFlagMeansNoSubscription() {
        val account = parseAccountStatus(body("""{"result":{"account":{"uid":1,"login":"x"},"plus":{"has_plus":false}}}"""))!!
        assertFalse(account.hasPlus)
    }

    @Test
    fun summaryHasKeyNamesAndScalarsOnly() {
        val account = parseAccountStatus(
            body("""{"result":{"account":{"uid":1},"plus":{"has_plus":true,"until":1700000000,"name":"secret"}}}"""),
        )!!
        assertTrue(account.plusSummary.contains("plus.has_plus=true"))
        assertTrue(account.plusSummary.contains("plus.until=1700000000"))
        assertTrue(account.plusSummary.contains("plus.name=string"))
        assertFalse(account.plusSummary.contains("secret"))
    }

    @Test
    fun missingAccountIsNull() {
        assertNull(parseAccountStatus(body("""{"result":{"plus":{"has_plus":true}}}""")))
    }
}
