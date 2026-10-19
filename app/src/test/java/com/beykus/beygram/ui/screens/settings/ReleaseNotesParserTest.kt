package com.beykus.beygram.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReleaseNotesParserTest {
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
        assertEquals(
            listOf(
                ReleaseNotesLine(ReleaseNotesKind.Section, "Исправления"),
                ReleaseNotesLine(ReleaseNotesKind.Bullet, "Исправлена загрузка голосовых сообщений.")
            ),
            result
        )
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
            listOf(
                ReleaseNotesLine(ReleaseNotesKind.Section, "Что нового"),
                ReleaseNotesLine(ReleaseNotesKind.Bullet, "Готово.")
            ),
            result
        )
    }

    @Test
    fun supportsReadableLinksAndMarkupWithoutRawSyntax() {
        val result = parseReleaseNotes(
            "## Улучшения\n- Открыть [подробности](https://github.com/example/repo)\n\nТекст с `кодом` и *курсивом*."
        )
        assertEquals(3, result.size)
        assertEquals("Открыть подробности", result[1].text)
        assertEquals("Текст с кодом и курсивом.", result[2].text)
    }

    @Test
    fun keepsNumberedItemsAndJoinsWrappedLinesIntoTheItem() {
        val result = parseReleaseNotes(
            "#### Подробности\n1. Первый пункт\nпродолжение пункта\n2) Второй пункт\n* Маркер звёздочкой"
        )
        assertEquals(
            listOf(
                ReleaseNotesLine(ReleaseNotesKind.Section, "Подробности"),
                ReleaseNotesLine(ReleaseNotesKind.Numbered, "Первый пункт продолжение пункта", marker = "1."),
                ReleaseNotesLine(ReleaseNotesKind.Numbered, "Второй пункт", marker = "2."),
                ReleaseNotesLine(ReleaseNotesKind.Bullet, "Маркер звёздочкой")
            ),
            result
        )
    }

    @Test
    fun oldReleaseWithoutMarkdownRemainsVisible() {
        val result = parseReleaseNotes("Android APKs built from a1b2c3d.")
        assertEquals(1, result.size)
        assertEquals(ReleaseNotesKind.Paragraph, result[0].kind)
        assertFalse(result[0].text.isBlank())
    }
}
