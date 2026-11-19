package com.beykus.beygram.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesParserTest {
    private fun kinds(markdown: String) = parseReleaseNotes(markdown).lines.map { it.kind to it.text }

    @Test
    fun humanNotesStopBeforeTechnicalAppendix() {
        val result = parseReleaseNotes(
            """
            # BeyGram 0.0.0.7

            ## Исправления
            - Исправлена **загрузка** голосовых сообщений.

            <!-- TECHNICAL_CHANGELOG -->

            ## Для разработчиков
            - Merge PR #100
            """.trimIndent()
        )
        assertTrue(result.hadTechnicalAppendix)
        assertFalse(result.truncated)
        assertEquals(
            listOf(
                ReleaseNotesKind.Section to "Исправления",
                ReleaseNotesKind.Bullet to "Исправлена загрузка голосовых сообщений.",
            ),
            result.lines.map { it.kind to it.text }
        )
        assertEquals(2, result.lines.first().level)
    }

    @Test
    fun dropsMultiLineCommentsAndEverythingAfterTechnicalMarker() {
        val result = parseReleaseNotes(
            """
            ## Что нового
            - Готово.
            <!-- Release notes generated using configuration
            in .github/release.yml at 2e783d9 -->
            <!-- TECHNICAL_CHANGELOG -->
            ## What's Changed
            """.trimIndent()
        )
        assertEquals(
            listOf(ReleaseNotesKind.Section to "Что нового", ReleaseNotesKind.Bullet to "Готово."),
            result.lines.map { it.kind to it.text }
        )
    }

    @Test
    fun devRangeMarkerIsSkippedAsHtmlComment() {
        val sha = "a".repeat(40)
        val result = parseReleaseNotes(
            "## Что нового\n- Готово.\n<!-- beygram-dev-range from=none to=$sha complete=true -->\n"
        )
        assertEquals(
            listOf(ReleaseNotesKind.Section to "Что нового", ReleaseNotesKind.Bullet to "Готово."),
            result.lines.map { it.kind to it.text }
        )
        assertFalse(result.hadTechnicalAppendix)
    }

    @Test
    fun headingLevelsTwoAndUpAreSectionsAndLevelOneIsSkipped() {
        val result = parseReleaseNotes("# Title\n## A\n### B\n#### C")
        assertEquals(listOf(2, 3, 4), result.lines.map { it.level })
        assertEquals(listOf("A", "B", "C"), result.lines.map { it.text })
        assertTrue(result.lines.all { it.kind == ReleaseNotesKind.Section })
    }

    @Test
    fun acceptsOrderedMarkersUpToNineDigits() {
        val result = parseReleaseNotes("100. Шаг\nпродолжение")
        assertEquals(1, result.lines.size)
        assertEquals(ReleaseNotesKind.Numbered, result.lines[0].kind)
        assertEquals("100.", result.lines[0].marker)
        assertEquals("Шаг продолжение", result.lines[0].text)
    }

    @Test
    fun h1HeadingEndsTheOpenItem() {
        assertEquals(
            listOf(ReleaseNotesKind.Bullet to "first", ReleaseNotesKind.Paragraph to "standalone"),
            kinds("- first\n# New release\nstandalone")
        )
    }

    @Test
    fun keepsNumberedItemsAndJoinsWrappedLinesIntoTheItem() {
        val result = parseReleaseNotes(
            "#### Подробности\n1. Первый пункт\nпродолжение пункта\n2) Второй пункт\n* Маркер звёздочкой"
        )
        assertEquals(
            listOf(
                Triple(ReleaseNotesKind.Section, "Подробности", 4),
                Triple(ReleaseNotesKind.Numbered, "Первый пункт продолжение пункта", 0),
                Triple(ReleaseNotesKind.Numbered, "Второй пункт", 0),
                Triple(ReleaseNotesKind.Bullet, "Маркер звёздочкой", 0),
            ),
            result.lines.map { Triple(it.kind, it.text, it.level) }
        )
        assertEquals(listOf("1.", "2."), result.lines.filter { it.kind == ReleaseNotesKind.Numbered }.map { it.marker })
    }

    @Test
    fun inlineEmphasisAndCodeBecomeSpans() {
        val line = parseReleaseNotes("**жирный** _курсив_ `код` *курсив2* __жирный2__").lines.single()
        assertEquals(
            listOf(
                InlineSpan("жирный", bold = true),
                InlineSpan(" "),
                InlineSpan("курсив", italic = true),
                InlineSpan(" "),
                InlineSpan("код", code = true),
                InlineSpan(" "),
                InlineSpan("курсив2", italic = true),
                InlineSpan(" "),
                InlineSpan("жирный2", bold = true),
            ),
            line.spans
        )
        assertEquals("жирный курсив код курсив2 жирный2", line.text)
    }

    @Test
    fun snakeCaseIsNotEmphasis() {
        val line = parseReleaseNotes("Вызов my_func_name здесь").lines.single()
        assertEquals(listOf(InlineSpan("Вызов my_func_name здесь")), line.spans)
    }

    @Test
    fun httpsLinkKeepsItsTarget() {
        val line = parseReleaseNotes("- Открыть [подробности](https://github.com/Beykus-Y/BeyGram/releases)").lines.single()
        assertEquals(
            listOf(InlineSpan("Открыть "), InlineSpan("подробности", url = "https://github.com/Beykus-Y/BeyGram/releases")),
            line.spans
        )
    }

    @Test
    fun unsafeLinksFallBackToTheirLabel() {
        val targets = listOf(
            "http://example.com",
            "javascript:alert(1)",
            "data:text/html,hi",
            "ftp://example.com/file",
            "https://user:secret@example.com/",
            "https://",
            "https://exa mple.com",
            "HTTP://example.com",
        )
        for (target in targets) {
            val line = parseReleaseNotes("[метка]($target)").lines.single()
            assertEquals("метка", line.text)
            assertTrue("target $target", line.spans.all { it.url == null })
        }
    }

    @Test
    fun safeLinkUrlAcceptsOnlyHttpsWithHost() {
        assertEquals("https://github.com/a", safeLinkUrl("https://github.com/a"))
        assertNull(safeLinkUrl("https://github.com/a b"))
        assertNull(safeLinkUrl("https:///path"))
        assertNull(safeLinkUrl("  "))
    }

    @Test
    fun htmlTagsAreRemovedAndComparisonsKept() {
        assertEquals("Текст жирный и x", parseReleaseNotes("Текст <b>жирный</b> и <script>x</script>").lines.single().text)
        assertEquals("1 < 2 и 3 > 2", parseReleaseNotes("1 < 2 и 3 > 2").lines.single().text)
    }

    @Test
    fun longNotesAreCutToTheLineLimit() {
        val result = parseReleaseNotes((1..200).joinToString("\n") { "Пункт $it" })
        assertEquals(120, result.lines.size)
        assertTrue(result.truncated)
    }

    @Test
    fun oversizedInputIsTruncated() {
        val result = parseReleaseNotes("a".repeat(15_001))
        assertTrue(result.truncated)
    }

    @Test
    fun emptyNotesHaveNoLines() {
        val result = parseReleaseNotes("")
        assertTrue(result.lines.isEmpty())
        assertFalse(result.truncated)
        assertFalse(result.hadTechnicalAppendix)
    }

    @Test
    fun malformedMarkupStaysLiteralAndNeverThrows() {
        val samples = listOf(
            "**не закрыт" to "**не закрыт",
            "[метка](" to "[метка](",
            "`незакрыт" to "`незакрыт",
            "[](https://a.b)" to "[](https://a.b)",
            "](" to "](",
            "<b" to "<b",
            "**" to "**",
            "[вложенная [ссылка](https://x.y)" to "вложенная [ссылка",
        )
        for ((input, expected) in samples) {
            val lines = parseReleaseNotes(input).lines
            assertEquals(input, expected, lines.single().text)
        }
    }

    @Test
    fun oldReleaseWithoutMarkdownRemainsVisible() {
        val result = parseReleaseNotes("Android APKs built from a1b2c3d.")
        assertEquals(1, result.lines.size)
        assertEquals(ReleaseNotesKind.Paragraph, result.lines[0].kind)
        assertFalse(result.lines[0].text.isBlank())
    }
}
