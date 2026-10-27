package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** JVM-side coverage guard for the reviewed Spanish UI translation. */
class SpanishResourceCoverageTest {
    private val placeholders = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[a-zA-Z%]")

    private fun resourceFiles(directory: String): List<File> {
        val folder = sequenceOf(
            File("src/main/res/$directory"),
            File("app/src/main/res/$directory")
        ).firstOrNull { it.isDirectory } ?: error("Cannot find resources: $directory")
        return folder.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }
            .sortedBy { it.name }
    }

    private fun resourceStrings(directory: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (file in resourceFiles(directory)) {
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

    private fun resourcePlurals(directory: String): Map<String, Map<String, String>> {
        val result = linkedMapOf<String, Map<String, String>>()
        for (file in resourceFiles(directory)) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val elements = document.getElementsByTagName("plurals")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as Element
                val key = element.getAttribute("name")
                require(!result.containsKey(key)) { "Duplicate plural resource $key in $directory (${file.name})" }
                val items = linkedMapOf<String, String>()
                val children = element.getElementsByTagName("item")
                for (itemIndex in 0 until children.length) {
                    val item = children.item(itemIndex) as Element
                    items[item.getAttribute("quantity")] = item.textContent
                }
                result[key] = items
            }
        }
        return result
    }

    private fun formatArguments(text: String): List<String> =
        placeholders.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyDefaultStringHasSpanishTranslation() {
        val missing = resourceStrings("values").keys - resourceStrings("values-es").keys
        assertTrue("Missing Spanish UI strings: $missing", missing.isEmpty())
        val extra = resourceStrings("values-es").keys - resourceStrings("values").keys
        assertTrue("Spanish UI strings without a default resource: $extra", extra.isEmpty())
    }

    @Test
    fun everyDefaultPluralHasSpanishTranslation() {
        val default = resourcePlurals("values")
        val english = resourcePlurals("values-en")
        val spanish = resourcePlurals("values-es")
        val missing = default.keys - spanish.keys
        assertTrue("Missing Spanish plural resources: $missing", missing.isEmpty())
        val extra = spanish.keys - english.keys
        assertTrue("Spanish plural resources without an English resource: $extra", extra.isEmpty())
        // Spanish uses the same one/other plural categories as English, not the
        // one/few/many categories of the default Russian resources.
        english.keys.intersect(spanish.keys).forEach { key ->
            assertEquals(
                "Spanish plural forms differ for $key",
                english.getValue(key).keys,
                spanish.getValue(key).keys
            )
        }
    }

    @Test
    fun translationsPreserveFormatArguments() {
        val defaultStrings = resourceStrings("values")
        val spanishStrings = resourceStrings("values-es")
        val brokenStrings = defaultStrings.keys.intersect(spanishStrings.keys).filter { key ->
            formatArguments(defaultStrings.getValue(key)) != formatArguments(spanishStrings.getValue(key))
        }
        assertEquals("Incompatible Spanish string format arguments: $brokenStrings", emptyList<String>(), brokenStrings)

        val englishPlurals = resourcePlurals("values-en")
        val spanishPlurals = resourcePlurals("values-es")
        val brokenPlurals = englishPlurals.keys.intersect(spanishPlurals.keys).flatMap { key ->
            val source = englishPlurals.getValue(key)
            val translated = spanishPlurals.getValue(key)
            source.keys.filter { quantity ->
                formatArguments(source.getValue(quantity)) != formatArguments(translated[quantity].orEmpty())
            }.map { quantity -> "$key/$quantity" }
        }
        assertEquals("Incompatible Spanish plural format arguments: $brokenPlurals", emptyList<String>(), brokenPlurals)
    }

    @Test
    fun spanishStringsAreNotBlank() {
        val emptyStrings = resourceStrings("values-es").filterValues { it.isBlank() }.keys
        assertTrue("Empty Spanish UI strings: $emptyStrings", emptyStrings.isEmpty())

        val emptyPlurals = resourcePlurals("values-es")
            .filterValues { items -> items.values.any { it.isBlank() } }
            .keys
        assertTrue("Empty Spanish plural strings: $emptyPlurals", emptyPlurals.isEmpty())
    }

    @Test
    fun spanishFilesMirrorEnglishAndDefaultFiles() {
        val spanish = resourceFiles("values-es").map { it.name }
        assertEquals("Spanish files do not mirror values-en", resourceFiles("values-en").map { it.name }, spanish)
        assertTrue(
            "Spanish files are missing default-only files",
            spanish.containsAll(resourceFiles("values").map { it.name })
        )
    }

    @Test
    fun spanishIsAnExplicitAppLocale() {
        assertEquals("es", AppLocale.SPANISH)
        assertTrue(AppLocale.SPANISH in AppLocale.supported)
        assertEquals("es", resolveAppLanguage("es-ES", "ru"))
        assertEquals("es", resolveAppLanguage("es-419", "ru"))
    }
}
