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
    fun supportsPlainParagraphsAndReadableLinks() {
        val result = parseReleaseNotes(
            "## Улучшения\n- Открыть [подробности](https://github.com/example/repo)\nТекст с `кодом`."
        )
        assertEquals(3, result.size)
        assertEquals("Открыть подробности", result[1].text)
        assertEquals("Текст с кодом.", result[2].text)
    }

    @Test
    fun oldReleaseWithoutMarkdownRemainsVisible() {
        val result = parseReleaseNotes("Android APKs built from a1b2c3d.")
        assertEquals(1, result.size)
        assertEquals(ReleaseNotesKind.Paragraph, result[0].kind)
        assertFalse(result[0].text.isBlank())
    }
}
