package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramFilledTextFieldColors

@Composable
internal fun FormattingTextField(value: String, entities: List<MessageTextEntity>, onChange: (String) -> Unit,
    onEntitiesChange: (List<MessageTextEntity>) -> Unit, placeholder: String, locked: Boolean, isError: Boolean, modifier: Modifier,
    focusRequest: Int = 0, cursor: ComposerCursor? = null,
    trailingAction: (@Composable () -> Unit)? = null) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    // Text set from outside keeps the cursor an outside edit chose (see [ComposerCursor]); otherwise it goes to the end.
    val current = if (field.text == value) field else TextFieldValue(value, cursor?.selectionFor(value) ?: TextRange(value.length))
    var format by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(TextRange.Zero) }
    val palette = beyGramPalette()
    val formatLabel = stringResource(R.string.chat_format)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Each reply start bumps [focusRequest]. The value seen on entry is not a request, so a field that comes back
    // after a recording does not take focus on its own.
    var handledFocus by remember { mutableIntStateOf(focusRequest) }
    LaunchedEffect(focusRequest) {
        if (focusRequest == handledFocus) return@LaunchedEffect
        handledFocus = focusRequest
        focusRequester.requestFocus()
        keyboard?.show()
    }
    SelectionMenuProvider(modifier) {
        TextField(current, { next ->
            // Sending locks the text, not the field: a disabled field loses focus and hides the keyboard.
            if (locked) return@TextField
            // Moving the cursor or the selection also fires this callback; only a real text change is a change.
            if (next.text != current.text) {
                onEntitiesChange(adjustTextEntities(value, next.text, entities))
                onChange(next.text)
            }
            field = next
            cursor?.update(next.text, next.selection)
        }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).focusRequester(focusRequester).appendTextContextMenuComponents {
                if (!current.selection.collapsed) item(FormatSelectionKey, formatLabel) {
                    range = current.selection
                    close()
                    format = true
                }
            }, isError = isError, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            placeholder = { Text(placeholder, color = palette.textSecondary) },
            trailingIcon = trailingAction, maxLines = 5,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), colors = beyGramFilledTextFieldColors(),
            visualTransformation = VisualTransformation { text ->
                TransformedText(buildAnnotatedString {
                    append(text)
                    validTextEntities(text.text, entities).forEach { addStyle(entitySpan(it.kind, palette.accentInk, palette.textSecondary), it.offset, it.offset + it.length) }
                }, OffsetMapping.Identity)
            })
    }
    if (format) AlertDialog(onDismissRequest = { format = false }, title = { Text(stringResource(R.string.chat_format)) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            listOf(TextStyleKind.Bold to R.string.chat_format_bold, TextStyleKind.Italic to R.string.chat_format_italic,
                TextStyleKind.Underline to R.string.chat_format_underline, TextStyleKind.Strike to R.string.chat_format_strike,
                TextStyleKind.Code to R.string.chat_format_code, TextStyleKind.Pre to R.string.chat_format_pre,
                TextStyleKind.Quote to R.string.chat_format_quote, TextStyleKind.Spoiler to R.string.chat_format_spoiler).forEach { (kind, label) ->
                TextButton(onClick = {
                    onEntitiesChange(applyTextFormat(value, entities, range.min, range.max, kind))
                    format = false
                }) { Text(stringResource(label)) }
            }
            TextButton(onClick = { format = false; link = "https://"; linkDialog = true }) { Text(stringResource(R.string.chat_format_link)) }
            TextButton(onClick = { onEntitiesChange(applyTextFormat(value, entities, range.min, range.max, null)); format = false }) { Text(stringResource(R.string.chat_format_clear)) }
        }
    }, confirmButton = { TextButton(onClick = { format = false }) { Text(stringResource(R.string.common_close)) } })
    if (linkDialog) AlertDialog(onDismissRequest = { linkDialog = false }, title = { Text(stringResource(R.string.chat_format_link)) },
        text = { TextField(link, { link = it }, singleLine = true, label = { Text(stringResource(R.string.chat_format_link_address)) }) },
        confirmButton = { TextButton(enabled = safeMessageUrl(link)?.let { it != "https://" } == true, onClick = {
            val target = safeMessageUrl(link) ?: return@TextButton
            onEntitiesChange(applyTextFormat(value, entities, range.min, range.max, TextStyleKind.Url).map {
                if (it.kind == TextStyleKind.Url && it.offset == range.min && it.length == range.max - range.min) it.copy(argument = target) else it
            })
            linkDialog = false
        }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = { linkDialog = false }) { Text(stringResource(R.string.common_close)) } })
}
