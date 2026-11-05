package com.beykus.beygram.domain.triggers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerWordsTest {
    private fun rule(
        pattern: String,
        mode: TriggerMatchMode = TriggerMatchMode.WHOLE_WORD,
        ignoreCase: Boolean = true,
        ignoreDiacritics: Boolean = false,
        enabled: Boolean = true,
        id: String = "id-${pattern.hashCode()}-${mode.name}"
    ) = TriggerRule(id, pattern, mode, ignoreCase, ignoreDiacritics, enabled)

    private fun fires(pattern: String, text: String, mode: TriggerMatchMode = TriggerMatchMode.WHOLE_WORD,
                      ignoreCase: Boolean = true, ignoreDiacritics: Boolean = false): Boolean =
        TriggerMatcher(listOf(rule(pattern, mode, ignoreCase, ignoreDiacritics))).matches(text).isNotEmpty()

    @Test(timeout = 10_000)
    fun nestedQuantifiersStopAtTheReadBudget() {
        val catastrophic = rule("((a+)+)+b", TriggerMatchMode.REGEX)
        assertNull(validateTriggerRule(catastrophic))
        assertTrue(TriggerMatcher(listOf(catastrophic)).matches("a".repeat(4096)).isEmpty())
    }

    @Test(timeout = 10_000)
    fun alternationOverLongTextDoesNotEscapeAsAnError() {
        assertFalse(fires("(a|b)*c", "ab".repeat(2048), TriggerMatchMode.REGEX))
    }

    @Test
    fun ordinaryExpressionStillMatchesLongText() {
        assertTrue(fires("a*b", "a".repeat(4096) + "b", TriggerMatchMode.REGEX))
    }

    @Test
    fun cyrillicMatchIgnoresCaseByDefault() {
        assertTrue(fires("срочно", "СРОЧНО всем в офис"))
        assertTrue(fires("Срочно", "нужно срочно ответить"))
    }

    @Test
    fun caseSensitiveRuleRejectsOtherCase() {
        assertFalse(fires("Срочно", "срочно", ignoreCase = false))
        assertTrue(fires("Срочно", "Срочно", ignoreCase = false))
    }

    @Test
    fun wholeWordRejectsLongerWordsButContainsAcceptsThem() {
        assertFalse(fires("тест", "Новые тесты готовы"))
        assertTrue(fires("тест", "Новые тесты готовы", mode = TriggerMatchMode.CONTAINS))
        assertTrue(fires("test", "run test-case now"))
        assertFalse(fires("test", "testing now"))
        assertFalse(fires("task", "task_1 is ready"))
    }

    @Test
    fun wholeWordMatchesAtPunctuationAndEdges() {
        assertTrue(fires("срочно", "Срочно!"))
        assertTrue(fires("срочно", "(срочно)"))
        assertTrue(fires("срочно", "срочно"))
        assertTrue(fires("срочно", "\"срочно\" — сказал он"))
    }

    @Test
    fun phraseMatchesAcrossWhitespaceAndLineBreaks() {
        assertTrue(fires("release day", "Next  release\tday!"))
        assertTrue(fires("release day", "next release\nday"))
        assertFalse(fires("release day", "release days"))
    }

    @Test
    fun diacriticsAreOptIn() {
        assertFalse(fires("cafe", "Café opens"))
        assertTrue(fires("cafe", "Café opens", ignoreDiacritics = true))
        assertTrue(fires("ёж", "ЁЖ", ignoreDiacritics = true))
        assertTrue(fires("ёж", "ЁЖ", ignoreDiacritics = false))
    }

    @Test
    fun unicodeCaseFoldingHandlesSharpSAndFinalSigma() {
        assertTrue(fires("straße", "STRASSE closed"))
        assertTrue(fires("οδος", "ΟΔΟΣ"))
    }

    @Test
    fun arabicAndCjkMatchByScript() {
        assertTrue(fires("مرحبا", "مرحبا بالجميع"))
        assertTrue(fires("北京", "我喜欢北京的秋天", mode = TriggerMatchMode.CONTAINS))
    }

    @Test
    fun emojiTriggerNeedsNoWordEdges() {
        assertTrue(fires("🚀", "launch 🚀!"))
    }

    @Test
    fun invisibleFormattingCharactersDoNotHideAWord() {
        assertTrue(fires("привет", "пр​ивет всем"))
        assertTrue(fires("привет", "при­вет всем"))
    }

    @Test
    fun regexRunsOnTextAsWritten() {
        assertTrue(fires("\\bdeadline\\b", "The Deadline is near", mode = TriggerMatchMode.REGEX))
        assertFalse(fires("\\bdeadline\\b", "deadlines", mode = TriggerMatchMode.REGEX))
        assertFalse(fires("\\bdeadline\\b", "The DEADLINE", mode = TriggerMatchMode.REGEX, ignoreCase = false))
    }

    @Test
    fun disabledRulesNeverFire() {
        val matcher = TriggerMatcher(listOf(rule("срочно", enabled = false)))
        assertTrue(matcher.isEmpty)
        assertTrue(matcher.matches("срочно").isEmpty())
    }

    @Test
    fun matchesReturnEveryFiredRuleInInputOrder() {
        val first = rule("ответ")
        val second = rule("срочно")
        val third = rule("проект")
        val fired = TriggerMatcher(listOf(first, second, third)).matches("Срочно, проект: ответ")
        assertEquals(listOf(first, second, third), fired)
    }

    @Test
    fun blankTextNeverMatches() {
        assertTrue(TriggerMatcher(listOf(rule("a"))).matches("   ").isEmpty())
    }

    @Test
    fun validationRejectsEmptyLongAndBrokenPatterns() {
        assertEquals(TriggerProblem.EMPTY, validateTriggerRule(rule("   ")))
        assertEquals(TriggerProblem.TOO_LONG, validateTriggerRule(rule("x".repeat(TriggerWordLimits.MAX_PATTERN_LENGTH + 1))))
        assertEquals(TriggerProblem.BAD_REGEX, validateTriggerRule(rule("[", TriggerMatchMode.REGEX)))
        assertEquals(TriggerProblem.REGEX_MATCHES_EMPTY, validateTriggerRule(rule("a*", TriggerMatchMode.REGEX)))
        assertEquals(TriggerProblem.REGEX_MATCHES_EMPTY, validateTriggerRule(rule("\\s*", TriggerMatchMode.REGEX)))
        assertNull(validateTriggerRule(rule("срочно")))
        assertNull(validateTriggerRule(rule("\\p{L}+ать", TriggerMatchMode.REGEX)))
    }

    @Test
    fun patternThatFoldsToNothingIsRejected() {
        // A lone combining mark disappears when diacritics are ignored, so it would match every message.
        assertEquals(TriggerProblem.EMPTY, validateTriggerRule(rule("́", ignoreDiacritics = true)))
    }

    @Test
    fun snippetCollapsesWhitespaceAndTruncates() {
        assertEquals("a b c", triggerSnippet("a\n\n  b\tc  "))
        val long = triggerSnippet("слово ".repeat(100))
        assertTrue(long.endsWith("…"))
        assertTrue(long.length <= TriggerWordLimits.MAX_SNIPPET_LENGTH + 1)
    }

    @Test
    fun rulesRoundTripThroughJsonAndInvalidEntriesAreDropped() {
        val rules = listOf(
            TriggerRule("1", "срочно", TriggerMatchMode.WHOLE_WORD, ignoreCase = true, ignoreDiacritics = true, enabled = false),
            TriggerRule("2", "\\bdead\\w*", TriggerMatchMode.REGEX, ignoreCase = false)
        )
        assertEquals(rules, TriggerWordsCodec.decodeRules(TriggerWordsCodec.encodeRules(rules)))

        val broken = """[{"id":"3","pattern":"[","mode":"REGEX"},{"id":"","pattern":"x"},{"id":"4","pattern":"ok","mode":"NOPE"}]"""
        val decoded = TriggerWordsCodec.decodeRules(broken)
        assertEquals(listOf(TriggerRule("4", "ok", TriggerMatchMode.WHOLE_WORD)), decoded)
        assertTrue(TriggerWordsCodec.decodeRules("not json").isEmpty())
        assertTrue(TriggerWordsCodec.decodeRules(null).isEmpty())
    }

    @Test
    fun mentionsRoundTripWithAndWithoutSnippet() {
        val mentions = listOf(
            TriggerMention("legacy:1:2", "legacy", 1L, 2L, 1_700_000_000L, "Чат", "Анна", "срочно сегодня",
                listOf("срочно"), muted = true),
            TriggerMention("acc1:9:10", "acc1", 9L, 10L, 1_700_000_100L, "Канал", "", null, listOf("a", "b"), muted = false)
        )
        assertEquals(mentions, TriggerWordsCodec.decodeMentions(TriggerWordsCodec.encodeMentions(mentions)))
    }
}
