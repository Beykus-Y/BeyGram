package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramFilledTextFieldColors
import kotlin.math.roundToInt

@Composable
internal fun FormattingTextField(value: String, entities: List<MessageTextEntity>, onChange: (String) -> Unit,
    onEntitiesChange: (List<MessageTextEntity>) -> Unit, placeholder: String, enabled: Boolean, isError: Boolean, modifier: Modifier) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    val current = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    var menu by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var rect by remember { mutableStateOf(Rect.Zero) }
    var copy by remember { mutableStateOf<(() -> Unit)?>(null) }
    var paste by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cut by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectAll by remember { mutableStateOf<(() -> Unit)?>(null) }
    var range by remember { mutableStateOf(TextRange.Zero) }
    val palette = beyGramPalette()
    val toolbar = remember { object : TextToolbar {
        override val status get() = if (menu) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
        override fun hide() { menu = false }
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
            // Copy coordinates before publishing the menu so recomposition sees one consistent snapshot.
            copy = onCopyRequested; paste = onPasteRequested; cut = onCutRequested; selectAll = onSelectAllRequested
            menuRect = rect
            menu = true
        }
        private var menuRect: Rect
            get() = rect
            set(value) { rect = value }
    } }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        TextField(current, { next ->
            onEntitiesChange(adjustTextEntities(value, next.text, entities))
            field = next
            onChange(next.text)
        }, modifier = modifier.defaultMinSize(minHeight = 40.dp), enabled = enabled, isError = isError,
            placeholder = { Text(placeholder, color = palette.textSecondary) }, maxLines = 5,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), colors = beyGramFilledTextFieldColors(),
            visualTransformation = VisualTransformation { text ->
                TransformedText(buildAnnotatedString {
                    append(text)
                    validTextEntities(text.text, entities).forEach { addStyle(entitySpan(it.kind, palette.accentInk, palette.textSecondary), it.offset, it.offset + it.length) }
                }, OffsetMapping.Identity)
            })
    }
    if (menu) Popup(popupPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
            IntOffset(rect.left.roundToInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                (rect.top.roundToInt() - popupContentSize.height).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }, onDismissRequest = { menu = false }) {
        Surface(shadowElevation = 6.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.widthIn(max = 340.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(enabled = selectAll != null, onClick = { selectAll?.invoke(); menu = false }) { Text(stringResource(android.R.string.selectAll)) }
                TextButton(enabled = copy != null, onClick = { copy?.invoke(); menu = false }) { Text(stringResource(android.R.string.copy)) }
                TextButton(enabled = paste != null, onClick = { paste?.invoke(); menu = false }) { Text(stringResource(android.R.string.paste)) }
            }
            Row {
                TextButton(enabled = cut != null, onClick = { cut?.invoke(); menu = false }) { Text(stringResource(android.R.string.cut)) }
                TextButton(enabled = !current.selection.collapsed, onClick = { range = current.selection; menu = false; format = true }) { Text(stringResource(R.string.chat_format)) }
            }
            }
        }
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
        text = { TextField(link, { link = it }, singleLine = true, label = { Text("URL") }) },
        confirmButton = { TextButton(enabled = safeMessageUrl(link)?.let { it != "https://" } == true, onClick = {
            val target = safeMessageUrl(link) ?: return@TextButton
            onEntitiesChange(applyTextFormat(value, entities, range.min, range.max, TextStyleKind.Url).map {
                if (it.kind == TextStyleKind.Url && it.offset == range.min && it.length == range.max - range.min) it.copy(argument = target) else it
            })
            linkDialog = false
        }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = { linkDialog = false }) { Text(stringResource(R.string.common_close)) } })
}
