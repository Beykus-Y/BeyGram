package com.beykus.beygram.data.music.bgmusic

/**
 * Checks that manifest text is strict JSON (RFC 8259) before anything parses it.
 *
 * org.json is lenient, and the leniency differs by platform. It reads single-quoted strings, `#`, `//` and block
 * comments, unquoted keys and words, and `;` or `=` separators. A nesting count over the raw text can be fooled by
 * such input, so the strict grammar is checked first. After that the parser only sees standard JSON, and both
 * platforms read the same manifest the same way.
 *
 * The check is iterative, so depth costs no stack. It enforces the nesting limit exactly, and it rejects a key that
 * repeats inside one object: the JVM parser already refuses that, while Android keeps the last value.
 */
internal object BgMusicStrictJson {

    /**
     * Returns null when [text] is exactly one strict JSON value, nested at most [maxDepth] levels, with no repeated
     * key in any object. Otherwise returns a short, fixed description of the first problem. The description never
     * contains text from the file.
     */
    fun problem(text: String, maxDepth: Int): String? = try {
        Checker(text, maxDepth).scanDocument()
        null
    } catch (failure: Problem) {
        failure.detail
    }

    private class Problem(val detail: String) : RuntimeException(detail, null, false, false)

    /** One open array or object. For an object, [keys] holds the names seen so far, after unescaping. */
    private class Frame(val isObject: Boolean) {
        var keys: HashSet<String>? = null
    }

    private enum class State { VALUE, ARRAY_VALUE_OR_END, OBJECT_KEY_OR_END, OBJECT_KEY, COLON, AFTER_VALUE }

    private class Checker(private val text: String, private val maxDepth: Int) {
        private var pos = 0
        private var state = State.VALUE
        private val frames = ArrayList<Frame>()

        fun scanDocument() {
            while (true) {
                skipWhitespace()
                if (state == State.AFTER_VALUE && frames.isEmpty()) {
                    if (pos != text.length) fail("data after the JSON value")
                    return
                }
                if (pos >= text.length) fail("unexpected end of input")
                step(text[pos])
            }
        }

        private fun step(c: Char) {
            when (state) {
                State.VALUE -> value(c)
                State.ARRAY_VALUE_OR_END -> if (c == ']') close() else value(c)
                State.OBJECT_KEY_OR_END -> if (c == '}') close() else key(c)
                State.OBJECT_KEY -> key(c)
                State.COLON -> {
                    if (c != ':') fail("expected ':' after an object key")
                    pos++
                    state = State.VALUE
                }
                State.AFTER_VALUE -> afterValue(c)
            }
        }

        private fun value(c: Char) {
            when {
                c == '{' -> open(isObject = true)
                c == '[' -> open(isObject = false)
                c == '"' -> {
                    readString(sink = null)
                    state = State.AFTER_VALUE
                }
                c == 't' -> literal("true")
                c == 'f' -> literal("false")
                c == 'n' -> literal("null")
                c == '-' || c in '0'..'9' -> number()
                else -> fail("unexpected character where a value belongs")
            }
        }

        private fun key(c: Char) {
            if (c != '"') fail("object key must be a double-quoted string")
            val name = StringBuilder()
            readString(sink = name)
            val frame = frames[frames.size - 1]
            val seen = frame.keys ?: HashSet<String>().also { frame.keys = it }
            if (!seen.add(name.toString())) fail("repeated key in one object")
            state = State.COLON
        }

        private fun open(isObject: Boolean) {
            if (frames.size >= maxDepth) fail("nesting is deeper than the limit")
            pos++
            frames.add(Frame(isObject))
            state = if (isObject) State.OBJECT_KEY_OR_END else State.ARRAY_VALUE_OR_END
        }

        private fun close() {
            pos++
            frames.removeAt(frames.size - 1)
            state = State.AFTER_VALUE
        }

        private fun afterValue(c: Char) {
            val top = frames[frames.size - 1]
            when {
                c == ',' -> {
                    pos++
                    state = if (top.isObject) State.OBJECT_KEY else State.VALUE
                }
                c == '}' && top.isObject -> close()
                c == ']' && !top.isObject -> close()
                else -> fail("unexpected character after a value")
            }
        }

        private fun literal(word: String) {
            if (!text.startsWith(word, pos)) fail("only true, false and null are allowed as words")
            pos += word.length
            state = State.AFTER_VALUE
        }

        /** Strict number grammar: optional minus, no leading zeros, digits after a dot, digits in an exponent. */
        private fun number() {
            if (text[pos] == '-') pos++
            if (pos >= text.length) fail("invalid number")
            when {
                text[pos] == '0' -> pos++
                text[pos] in '1'..'9' -> skipDigits()
                else -> fail("invalid number")
            }
            if (pos < text.length && text[pos] == '.') {
                pos++
                requireDigit()
                skipDigits()
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                requireDigit()
                skipDigits()
            }
            state = State.AFTER_VALUE
        }

        private fun requireDigit() {
            if (pos >= text.length || text[pos] !in '0'..'9') fail("invalid number")
        }

        private fun skipDigits() {
            while (pos < text.length && text[pos] in '0'..'9') pos++
        }

        /** Reads a string whose opening quote is at [pos]. Decoded characters go to [sink] when it is not null. */
        private fun readString(sink: StringBuilder?) {
            pos++
            while (true) {
                if (pos >= text.length) fail("unterminated string")
                val c = text[pos]
                when {
                    c == '"' -> {
                        pos++
                        return
                    }
                    c == '\\' -> {
                        pos++
                        escape(sink)
                    }
                    c < ' ' -> fail("control character inside a string")
                    else -> {
                        sink?.append(c)
                        pos++
                    }
                }
            }
        }

        private fun escape(sink: StringBuilder?) {
            if (pos >= text.length) fail("unterminated string")
            val c = text[pos]
            pos++
            val decoded = when (c) {
                '"', '\\', '/' -> c
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> hex4()
                else -> fail("invalid escape inside a string")
            }
            sink?.append(decoded)
        }

        private fun hex4(): Char {
            var value = 0
            repeat(4) {
                if (pos >= text.length) fail("invalid unicode escape")
                val digit = hexDigit(text[pos])
                if (digit < 0) fail("invalid unicode escape")
                value = value * 16 + digit
                pos++
            }
            return value.toChar()
        }

        /** ASCII hex digits only. `Character.digit` would also accept fullwidth letters. */
        private fun hexDigit(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }

        private fun skipWhitespace() {
            while (pos < text.length) {
                val c = text[pos]
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return
                pos++
            }
        }

        private fun fail(detail: String): Nothing = throw Problem(detail)
    }
}
