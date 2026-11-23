package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.OutlinedButton
import com.beykus.beygram.ui.components.beyGramPalette
import java.net.URI

/*
 * Stable and DEV release notes are written in a small, reviewable Markdown subset: headings, bullet and numbered items,
 * paragraphs, bold, italic, code and https links. Nothing is interpreted as HTML and no WebView is used. The technical
 * appendix is for GitHub and is cut before it reaches the update screen. The model and parser below are pure Kotlin.
 */

/** A run of text with its emphasis. [url] is set only for a link that passed [safeLinkUrl]. */
internal data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

internal enum class ReleaseNotesKind { Section, Bullet, Numbered, Paragraph }

/**
 * One visible line. [marker] is the visible number of a [ReleaseNotesKind.Numbered] item, empty otherwise.
 * [level] is the heading level of a [ReleaseNotesKind.Section]. [text] is the plain text, without any markup.
 */
internal data class ReleaseNotesLine(
    val kind: ReleaseNotesKind,
    val spans: List<InlineSpan>,
    val marker: String = "",
    val level: Int = 0,
) {
    val text: String get() = spans.joinToString(separator = "") { it.text }
}

internal data class ParsedReleaseNotes(
    val lines: List<ReleaseNotesLine>,
    /** True when the input or the line count was cut. */
    val truncated: Boolean,
    /** True when the technical changelog marker was found; everything after it is dropped. */
    val hadTechnicalAppendix: Boolean,
)

/** The compact "Что нового" overview shows this many lines before the user expands it. */
internal const val RELEASE_NOTES_PREVIEW_LINES = 6

private const val TECHNICAL_CHANGELOG_MARKER = "<!-- TECHNICAL_CHANGELOG -->"
private const val MAX_INPUT_CHARS = 15_000
private const val MAX_LINES = 120
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val NUMBERED_ITEM = Regex("^(\\d{1,9})[.)]\\s+(.*)$")
private val HTML_TAG = Regex("</?[A-Za-z][A-Za-z0-9-]*(\\s[^<>]*)?/?>")

/**
 * Pure, bounded parser. HTML comments are skipped, raw HTML tags are dropped from the text, and the technical
 * appendix is cut. A plain line directly under a list item continues that item, as in Markdown.
 * Malformed markup is kept as literal text; this function does not throw on any input.
 */
internal fun parseReleaseNotes(markdown: String): ParsedReleaseNotes {
    val input = markdown.take(MAX_INPUT_CHARS)
    val lines = mutableListOf<ReleaseNotesLine>()
    var hadTechnicalAppendix = false
    var inItem = false
    var inComment = false
    for (raw in input.lineSequence()) {
        val line = raw.trim()
        if (line == TECHNICAL_CHANGELOG_MARKER) {
            hadTechnicalAppendix = true
            break
        }
        if (inComment) {
            inComment = !line.contains("-->")
            continue
        }
        if (line.isBlank()) {
            inItem = false
            continue
        }
        if (line.startsWith("<!--")) {
            inComment = !line.contains("-->")
            continue
        }
        val heading = HEADING.matchEntire(line)
        val numbered = NUMBERED_ITEM.matchEntire(line)
        when {
            // A level-1 heading repeats the release title, so it is skipped.
            heading != null && heading.groupValues[1].length == 1 -> inItem = false
            heading != null -> {
                lines += ReleaseNotesLine(
                    ReleaseNotesKind.Section,
                    parseInline(heading.groupValues[2]),
                    level = heading.groupValues[1].length,
                )
                inItem = false
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                lines += ReleaseNotesLine(ReleaseNotesKind.Bullet, parseInline(line.drop(2)))
                inItem = true
            }
            numbered != null -> {
                lines += ReleaseNotesLine(
                    ReleaseNotesKind.Numbered,
                    parseInline(numbered.groupValues[2]),
                    marker = numbered.groupValues[1] + ".",
                )
                inItem = true
            }
            inItem && lines.isNotEmpty() -> {
                val last = lines.last()
                lines[lines.lastIndex] = last.copy(spans = joinSpans(last.spans, parseInline(line)))
            }
            else -> lines += ReleaseNotesLine(ReleaseNotesKind.Paragraph, parseInline(line))
        }
    }
    val visible = lines.filter { it.text.isNotBlank() }
    return ParsedReleaseNotes(
        lines = visible.take(MAX_LINES),
        truncated = markdown.length > MAX_INPUT_CHARS || visible.size > MAX_LINES,
        hadTechnicalAppendix = hadTechnicalAppendix,
    )
}

/**
 * The https link [raw] as it may be opened, or null. Only https with a host is kept; userinfo, whitespace, control
 * characters, other schemes (http, javascript, data, ...) and anything java.net.URI rejects are refused.
 */
internal fun safeLinkUrl(raw: String): String? {
    val candidate = raw.trim()
    if (candidate.isEmpty() || candidate.any { it.isWhitespace() || it.isISOControl() }) return null
    if (!candidate.startsWith("https://", ignoreCase = true)) return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.rawUserInfo != null || uri.userInfo != null) return null
    if (uri.host.isNullOrBlank()) return null
    return candidate
}

private fun joinSpans(first: List<InlineSpan>, second: List<InlineSpan>): List<InlineSpan> = when {
    first.isEmpty() -> second
    second.isEmpty() -> first
    else -> first + InlineSpan(" ") + second
}

/**
 * Inline Markdown of one line: `code`, **bold**, __bold__, *italic*, _italic_ (word boundaries) and [label](url).
 * An unclosed or malformed marker stays literal text. Raw HTML tags are removed, never interpreted.
 */
private fun parseInline(text: String, allowLinks: Boolean = true): List<InlineSpan> {
    val out = mutableListOf<InlineSpan>()
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            out += InlineSpan(plain.toString())
            plain.clear()
        }
    }
    fun emit(spans: List<InlineSpan>) {
        flush()
        out += spans
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                emit(listOf(InlineSpan(text.substring(i + 1, end), code = true)))
                i = end + 1
            } else {
                plain.append(c)
                i++
            }
            continue
        }
        if (c == '[' && allowLinks) {
            val link = parseLink(text, i)
            if (link != null) {
                val url = safeLinkUrl(link.target)
                emit(parseInline(link.label, allowLinks = false).map { it.copy(url = url) })
                i = link.end
                continue
            }
        }
        if (c == '<') {
            val close = text.indexOf('>', i + 1)
            if (close > i && HTML_TAG.matches(text.substring(i, close + 1))) {
                i = close + 1
                continue
            }
        }
        if ((c == '*' || c == '_') && text.startsWith("$c$c", i) && canOpen(text, i, 2, c)) {
            val delim = "$c$c"
            val end = findCloser(text, i + 2, delim, wordBound = c == '_')
            if (end >= 0) {
                emit(parseInline(text.substring(i + 2, end), allowLinks).map { it.copy(bold = true) })
                i = end + 2
            } else {
                plain.append(delim)
                i += 2
            }
            continue
        }
        if ((c == '*' || c == '_') && canOpen(text, i, 1, c)) {
            val end = findCloser(text, i + 1, c.toString(), wordBound = c == '_')
            if (end >= 0) {
                emit(parseInline(text.substring(i + 1, end), allowLinks).map { it.copy(italic = true) })
                i = end + 1
                continue
            }
        }
        plain.append(c)
        i++
    }
    flush()
    return out
}

/** A marker can open emphasis when text follows it; `_` also needs a word boundary before it. */
private fun canOpen(text: String, index: Int, width: Int, marker: Char): Boolean {
    val after = index + width
    if (after >= text.length || text[after].isWhitespace()) return false
    return marker != '_' || index == 0 || !text[index - 1].isLetterOrDigit()
}

/** The index of the next [delim] that closes emphasis: not after a space, and at a word boundary for `_`. */
private fun findCloser(text: String, from: Int, delim: String, wordBound: Boolean): Int {
    var j = text.indexOf(delim, from)
    while (j >= 0) {
        val after = j + delim.length
        val boundaryOk = !wordBound || after >= text.length || !text[after].isLetterOrDigit()
        if (j > from && !text[j - 1].isWhitespace() && boundaryOk) return j
        j = text.indexOf(delim, j + 1)
    }
    return -1
}

private class LinkParts(val label: String, val target: String, val end: Int)

/** `[label](target)` starting at [start], with balanced parentheses in the target; null when malformed. */
private fun parseLink(text: String, start: Int): LinkParts? {
    val labelEnd = text.indexOf(']', start + 1)
    if (labelEnd < 0 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
    val label = text.substring(start + 1, labelEnd)
    if (label.isBlank()) return null
    var depth = 1
    var j = labelEnd + 2
    while (j < text.length) {
        when (text[j]) {
            '(' -> depth++
            ')' -> depth--
        }
        if (depth == 0) break
        j++
    }
    if (depth != 0) return null
    return LinkParts(label, text.substring(labelEnd + 2, j).trim(), j + 1)
}

private fun ReleaseNotesLine.toAnnotated(linkColor: Color): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        pushStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
            )
        )
        val url = span.url
        if (url != null) {
            pushLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                )
            )
            append(span.text)
            pop()
        } else {
            append(span.text)
        }
        pop()
    }
}

/**
 * Release notes as a readable list. In [compact] mode the first [RELEASE_NOTES_PREVIEW_LINES] lines are shown and the
 * rest expand in place. A cut text says so and, when [releaseUrl] is known, links to the full release on GitHub.
 */
@Composable
internal fun ReleaseNotesContent(
    markdown: String,
    truncated: Boolean = false,
    releaseUrl: String? = null,
    compact: Boolean = false,
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val parsed = remember(markdown) { parseReleaseNotes(markdown) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val lines = parsed.lines
    if (lines.isEmpty()) {
        Text(stringResource(R.string.updates_no_notes), color = palette.textSecondary, fontSize = 15.sp)
        return
    }
    val showAll = !compact || expanded || lines.size <= RELEASE_NOTES_PREVIEW_LINES
    val visible = if (showAll) lines else lines.take(RELEASE_NOTES_PREVIEW_LINES)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { line -> ReleaseNoteLine(line) }
        if (!showAll) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_show_all_changes, lines.size))
            }
        }
        if (truncated || parsed.truncated) {
            Text(stringResource(R.string.updates_notes_truncated), color = palette.textSecondary, fontSize = 14.sp)
            releaseUrl?.let { url ->
                OutlinedButton(onClick = { openExternalUrl(context, url) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.updates_open_release))
                }
            }
        }
    }
}

@Composable
private fun ReleaseNoteLine(line: ReleaseNotesLine) {
    val palette = beyGramPalette()
    val annotated = remember(line, palette.accent) { line.toAnnotated(palette.accent) }
    when (line.kind) {
        ReleaseNotesKind.Section -> Text(
            annotated,
            color = palette.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (line.level <= 2) 16.sp else 15.sp,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
        )
        ReleaseNotesKind.Bullet -> Row(verticalAlignment = Alignment.Top) {
            Text("•", color = palette.accent, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
            Text(annotated, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
        ReleaseNotesKind.Numbered -> Row(verticalAlignment = Alignment.Top) {
            Text(line.marker, color = palette.accent, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
            Text(annotated, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
        ReleaseNotesKind.Paragraph -> Text(annotated, color = palette.textPrimary, fontSize = 15.sp)
    }
}
