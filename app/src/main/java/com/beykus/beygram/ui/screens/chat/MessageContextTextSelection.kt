package com.beykus.beygram.ui.screens.chat

import android.icu.text.BreakIterator
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit

internal data class SelectedMessageQuote(val text: String, val position: Int)

/** TDLib positions are UTF-16 code units. Do not reinterpret a quote by searching for its text. */
internal fun selectedMessageQuote(source: String, range: TextRange): SelectedMessageQuote? {
    val start = range.min
    val end = range.max
    if (start < 0 || end > source.length || start >= end || end - start > 1024) return null
    val graphemes = BreakIterator.getCharacterInstance().apply { setText(source) }
    if (!graphemes.isBoundary(start) || !graphemes.isBoundary(end)) return null
    val text = source.substring(start, end)
    return text.takeUnless(String::isBlank)?.let { SelectedMessageQuote(it, start) }
}

@Stable
internal class MessageContextTextSelection(val source: String) {
    var value by mutableStateOf(TextFieldValue(source))
        private set

    val selectedText: String? get() = value.selection.takeUnless { it.collapsed }
        ?.let { source.substring(it.min, it.max) }

    fun update(next: TextFieldValue) {
        if (next.text == source) value = next
    }
    fun selectAll() { value = value.copy(selection = TextRange(0, source.length)) }
    fun clear() { value = value.copy(selection = TextRange(value.selection.end)) }
}

/** The context scene draws its own actions while the read-only field provides native selection handles. */
private object SceneSelectionMenuProvider : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) = Unit
}

@Composable
internal fun ContextSelectableMessageText(
    selection: MessageContextTextSelection,
    color: Color,
    size: TextUnit,
    lineHeight: TextUnit,
    onTextLayout: (TextLayoutResult) -> Unit
) {
    CompositionLocalProvider(
        LocalTextContextMenuToolbarProvider provides SceneSelectionMenuProvider,
        LocalTextContextMenuDropdownProvider provides SceneSelectionMenuProvider
    ) {
        BasicTextField(
            value = selection.value,
            onValueChange = selection::update,
            readOnly = true,
            textStyle = TextStyle(color = color, fontSize = size, fontFamily = FontFamily.Default, lineHeight = lineHeight),
            onTextLayout = onTextLayout
        )
    }
}
