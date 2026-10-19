package com.beykus.beygram.ui.screens.settings

/**
 * Stable release notes are written in a small, reviewable Markdown subset:
 * headings, bullet and numbered items, plain paragraphs, bold, italic and
 * links. The technical appendix is intended for GitHub and must not clutter
 * the Android update dialog.
 */
internal enum class ReleaseNotesKind { Section, Bullet, Numbered, Paragraph }

/** [marker] is the visible number of a [ReleaseNotesKind.Numbered] item, empty otherwise. */
internal data class ReleaseNotesLine(val kind: ReleaseNotesKind, val text: String, val marker: String = "")

private const val TECHNICAL_CHANGELOG_MARKER = "<!-- TECHNICAL_CHANGELOG -->"
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val NUMBERED_ITEM = Regex("^(\\d{1,2})[.)]\\s+(.*)$")

/**
 * Pure, bounded parser: never passes GitHub Markdown or HTML through a WebView.
 * A plain line directly under a list item continues that item, as in Markdown.
 */
internal fun parseReleaseNotes(markdown: String): List<ReleaseNotesLine> {
    val lines = mutableListOf<ReleaseNotesLine>()
    var inItem = false
    for (raw in markdown.lineSequence()) {
        val line = raw.trim()
        if (line == TECHNICAL_CHANGELOG_MARKER) break
        if (line.isBlank()) {
            inItem = false
            continue
        }
        if (line.startsWith("<!--")) continue
        val heading = HEADING.matchEntire(line)
        val numbered = NUMBERED_ITEM.matchEntire(line)
        when {
            heading != null && heading.groupValues[1] == "#" -> Unit
            heading != null -> {
                lines += ReleaseNotesLine(ReleaseNotesKind.Section, releaseNotePlainText(heading.groupValues[2]))
                inItem = false
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                lines += ReleaseNotesLine(ReleaseNotesKind.Bullet, releaseNotePlainText(line.drop(2)))
                inItem = true
            }
            numbered != null -> {
                lines += ReleaseNotesLine(
                    ReleaseNotesKind.Numbered, releaseNotePlainText(numbered.groupValues[2]),
                    marker = numbered.groupValues[1] + "."
                )
                inItem = true
            }
            inItem && lines.isNotEmpty() -> {
                val last = lines.last()
                lines[lines.lastIndex] = last.copy(text = "${last.text} ${releaseNotePlainText(line)}")
            }
            else -> lines += ReleaseNotesLine(ReleaseNotesKind.Paragraph, releaseNotePlainText(line))
        }
    }
    return lines.filter { it.text.isNotBlank() }.take(120)
}

/** Preserve readable labels instead of exposing raw Markdown syntax. */
private fun releaseNotePlainText(value: String): String =
    value.replace(Regex("\\[([^]]+)]\\(https?://[^)]+\\)"), "$1")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .replace(Regex("\\*(?=\\S)(.+?)(?<=\\S)\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .trim()
