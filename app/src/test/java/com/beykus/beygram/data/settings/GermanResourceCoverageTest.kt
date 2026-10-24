package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** JVM-side coverage guard for the reviewed German UI translation. */
class GermanResourceCoverageTest {
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
    fun everyDefaultStringHasGermanTranslation() {
        val missing = resourceStrings("values").keys - resourceStrings("values-de").keys
        assertTrue("Missing German UI strings: $missing", missing.isEmpty())
    }

    @Test
    fun everyDefaultPluralHasGermanTranslation() {
        val default = resourcePlurals("values")
        val english = resourcePlurals("values-en")
        val german = resourcePlurals("values-de")
        val missing = default.keys - german.keys
        assertTrue("Missing German plural resources: $missing", missing.isEmpty())
        // German uses the same one/other plural categories as English, not the
        // one/few/many categories of the default Russian resources.
        english.keys.intersect(german.keys).forEach { key ->
            assertEquals(
                "German plural forms differ for $key",
                english.getValue(key).keys,
                german.getValue(key).keys
            )
        }
    }

    @Test
    fun translationsPreserveFormatArguments() {
        val defaultStrings = resourceStrings("values")
        val germanStrings = resourceStrings("values-de")
        val brokenStrings = defaultStrings.keys.intersect(germanStrings.keys).filter { key ->
            formatArguments(defaultStrings.getValue(key)) != formatArguments(germanStrings.getValue(key))
        }
        assertEquals("Incompatible German string format arguments: $brokenStrings", emptyList<String>(), brokenStrings)

        val englishPlurals = resourcePlurals("values-en")
        val germanPlurals = resourcePlurals("values-de")
        val brokenPlurals = englishPlurals.keys.intersect(germanPlurals.keys).flatMap { key ->
            val source = englishPlurals.getValue(key)
            val translated = germanPlurals.getValue(key)
            source.keys.filter { quantity ->
                formatArguments(source.getValue(quantity)) != formatArguments(translated[quantity].orEmpty())
            }.map { quantity -> "$key/$quantity" }
        }
        assertEquals("Incompatible German plural format arguments: $brokenPlurals", emptyList<String>(), brokenPlurals)
    }

    @Test
    fun germanStringsAreNotBlank() {
        val emptyStrings = resourceStrings("values-de").filterValues { it.isBlank() }.keys
        assertTrue("Empty German UI strings: $emptyStrings", emptyStrings.isEmpty())

        val emptyPlurals = resourcePlurals("values-de")
            .filterValues { items -> items.values.any { it.isBlank() } }
            .keys
        assertTrue("Empty German plural strings: $emptyPlurals", emptyPlurals.isEmpty())
    }

    @Test
    fun germanFilesMirrorEnglishAndDefaultFiles() {
        val german = resourceFiles("values-de").map { it.name }
        assertEquals("German files do not mirror values-en", resourceFiles("values-en").map { it.name }, german)
        assertTrue(
            "German files are missing default-only files",
            german.containsAll(resourceFiles("values").map { it.name })
        )
    }

    @Test
    fun germanIsAnExplicitAppLocale() {
        assertEquals("de", AppLocale.GERMAN)
        assertTrue(AppLocale.GERMAN in AppLocale.supported)
        assertEquals("de", resolveAppLanguage("de-DE", "ru"))
    }
}
