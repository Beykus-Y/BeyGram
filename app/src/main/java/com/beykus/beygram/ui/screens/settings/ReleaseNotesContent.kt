package com.beykus.beygram.ui.screens.settings

/**
 * Stable release notes are written in a small, reviewable Markdown subset:
 * headings, bullet items and plain paragraphs. The technical appendix is
 * intended for GitHub and must not clutter the Android update dialog.
 */
internal enum class ReleaseNotesKind { Section, Bullet, Paragraph }

internal data class ReleaseNotesLine(val kind: ReleaseNotesKind, val text: String)

private const val TECHNICAL_CHANGELOG_MARKER = "<!-- TECHNICAL_CHANGELOG -->"

/** Pure, bounded parser: never passes GitHub Markdown or HTML through a WebView. */
internal fun parseReleaseNotes(markdown: String): List<ReleaseNotesLine> =
    markdown.lineSequence()
        .takeWhile { it.trim() != TECHNICAL_CHANGELOG_MARKER }
        .mapNotNull { raw ->
            val line = raw.trim()
            when {
                line.isBlank() || line.startsWith("# ") || line.startsWith("<!--") -> null
                line.startsWith("## ") -> ReleaseNotesLine(ReleaseNotesKind.Section, releaseNotePlainText(line.removePrefix("## ")))
                line.startsWith("### ") -> ReleaseNotesLine(ReleaseNotesKind.Section, releaseNotePlainText(line.removePrefix("### ")))
                line.startsWith("- ") -> ReleaseNotesLine(ReleaseNotesKind.Bullet, releaseNotePlainText(line.removePrefix("- ")))
                else -> ReleaseNotesLine(ReleaseNotesKind.Paragraph, releaseNotePlainText(line))
            }
        }
        .filter { it.text.isNotBlank() }
        .take(120)
        .toList()

/** Preserve readable labels instead of exposing raw Markdown syntax. */
private fun releaseNotePlainText(value: String): String =
    value.replace(Regex("\\[([^]]+)]\\(https?://[^)]+\\)"), "$1")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .trim()
