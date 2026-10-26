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

    @Test fun pluralFormEnglishUsesOneOnlyForOne() {
        assertEquals("one", pluralForm("en", 1, "one", "few", "many"))
        assertEquals("many", pluralForm("en", 2, "one", "few", "many"))
        assertEquals("many", pluralForm("en", 21, "one", "few", "many"))
    }

    @Test fun pluralFormGermanUsesOneOnlyForOne() {
        assertEquals("many", pluralForm("de", 21, "one", "few", "many"))
    }

    @Test fun pluralFormFrenchTreatsZeroAsOne() {
        assertEquals("one", pluralForm("fr", 0, "one", "few", "many"))
        assertEquals("one", pluralForm("fr", 1, "one", "few", "many"))
        assertEquals("many", pluralForm("fr", 2, "one", "few", "many"))
    }

    @Test fun pluralFormRussianUsesRussianRules() {
        assertEquals("one", pluralForm("ru", 21, "one", "few", "many"))
        assertEquals("few", pluralForm("ru", 22, "one", "few", "many"))
        assertEquals("many", pluralForm("ru", 25, "one", "few", "many"))
    }

    @Test fun pluralFormUnsupportedLanguageFallsBackToRussianStrings() {
        assertEquals("few", pluralForm("ja", 2, "one", "few", "many"))
        assertEquals("one", pluralForm("ja", 21, "one", "few", "many"))
    }
}
