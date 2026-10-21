package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** JVM-side coverage guard for completed English UI sections; no Android emulator required. */
class EnglishResourceCoverageTest {
    private val completedSections = listOf(
        "auth_", "chats_", "global_search_", "tabs_", "start_", "archive_",
        "contacts_", "calls_", "updates_", "nav_", "settings_", "theme_",
        "wallpaper_", "proxy_", "notif_", "storage_", "err_", "profile_", "chat_"
    )

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
    fun completedEnglishSectionsNeverFallBackToRussian() {
        val default = resources("values")
        val english = resources("values-en")
        val missing = default.keys.filter { key -> completedSections.any { key.startsWith(it) } && !english.containsKey(key) }
        assertTrue("Missing English UI strings: $missing", missing.isEmpty())
    }

    @Test
    fun translationsPreserveFormatArguments() {
        val default = resources("values")
        val english = resources("values-en")
        val placeholders = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[sdif]|%%")
        val broken = default.keys.intersect(english.keys)
            .filter { key -> completedSections.any { key.startsWith(it) } }
            .filter { key ->
                placeholders.findAll(default.getValue(key)).map { it.value }.sorted().toList() !=
                    placeholders.findAll(english.getValue(key)).map { it.value }.sorted().toList()
            }
        assertEquals("Incompatible format arguments: $broken", emptyList<String>(), broken)
    }

    @Test
    fun everyCompletedSectionHasNonEmptyEnglishText() {
        val translated = resources("values-en")
        val empty = translated.filter { (key, value) ->
            completedSections.any { key.startsWith(it) } && value.isBlank()
        }.keys
        assertTrue("Empty translated UI strings: $empty", empty.isEmpty())
    }
}
