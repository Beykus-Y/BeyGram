package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** JVM-side coverage guard for completed English UI sections; no Android emulator required. */
class EnglishResourceCoverageTest {
    // Every default-language string now has an explicit English counterpart.

    private fun resources(directory: String): Map<String, String> {
        val folder = sequenceOf(
            File("src/main/res/$directory"),
            File("app/src/main/res/$directory")
        ).firstOrNull { it.isDirectory } ?: error("Cannot find resources: $directory")
        val result = linkedMapOf<String, String>()
        val xmlFiles = folder.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }
        for (file in xmlFiles) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val elements = document.getElementsByTagName("string")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as Element
                val key = element.getAttribute("name")
                require(!result.containsKey(key)) { "Duplicate resource $key in $directory (${file.name})" }
                result[key] = element.textContent
            }
        }
        return result
    }

    @Test
    fun allEnglishStringsHaveExplicitTranslations() {
        val default = resources("values")
        val english = resources("values-en")
        val missing = default.keys.filterNot(english::containsKey)
        assertTrue("Missing English UI strings: $missing", missing.isEmpty())
    }

    @Test
    fun translationsPreserveFormatArguments() {
        val default = resources("values")
        val english = resources("values-en")
        val placeholders = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[sdif]|%%")
        val broken = default.keys.intersect(english.keys)
            .filter { key ->
                placeholders.findAll(default.getValue(key)).map { it.value }.sorted().toList() !=
                    placeholders.findAll(english.getValue(key)).map { it.value }.sorted().toList()
            }
        assertEquals("Incompatible format arguments: $broken", emptyList<String>(), broken)
    }

    @Test
    fun englishResourceStringsAreNotBlank() {
        val translated = resources("values-en")
        val empty = translated.filterValues { it.isBlank() }.keys
        assertTrue("Empty translated UI strings: $empty", empty.isEmpty())
    }
}
