package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** JVM-side coverage guard for the reviewed French UI translation. */
class FrenchResourceCoverageTest {
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
    fun everyDefaultStringHasFrenchTranslation() {
        val missing = resourceStrings("values").keys - resourceStrings("values-fr").keys
        assertTrue("Missing French UI strings: $missing", missing.isEmpty())
        val extra = resourceStrings("values-fr").keys - resourceStrings("values").keys
        assertTrue("French UI strings without a default resource: $extra", extra.isEmpty())
    }

    @Test
    fun everyDefaultPluralHasFrenchTranslation() {
        val default = resourcePlurals("values")
        val english = resourcePlurals("values-en")
        val french = resourcePlurals("values-fr")
        val missing = default.keys - french.keys
        assertTrue("Missing French plural resources: $missing", missing.isEmpty())
        val extra = french.keys - english.keys
        assertTrue("French plural resources without an English resource: $extra", extra.isEmpty())
        // French uses the same one/other plural categories as English, not the
        // one/few/many categories of the default Russian resources.
        english.keys.intersect(french.keys).forEach { key ->
            assertEquals(
                "French plural forms differ for $key",
                english.getValue(key).keys,
                french.getValue(key).keys
            )
        }
    }

    @Test
    fun translationsPreserveFormatArguments() {
        val defaultStrings = resourceStrings("values")
        val frenchStrings = resourceStrings("values-fr")
        val brokenStrings = defaultStrings.keys.intersect(frenchStrings.keys).filter { key ->
            formatArguments(defaultStrings.getValue(key)) != formatArguments(frenchStrings.getValue(key))
        }
        assertEquals("Incompatible French string format arguments: $brokenStrings", emptyList<String>(), brokenStrings)

        val englishPlurals = resourcePlurals("values-en")
        val frenchPlurals = resourcePlurals("values-fr")
        val brokenPlurals = englishPlurals.keys.intersect(frenchPlurals.keys).flatMap { key ->
            val source = englishPlurals.getValue(key)
            val translated = frenchPlurals.getValue(key)
            source.keys.filter { quantity ->
                formatArguments(source.getValue(quantity)) != formatArguments(translated[quantity].orEmpty())
            }.map { quantity -> "$key/$quantity" }
        }
        assertEquals("Incompatible French plural format arguments: $brokenPlurals", emptyList<String>(), brokenPlurals)
    }

    @Test
    fun frenchStringsAreNotBlank() {
        val emptyStrings = resourceStrings("values-fr").filterValues { it.isBlank() }.keys
        assertTrue("Empty French UI strings: $emptyStrings", emptyStrings.isEmpty())

        val emptyPlurals = resourcePlurals("values-fr")
            .filterValues { items -> items.values.any { it.isBlank() } }
            .keys
        assertTrue("Empty French plural strings: $emptyPlurals", emptyPlurals.isEmpty())
    }

    @Test
    fun frenchFilesMirrorEnglishAndDefaultFiles() {
        val french = resourceFiles("values-fr").map { it.name }
        assertEquals("French files do not mirror values-en", resourceFiles("values-en").map { it.name }, french)
        assertTrue(
            "French files are missing default-only files",
            french.containsAll(resourceFiles("values").map { it.name })
        )
    }

    @Test
    fun frenchIsAnExplicitAppLocale() {
        assertEquals("fr", AppLocale.FRENCH)
        assertTrue(AppLocale.FRENCH in AppLocale.supported)
        assertEquals("fr", resolveAppLanguage("fr-FR", "ru"))
        assertEquals("fr", resolveAppLanguage("fr-CA", "ru"))
        assertEquals("fr", resolveAppLanguage(null, "fr"))
    }
}
