package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static guard for the stealth gate. Read receipts, typing, presence, opened content and story views reach TDLib
 * only through their gate file. A new route must go through the gate, or be added here on purpose.
 */
class StealthBoundaryTest {
    private val gatedRequests = mapOf(
        "TdApi.ViewMessages(" to setOf("TdConversationEffects.kt"),
        "TdApi.OpenMessageContent(" to setOf("TdConversationEffects.kt"),
        "TdApi.SendChatAction(" to setOf("TdConversationEffects.kt"),
        "SetOption(\"online\"" to setOf("TdConversationEffects.kt"),
        "TdApi.OpenStory(" to setOf("TdStories.kt"),
        "TdApi.CloseStory(" to setOf("TdStories.kt")
    )

    @Test fun sensitiveTdlibRequestsAppearOnlyInTheirGateFiles() {
        val sources = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val offenders = gatedRequests.entries.flatMap { (pattern, allowedFiles) ->
            sources.filter { it.name !in allowedFiles && it.readText().contains(pattern) }
                .map { "${it.name} uses $pattern" }
        }
        assertTrue("Sensitive TDLib requests outside their gate: $offenders", offenders.isEmpty())
    }

    /** Stale guard: if a pattern no longer appears in its gate file, this list must be updated. */
    @Test fun everyGatedPatternStillExistsInItsGateFile() {
        val sources = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val stale = gatedRequests.filter { (pattern, allowedFiles) ->
            sources.none { it.name in allowedFiles && it.readText().contains(pattern) }
        }.keys
        assertTrue("Gated patterns not found in their gate files: $stale", stale.isEmpty())
    }

    private fun sourceRoot(): File = sequenceOf(File("src/main/java"), File("app/src/main/java"))
        .firstOrNull { it.isDirectory } ?: error("Cannot find main sources")
}
