package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerTextEditsTest {
    @Test
    fun emojiGoesToTheCursorAndTheCursorMovesPastIt() {
        val edit = insertAtSelection("hello world", TextRange(5), "😀")
        assertEquals("hello😀 world", edit.text)
        assertEquals(TextRange(5 + "😀".length), edit.selection)
    }

    @Test
    fun emojiReplacesTheSelection() {
        val edit = insertAtSelection("hello world", TextRange(6, 11), "👍")
        assertEquals("hello 👍", edit.text)
    }

    @Test
    fun backspaceRemovesOneWholeEmojiBeforeTheCursor() {
        val family = "👨‍👩‍👧"
        val text = "a${family}b"
        val edit = deleteBeforeSelection(text, TextRange(1 + family.length))
        assertEquals("ab", edit.text)
        assertEquals(TextRange(1), edit.selection)
    }

    @Test
    fun backspaceRemovesTheSelectionAndDoesNothingAtTheStart() {
        assertEquals("ad", deleteBeforeSelection("abcd", TextRange(1, 3)).text)
        assertEquals("abcd", deleteBeforeSelection("abcd", TextRange(0)).text)
    }

    @Test
    fun cursorFallsBackToTheEndForTextTheFieldHasNotSeen() {
        val cursor = ComposerCursor()
        cursor.update("abc", TextRange(1))
        assertEquals(TextRange(1), cursor.selectionFor("abc"))
        assertEquals(TextRange(4), cursor.selectionFor("abcd"))
    }
}
