package com.beykus.beygram.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM-side guard for Auto Backup and device-transfer rules; no Android device required.
 * Account IDs are opaque and generated at runtime, so shared preferences rely on an explicit
 * allowlist: Android backup rules match exact filenames and do not support wildcards.
 */
class BackupRulesTest {
    private data class Rule(val domain: String, val path: String)

    private fun document(name: String): Document {
        val file = sequenceOf(
            File("src/main/res/xml/$name"),
            File("app/src/main/res/xml/$name")
        ).firstOrNull { it.isFile } ?: error("Cannot find backup rules: $name")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun Element.elements(tag: String): List<Element> =
        (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .filter { it.tagName == tag }

    private fun Element.rules(tag: String): List<Rule> =
        elements(tag).map { Rule(it.getAttribute("domain"), it.getAttribute("path")) }

    private fun sections(fileName: String): Map<String, Element> {
        val root = document(fileName).documentElement
        return when (root.tagName) {
            "full-backup-content" -> mapOf("full-backup-content" to root)
            "data-extraction-rules" -> mapOf(
                "cloud-backup" to root.elements("cloud-backup").single(),
                "device-transfer" to root.elements("device-transfer").single()
            )
            else -> error("Unexpected root element ${root.tagName} in $fileName")
        }
    }

    private fun assertEverySection(fileName: String, assertion: (String, Element) -> Unit) {
        val sections = sections(fileName)
        val expectedSections = if (fileName == "backup_rules.xml") {
            setOf("full-backup-content")
        } else {
            setOf("cloud-backup", "device-transfer")
        }
        assertEquals("$fileName must define every backup section", expectedSections, sections.keys)
        sections.entries.forEach { (section, element) -> assertion(section, element) }
    }

    @Test
    fun tdlibSessionAndRegistryAreExcludedFromEveryBackupSection() {
        RULE_FILES.forEach { fileName ->
            assertEverySection(fileName) { section, element ->
                val excludes = element.rules("exclude")
                REQUIRED_FILE_EXCLUSIONS.forEach { path ->
                    assertTrue(
                        "$fileName/$section must exclude file/$path",
                        Rule("file", path) in excludes
                    )
                }
            }
        }
    }

    @Test
    fun accountScopedPreferencesAreOmittedWhileTransferableSettingsRemain() {
        RULE_FILES.forEach { fileName ->
            assertEverySection(fileName) { section, element ->
                val includes = element.rules("include")
                assertTrue(
                    "$fileName/$section must keep ordinary file data after TDLib is excluded",
                    Rule("file", ".") in includes
                )
                assertTrue(
                    "$fileName/$section must keep ordinary database data",
                    Rule("database", ".") in includes
                )
                assertTrue(
                    "$fileName/$section must keep ordinary external data",
                    Rule("external", ".") in includes
                )

                val sharedPrefIncludes = includes.filter { it.domain == "sharedpref" }.map { it.path }
                assertTrue(
                    "$fileName/$section must keep the settings that are safe to transfer",
                    sharedPrefIncludes.isNotEmpty()
                )
                TRANSFERABLE_SHARED_PREFS.forEach { path ->
                    assertTrue(
                        "$fileName/$section must include sharedpref/$path",
                        path in sharedPrefIncludes
                    )
                }
                SESSION_SHARED_PREFS_OMITTED.forEach { path ->
                    assertTrue(
                        "$fileName/$section must omit session-bearing sharedpref/$path",
                        path !in sharedPrefIncludes
                    )
                }
                ACCOUNT_SCOPED_PREF_PREFIXES.forEach { prefix ->
                    assertTrue(
                        "$fileName/$section must not include account-scoped preferences ($prefix*)",
                        sharedPrefIncludes.none { it.startsWith(prefix) }
                    )
                }
                assertTrue(
                    "$fileName/$section must use exact sharedpref filenames, not a directory or wildcard",
                    sharedPrefIncludes.all { it.endsWith(".xml") && '/' !in it && '*' !in it && '?' !in it }
                )
            }
        }
    }

    private companion object {
        val RULE_FILES = listOf("backup_rules.xml", "data_extraction_rules.xml")
        val REQUIRED_FILE_EXCLUSIONS = listOf("tdlib", "tdlib-accounts", "diagnostics-report", "uploads")
        val SESSION_SHARED_PREFS_OMITTED = listOf(
            "vpn-settings.xml",
            "chat_message_drafts.xml",
            "notifications.xml",
            "pending_group_creation.xml",
            "mihomo_tdlib_proxy.xml",
            "beygram_secondary_resume_v1.xml",
            "mihomo-loopback.xml",
            "analytics.xml",
            // External links can contain invite hashes or proxy secrets; never restore them on another device.
            "telegram_deep_links.xml"
        )
        val TRANSFERABLE_SHARED_PREFS = listOf(
            "analytics-consent.xml",
            "appearance.xml",
            "beygram-app-locale.xml",
            "chats.xml",
            "last-seen-settings.xml",
            "mihomo-selection.xml",
            "navigation.xml",
            "storage-settings.xml",
            "video-message-settings.xml"
        )
        val ACCOUNT_SCOPED_PREF_PREFIXES = listOf(
            "chat_message_drafts_",
            "chats_",
            "notifications_",
            "pending_group_creation_",
            "mihomo_tdlib_proxy_"
        )
    }
}
