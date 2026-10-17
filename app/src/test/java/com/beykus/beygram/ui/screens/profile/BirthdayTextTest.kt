package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.ProfileBirthday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class BirthdayTextTest {
    private val russian = Locale.forLanguageTag("ru")

    @Test fun showsYearWhenAccountHasOne() {
        assertEquals("17 октября 1990", birthdayText(ProfileBirthday(17, 10, 1990), russian))
    }

    @Test fun omitsYearWhenAccountHidesIt() {
        assertEquals("5 марта", birthdayText(ProfileBirthday(5, 3, null), russian))
    }

    @Test fun impossibleDateIsNotShown() {
        assertNull(birthdayText(ProfileBirthday(31, 2, 1990), russian))
        assertNull(birthdayText(ProfileBirthday(31, 2, null), russian))
    }
}
