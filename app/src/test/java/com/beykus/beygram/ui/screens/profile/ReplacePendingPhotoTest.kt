package com.beykus.beygram.ui.screens.profile

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacePendingPhotoTest {
    @Test fun newSelectionDiscardsPreviousStagedPhoto() = runBlocking {
        val discarded = mutableListOf<String>()
        val next = replacePendingPhoto("old", { "new" }, { discarded += it })
        assertEquals("new", next)
        assertEquals(listOf("old"), discarded)
    }

    @Test fun firstSelectionDiscardsNothing() = runBlocking {
        val discarded = mutableListOf<String>()
        assertEquals("first", replacePendingPhoto(null, { "first" }, { discarded += it }))
        assertTrue(discarded.isEmpty())
    }

    @Test fun failedPreparationKeepsPreviousStagedPhoto() = runBlocking {
        val discarded = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { replacePendingPhoto<String>("old", { error("decode failed") }, { discarded += it }) }
        }
        assertTrue(discarded.isEmpty())
    }
}
