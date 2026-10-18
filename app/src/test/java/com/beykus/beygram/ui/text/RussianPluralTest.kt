package com.beykus.beygram.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianPluralTest {
    private fun form(count: Int) = russianPlural(count, "один", "несколько", "много")

    @Test fun lastDigitOneTakesTheFirstForm() {
        assertEquals("один", form(1))
        assertEquals("один", form(21))
        assertEquals("один", form(101))
    }

    @Test fun twoToFourTakeTheSecondForm() {
        assertEquals("несколько", form(2))
        assertEquals("несколько", form(3))
        assertEquals("несколько", form(24))
    }

    @Test fun zeroTeensAndOtherNumbersTakeTheThirdForm() {
        assertEquals("много", form(0))
        assertEquals("много", form(5))
        assertEquals("много", form(11))
        assertEquals("много", form(14))
        assertEquals("много", form(111))
    }
}
