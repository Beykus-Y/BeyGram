package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.contextmenu.data.*
import androidx.compose.foundation.text.contextmenu.provider.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.roundToInt

internal object FormatSelectionKey

private class SelectionSession(val data: TextContextMenuDataProvider) : TextContextMenuSession {
    val closed = CompletableDeferred<Unit>()
    override fun close() { closed.complete(Unit) }
}

private class SelectionProvider : TextContextMenuProvider {
    var session by mutableStateOf<SelectionSession?>(null)
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        session?.close()
        val next = SelectionSession(dataProvider)
        try { session = next; next.closed.await() }
        finally { if (session === next) session = null }
    }
}

/** Compose 1.10 uses this provider instead of the old LocalTextToolbar. */
@Composable
internal fun SelectionMenuProvider(modifier: Modifier, content: @Composable () -> Unit) {
    val provider = remember { SelectionProvider() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(provider) { onDispose { provider.session?.close() } }
    CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides provider, LocalTextContextMenuDropdownProvider provides provider) {
        Box(modifier.onGloballyPositioned { coordinates = it }, propagateMinConstraints = true) {
            content()
            val session = provider.session
            val anchor = coordinates
            if (session != null && anchor != null && anchor.isAttached) {
                val bounds = session.data.contentBounds(anchor).translate(anchor.positionInWindow())
                val items = session.data.data().components.filterIsInstance<TextContextMenuItem>()
                Popup(popupPositionProvider = SelectionPosition(bounds), onDismissRequest = session::close) {
                    Surface(shadowElevation = 6.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                        Column(Modifier.widthIn(max = 340.dp).padding(horizontal = 4.dp)) {
                            listOf(listOf(TextContextMenuKeys.SelectAllKey, TextContextMenuKeys.CopyKey, TextContextMenuKeys.PasteKey),
                                listOf(TextContextMenuKeys.CutKey, FormatSelectionKey)).forEach { keys ->
                                Row {
                                    keys.forEach { key -> items.firstOrNull { it.key == key }?.let { item ->
                                        TextButton(onClick = { item.onClick(session) }) { Text(item.label) }
                                    } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class SelectionPosition(private val bounds: Rect) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(bounds.left.roundToInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            (bounds.top.roundToInt() - popupContentSize.height).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
}
