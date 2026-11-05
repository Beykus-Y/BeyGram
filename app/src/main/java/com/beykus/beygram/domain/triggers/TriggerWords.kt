package com.beykus.beygram.domain.triggers

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Trigger words: words, phrases or regular expressions that flag incoming messages.
 *
 * Nothing here depends on a language. Matching uses Unicode normalization (NFKC, optional
 * removal of nonspacing marks), Unicode case folding and Unicode word segmentation, so Latin,
 * Cyrillic, Greek, Arabic, Indic, CJK and any other script work the same way.
 */

/** How a trigger is compared with the text of a message. */
enum class TriggerMatchMode {
    /** The trigger must not touch letters or digits on either side. Scripts without spaces use word segmentation. */
    WHOLE_WORD,
    /** The trigger may appear anywhere, including inside longer words. */
    CONTAINS,
    /** The trigger is a Java/Unicode regular expression applied to the text as written. */
    REGEX
}

/** Which chats the triggers look at. */
enum class TriggerScope {
    /** Only chats that are muted, where Telegram itself stays silent. */
    MUTED_CHATS,
    /** Every chat. Matches in unmuted chats only go to the mentions feed, because Telegram already alerts there. */
    ALL_CHATS
}

data class TriggerRule(
    val id: String,
    val pattern: String,
    val mode: TriggerMatchMode = TriggerMatchMode.WHOLE_WORD,
    val ignoreCase: Boolean = true,
    /** Treats accented letters as their base letters. Off by default: "й" and "и" are different letters in many languages. */
    val ignoreDiacritics: Boolean = false,
    val enabled: Boolean = true
)

/** Why a pattern cannot be saved. The UI maps each value to a localized message. */
enum class TriggerProblem { EMPTY, TOO_LONG, BAD_REGEX, REGEX_MATCHES_EMPTY, TOO_MANY }

object TriggerWordLimits {
    const val MAX_PATTERN_LENGTH = 120
    const val MAX_RULES = 50
    const val MAX_MENTIONS = 200
    const val MAX_SNIPPET_LENGTH = 200
}

private val REGEX_PROBES = listOf("", " ")

/** Returns the problem with [rule], or null when it can be saved and used for matching. */
fun validateTriggerRule(rule: TriggerRule): TriggerProblem? {
    val pattern = rule.pattern.trim()
    if (pattern.isEmpty()) return TriggerProblem.EMPTY
    if (pattern.length > TriggerWordLimits.MAX_PATTERN_LENGTH) return TriggerProblem.TOO_LONG
    if (rule.mode != TriggerMatchMode.REGEX) {
        // A pattern that folds to nothing (for example only combining marks) would match every text.
        return if (foldText(pattern, rule.ignoreCase, rule.ignoreDiacritics).isEmpty()) TriggerProblem.EMPTY else null
    }
    val compiled = try {
        Pattern.compile(pattern, regexFlags(rule.ignoreCase))
    } catch (_: PatternSyntaxException) {
        return TriggerProblem.BAD_REGEX
    }
    // An expression that matches an empty text would fire on every message.
    return if (REGEX_PROBES.any { compiled.matcher(it).find() }) TriggerProblem.REGEX_MATCHES_EMPTY else null
}

/**
 * Matches message texts against a fixed set of rules. Create one per batch of messages;
 * it only keeps the compiled rules, and [matches] folds each text once per option pair.
 */
class TriggerMatcher(rules: Collection<TriggerRule>) {
    private val compiled: List<CompiledTrigger> = rules.asSequence()
        .filter { it.enabled }
        .mapNotNull(::compileTrigger)
        .toList()

    val isEmpty: Boolean get() = compiled.isEmpty()

    /** Returns the enabled rules that fire for [text], in the order of the input rules. */
    fun matches(text: String): List<TriggerRule> {
        if (compiled.isEmpty() || text.isBlank()) return emptyList()
        val variants = TextVariants(text)
        return compiled.filter { it.matches(variants) }.map { it.rule }
    }
}

/** Normalizes [text] the way rules are matched. Exposed for tests and for building a short snippet. */
internal fun foldText(text: String, ignoreCase: Boolean, ignoreDiacritics: Boolean): String {
    var value = stripFormatting(Normalizer.normalize(text, Normalizer.Form.NFKC))
    if (ignoreDiacritics) {
        value = NONSPACING_MARKS.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
    }
    if (ignoreCase) value = value.uppercase(Locale.ROOT).lowercase(Locale.ROOT)
    return WHITESPACE.replace(value, " ").trim()
}

private val WHITESPACE = Regex("[\\p{Z}\\s]+")
private val NONSPACING_MARKS = Regex("\\p{Mn}+")
private val FORMAT_CHARACTERS = Regex("\\p{Cf}+")

/** Zero-width characters and soft hyphens are invisible and must not split a word or a phrase. */
private fun stripFormatting(text: String): String = FORMAT_CHARACTERS.replace(text, "")

private fun regexFlags(ignoreCase: Boolean): Int =
    Pattern.UNICODE_CHARACTER_CLASS or (if (ignoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0)

/** Character reads one expression may make on one message. A pattern that backtracks past this does not match. */
private const val REGEX_READ_BUDGET = 500_000

/** Thrown from inside the regex engine when a match runs past its budget. No stack trace, so it stays cheap. */
private class RegexBudgetExceeded : RuntimeException(null, null, false, false)

/**
 * The text an expression reads. The engine reads each character through [get], so counting those reads bounds the
 * work of nested quantifiers, which backtrack for minutes on a few dozen characters.
 */
private class BudgetedText(private val text: String) : CharSequence {
    private var reads = 0
    override val length: Int get() = text.length
    override fun get(index: Int): Char {
        if (++reads > REGEX_READ_BUDGET) throw RegexBudgetExceeded()
        return text[index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString(): String = text
}

/**
 * Runs an expression on [text] within its budget. Running out of budget, or the stack on alternation over a long
 * text (`(a|b)*c` overflows on 4096 characters), counts as no match: a message must never stop the update path.
 */
private fun matchesWithinBudget(pattern: Pattern, text: String): Boolean = try {
    pattern.matcher(BudgetedText(text)).find()
} catch (_: RegexBudgetExceeded) {
    false
} catch (_: StackOverflowError) {
    false
}

private fun compileTrigger(rule: TriggerRule): CompiledTrigger? {
    val pattern = rule.pattern.trim()
    if (validateTriggerRule(rule.copy(pattern = pattern)) != null) return null
    return if (rule.mode == TriggerMatchMode.REGEX) {
        CompiledTrigger(rule, folded = "", regex = Pattern.compile(pattern, regexFlags(rule.ignoreCase)))
    } else {
        val folded = foldText(pattern, rule.ignoreCase, rule.ignoreDiacritics)
        if (folded.isEmpty()) null else CompiledTrigger(rule, folded = folded, regex = null)
    }
}

private class CompiledTrigger(val rule: TriggerRule, val folded: String, val regex: Pattern?) {
    /** Only triggers in scripts without spaces let word segmentation decide their edges. */
    private val segmentedByScript: Boolean = folded.isNotEmpty() && hasNoSpaceScript(folded)

    fun matches(variants: TextVariants): Boolean {
        regex?.let { return matchesWithinBudget(it, variants.raw) }
        val text = variants.folded(rule.ignoreCase, rule.ignoreDiacritics)
        return when (rule.mode) {
            TriggerMatchMode.CONTAINS -> text.value.contains(folded)
            TriggerMatchMode.WHOLE_WORD -> containsWholeWord(text, folded, segmentedByScript)
            TriggerMatchMode.REGEX -> false
        }
    }
}

/** Scripts whose words are not separated by spaces. Their word edges come from Unicode segmentation. */
private val NO_SPACE_SCRIPTS = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.THAI,
    Character.UnicodeScript.LAO,
    Character.UnicodeScript.KHMER,
    Character.UnicodeScript.MYANMAR
)

private fun hasNoSpaceScript(pattern: String): Boolean {
    var index = 0
    while (index < pattern.length) {
        val codePoint = Character.codePointAt(pattern, index)
        if (Character.UnicodeScript.of(codePoint) in NO_SPACE_SCRIPTS) return true
        index += Character.charCount(codePoint)
    }
    return false
}

/** One message text in the variants that rules ask for; each variant is computed at most once. */
private class TextVariants(text: String) {
    val raw: String = stripFormatting(Normalizer.normalize(text, Normalizer.Form.NFKC))
    private val folds = HashMap<Int, FoldedText>(4)

    fun folded(ignoreCase: Boolean, ignoreDiacritics: Boolean): FoldedText {
        val key = (if (ignoreCase) 1 else 0) or (if (ignoreDiacritics) 2 else 0)
        return folds.getOrPut(key) { FoldedText(foldText(raw, ignoreCase, ignoreDiacritics)) }
    }
}

private class FoldedText(val value: String) {
    private val breaks: BreakIterator by lazy { BreakIterator.getWordInstance(Locale.ROOT).also { it.setText(value) } }
    fun isWordBoundary(offset: Int): Boolean = breaks.isBoundary(offset)
}

private fun containsWholeWord(text: FoldedText, pattern: String, segmentedByScript: Boolean): Boolean {
    val value = text.value
    var from = 0
    while (from <= value.length) {
        val start = value.indexOf(pattern, from)
        if (start < 0) return false
        val end = start + pattern.length
        // Letter and digit edges decide first. For scripts without spaces (Chinese, Japanese, Thai) letters
        // touch each other, so Unicode word segmentation decides instead.
        if (hasWordEdges(value, start, end, pattern) ||
            (segmentedByScript && text.isWordBoundary(start) && text.isWordBoundary(end))) return true
        from = start + 1
    }
    return false
}

private fun hasWordEdges(value: String, start: Int, end: Int, pattern: String): Boolean {
    val firstIsWord = isWordCodePoint(Character.codePointAt(pattern, 0))
    val lastIsWord = isWordCodePoint(Character.codePointBefore(pattern, pattern.length))
    val leftOk = !firstIsWord || start == 0 || !isWordCodePoint(Character.codePointBefore(value, start))
    val rightOk = !lastIsWord || end == value.length || !isWordCodePoint(Character.codePointAt(value, end))
    return leftOk && rightOk
}

/** Letters, digits, underscore and marks, for every script. Marks count because they belong to the word they sit on. */
private fun isWordCodePoint(codePoint: Int): Boolean {
    if (codePoint == '_'.code || Character.isLetterOrDigit(codePoint)) return true
    return when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.LETTER_NUMBER.toInt(),
        Character.OTHER_NUMBER.toInt() -> true
        else -> false
    }
}

/** Shortens a message for the mentions feed. Whitespace runs collapse, so multi-line texts stay on one line. */
fun triggerSnippet(text: String): String {
    val collapsed = WHITESPACE.replace(stripFormatting(text), " ").trim()
    return if (collapsed.length <= TriggerWordLimits.MAX_SNIPPET_LENGTH) collapsed
    else collapsed.take(TriggerWordLimits.MAX_SNIPPET_LENGTH).trimEnd() + "…"
}
