package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import java.text.DateFormat
import java.util.Date

/** Строка поиска вместо заголовка чата: «назад» закрывает поиск, поле вводит запрос. */
@Composable
internal fun ChatSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Закрыть поиск", tint = palette.textPrimary) }
            Box(Modifier.weight(1f)) { SearchField(query, onQueryChange, placeholder = "Поиск в чате") }
        }
        HorizontalDivider(color = palette.divider)
    }
}

/** Результаты поиска поверх переписки: совпадения подсвечены, страницы догружаются по кнопке. */
@Composable
internal fun ChatSearchResults(
    query: String, results: List<ChatMessage>, busy: Boolean, error: String?, hasMore: Boolean,
    onOpen: (ChatMessage) -> Unit, onMore: () -> Unit
) {
    val palette = beyGramPalette()
    val needle = query.trim()
    Box(Modifier.fillMaxSize().background(palette.screen).imePadding()) {
        when {
            needle.isEmpty() -> SearchNote("Введите текст, чтобы найти сообщения в этом чате", palette.textSecondary)
            busy && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            results.isEmpty() -> if (error != null) SearchNote(error, palette.danger) else SearchNote("Ничего не найдено", palette.textSecondary)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(results, key = { it.id }) { message -> SearchResultRow(message, needle) { onOpen(message) } }
                if (hasMore) item(key = "search_more") {
                    TextButton(onClick = onMore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Загружаем…" else "Показать ещё")
                    }
                }
                error?.let { message ->
                    item(key = "search_error") { Text(message, color = palette.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(message: ChatMessage, query: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (message.isOutgoing) "Вы" else message.senderName, color = palette.accentInk, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(formatSearchDate(message.dateSeconds), color = palette.textSecondary, fontSize = 12.sp)
        }
        Text(
            highlightMatches(snippet(message.text.ifBlank { message.summary() }, query), query, palette.accentInk),
            color = palette.textPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchNote(text: String, color: Color) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, textAlign = TextAlign.Center)
    }
}

private fun formatSearchDate(seconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(seconds * 1000))

private const val SNIPPET_LEAD = 40

/** Кусок текста вокруг первого совпадения: иначе в длинном сообщении оно оказалось бы за краем строки. */
private fun snippet(text: String, query: String): String {
    val flat = text.replace('\n', ' ')
    val at = flat.indexOf(query, ignoreCase = true)
    if (at <= SNIPPET_LEAD) return flat
    var start = at - SNIPPET_LEAD
    if (Character.isLowSurrogate(flat[start])) start--
    return "…" + flat.substring(start)
}

/** Совпадения с запросом выделяются цветом акцента и полужирным начертанием. */
private fun highlightMatches(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isEmpty()) return@buildAnnotatedString
    var from = text.indexOf(query, ignoreCase = true)
    while (from >= 0) {
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), from, from + query.length)
        from = text.indexOf(query, from + query.length, ignoreCase = true)
    }
}
