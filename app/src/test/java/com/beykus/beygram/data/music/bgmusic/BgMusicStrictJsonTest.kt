package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BgMusicStrictJsonTest {

    private fun problem(text: String): String? = BgMusicStrictJson.problem(text, BgMusicFormat.MAX_NESTING_DEPTH)

    @Test
    fun strictJsonIsAccepted() {
        val valid = listOf(
            "{}",
            "[]",
            "\"just a string\"",
            "42",
            " \t\r\n{\"a\" : [ 1 , -0 , 2.5e+3 , 1E-2 , true , false , null ] }\n",
            "{\"s\":\"quote \\\" slash \\/ escapes \\b\\f\\n\\r\\t \\u00e9 \\ud83d\\ude00\"}",
            "{\"emoji\":\"😀 ё ש\"}",
            "{\"a\":{\"x\":1},\"b\":{\"x\":2}}",
            "[{\"a\":1},{\"a\":2}]",
        )
        for (text in valid) {
            assertNull(text, problem(text))
        }
    }

    @Test
    fun lenientSyntaxIsRejected() {
        val lenient = listOf(
            "{'a':1}",
            "{\"a\":'\"',\"b\":[[[[",
            "{a:1}",
            "{\"a\":word}",
            "{\"a\":NaN}",
            "{\"a\":Infinity}",
            "{\"a\":1;\"b\":2}",
            "{\"a\"=1}",
            "{\"a\"=>1}",
            "{\"a\":1} // trailing comment",
            "{\"a\":1} # hash comment",
            "/* leading */ {}",
            "{\"a\":1 /* inside */}",
            "{\"a\":1} x",
            "{}{}",
            "[1] ]",
            "{\"a\":1,}",
            "[1,]",
            "{,}",
            "{\"a\" 1}",
            "[1 2]",
            "{\"a\":01}",
            "{\"a\":+1}",
            "{\"a\":.5}",
            "{\"a\":1.}",
            "{\"a\":1e}",
            "{\"a\":-}",
            "{\"a\":--1}",
            "{\"a\":tru}",
            "{\"a\":nul}",
            "{\"a\":\"unterminated",
            "{\"a\":\"\\x\"}",
            "{\"a\":\"\\u12G4\"}",
            "{\"a\":\"raw\ttab\"}",
            "{\"a\":\"raw\nnewline\"}",
            "\u00A0{}",
            "\u000C{}",
            "\uFEFF{}",
            "",
            "   ",
            "{",
            "[",
        )
        for (text in lenient) {
            assertNotNull(text, problem(text))
        }
    }

    @Test
    fun nestingIsCountedExactlyAgainstTheLimit() {
        assertNull(problem("[".repeat(16) + "]".repeat(16)))
        assertNotNull(problem("[".repeat(17) + "]".repeat(17)))
        assertNotNull(problem("[".repeat(100_000) + "]".repeat(100_000)))
        assertNull(problem("{\"a\":" + "[".repeat(15) + "]".repeat(15) + "}"))
        assertNotNull(problem("{\"a\":" + "[".repeat(16) + "]".repeat(16) + "}"))
    }

    @Test
    fun repeatedKeysAreRejectedAtAnyLevelAndAfterUnescaping() {
        assertNotNull(problem("{\"a\":1,\"a\":2}"))
        assertNotNull(problem("{\"x\":{\"a\":1,\"a\":2}}"))
        assertNotNull(problem("[{\"a\":1,\"b\":2,\"a\":3}]"))
        assertNotNull(problem("{\"A\":1,\"\\u0041\":2}"))
        assertNull(problem("{\"x\":{\"a\":1},\"y\":{\"a\":2}}"))
    }
}
