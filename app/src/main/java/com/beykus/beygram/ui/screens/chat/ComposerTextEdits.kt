package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.text.TextRange
import java.text.BreakIterator

/** Result of an edit made outside the text field: the new text and where the cursor goes. */
internal data class ComposerTextEdit(val text: String, val selection: TextRange)

/**
 * Shares the composer field's selection with the emoji panel, which edits the text from outside the field. It is a
 * plain holder, not Compose state: cursor moves must not recompose the chat screen.
 */
internal class ComposerCursor {
    private var knownText = ""
    private var knownSelection = TextRange.Zero

    /** Called by the field on every value change, including cursor moves. */
    fun update(text: String, selection: TextRange) {
        knownText = text
        knownSelection = selection
    }

    /** The selection for [text]; the end of the text when the field has not reported this text yet. */
    fun selectionFor(text: String): TextRange =
        if (text == knownText) clampSelection(knownSelection, text.length) else TextRange(text.length)
}

private fun clampSelection(selection: TextRange, length: Int): TextRange =
    TextRange(selection.start.coerceIn(0, length), selection.end.coerceIn(0, length))

/** Inserts [insert] at the cursor, replacing the selected text if there is any. */
internal fun insertAtSelection(text: String, selection: TextRange, insert: String): ComposerTextEdit {
    val range = clampSelection(selection, text.length)
    val next = text.substring(0, range.min) + insert + text.substring(range.max)
    return ComposerTextEdit(next, TextRange(range.min + insert.length))
}

/**
 * Backspace at the cursor: removes the selected text, or the user-perceived character before the cursor. BreakIterator
 * keeps a surrogate pair, a combining mark or a ZWJ emoji sequence together, so one press removes one visible symbol.
 */
internal fun deleteBeforeSelection(text: String, selection: TextRange): ComposerTextEdit {
    val range = clampSelection(selection, text.length)
    if (!range.collapsed) return ComposerTextEdit(text.substring(0, range.min) + text.substring(range.max), TextRange(range.min))
    if (range.min == 0) return ComposerTextEdit(text, TextRange.Zero)
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(text)
    val start = boundaries.preceding(range.min).coerceAtLeast(0)
    return ComposerTextEdit(text.substring(0, start) + text.substring(range.min), TextRange(start))
}
